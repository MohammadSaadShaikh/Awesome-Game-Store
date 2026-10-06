package com.gamestore.service.impl;

import com.gamestore.dto.CheapSharkDealDto;
import com.gamestore.dto.CheapSharkStoreDto;
import com.gamestore.dto.response.DealResponse;
import com.gamestore.dto.response.GameLookupResponse;
import com.gamestore.dto.response.StoreResponse;
import com.gamestore.service.DealAggregatorService;
import com.gamestore.service.GameDealClientService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DealAggregatorServiceImpl implements DealAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(DealAggregatorServiceImpl.class);
    private static final String CHEAPSHARK_BASE_URL = "https://www.cheapshark.com";

    private final GameDealClientService gameDealClientService;
    private final Map<String, CheapSharkStoreDto> storeCache = new ConcurrentHashMap<>();

    public DealAggregatorServiceImpl(GameDealClientService gameDealClientService) {
        this.gameDealClientService = gameDealClientService;
    }

    @PostConstruct
    public void initStoreCache() {
        refreshStoreCache();
    }

    public synchronized void refreshStoreCache() {
        try {
            List<CheapSharkStoreDto> activeStores = gameDealClientService.getActiveStores();
            storeCache.clear();
            for (CheapSharkStoreDto store : activeStores) {
                storeCache.put(store.storeID(), store);
            }
            log.info("Store cache initialized with {} active stores.", storeCache.size());
        } catch (Exception e) {
            log.error("Failed to initialize store cache: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<StoreResponse> getAllStores() {
        ensureStoreCacheLoaded();
        return storeCache.values().stream()
                .map(this::mapToStoreResponse)
                .sorted(Comparator.comparing(StoreResponse::name))
                .collect(Collectors.toList());
    }

    @Override
    public List<DealResponse> getTopDeals(int limit, String sortBy) {
        ensureStoreCacheLoaded();
        List<CheapSharkDealDto> rawDeals = gameDealClientService.getTopDeals(limit);
        return rawDeals.stream()
                .map(this::mapToDealResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DealResponse> searchDeals(String title, String storeId, Double maxPrice) {
        ensureStoreCacheLoaded();
        if (title == null || title.isBlank()) {
            return List.of();
        }

        List<CheapSharkDealDto> rawDeals = gameDealClientService.searchDealsByTitle(title, 50);

        return rawDeals.stream()
                .map(this::mapToDealResponse)
                .filter(deal -> storeId == null || storeId.isBlank() || deal.storeId().equalsIgnoreCase(storeId))
                .filter(deal -> maxPrice == null || deal.salePrice() <= maxPrice)
                .collect(Collectors.toList());
    }

    @Override
    public GameLookupResponse getGameDealsGrouped(String title) {
        ensureStoreCacheLoaded();
        List<DealResponse> deals = searchDeals(title, null, null);

        if (deals.isEmpty()) {
            return new GameLookupResponse(null, title, null, 0.0, List.of());
        }

        DealResponse primary = deals.get(0);
        double cheapestPrice = deals.stream()
                .mapToDouble(DealResponse::salePrice)
                .min()
                .orElse(0.0);

        return new GameLookupResponse(
                primary.gameId(),
                primary.title(),
                primary.thumbnail(),
                cheapestPrice,
                deals
        );
    }

    private void ensureStoreCacheLoaded() {
        if (storeCache.isEmpty()) {
            refreshStoreCache();
        }
    }

    private StoreResponse mapToStoreResponse(CheapSharkStoreDto dto) {
        String banner = dto.images() != null ? dto.images().banner() : null;
        String logo = dto.images() != null ? dto.images().logo() : null;
        String icon = dto.images() != null ? dto.images().icon() : null;

        return new StoreResponse(
                dto.storeID(),
                dto.storeName(),
                dto.isActive() == 1,
                formatImageUrl(banner),
                formatImageUrl(logo),
                formatImageUrl(icon)
        );
    }

    private DealResponse mapToDealResponse(CheapSharkDealDto dto) {
        CheapSharkStoreDto storeDto = storeCache.get(dto.storeID());
        String storeName = storeDto != null ? storeDto.storeName() : "Store #" + dto.storeID();

        double normalPrice = parseDoubleOrDefault(dto.normalPrice(), 0.0);
        double salePrice = parseDoubleOrDefault(dto.salePrice(), 0.0);
        double savings = parseDoubleOrDefault(dto.savings(), 0.0);
        String steamAppId = dto.steamAppID();

        String storeUrl = generateDirectStoreUrl(storeName, dto.storeID(), dto.title(), steamAppId);

        return new DealResponse(
                dto.dealID(),
                dto.gameID(),
                dto.title(),
                dto.storeID(),
                storeName,
                normalPrice,
                salePrice,
                Math.round(savings * 100.0) / 100.0,
                dto.steamRatingText() != null ? dto.steamRatingText() : "N/A",
                dto.thumb(),
                steamAppId,
                storeUrl
        );
    }

    private String generateDirectStoreUrl(String storeName, String storeId, String title, String steamAppId) {
        if (title == null) title = "";
        String name = (storeName != null ? storeName : "").toLowerCase();
        String id = storeId != null ? storeId : "";
        String encodedTitle = java.net.URLEncoder.encode(title, java.nio.charset.StandardCharsets.UTF_8);

        if ("1".equals(id) || name.contains("steam")) {
            if (steamAppId != null && !steamAppId.isBlank() && !"0".equals(steamAppId) && !"null".equalsIgnoreCase(steamAppId)) {
                return "https://store.steampowered.com/app/" + steamAppId + "/";
            }
            return "https://store.steampowered.com/search/?term=" + encodedTitle;
        }
        if ("7".equals(id) || name.contains("gog")) {
            return "https://www.gog.com/en/games?query=" + encodedTitle;
        }
        if ("25".equals(id) || name.contains("epic")) {
            return "https://store.epicgames.com/en-US/browse?q=" + encodedTitle + "&sortBy=relevance&sortDir=DESC";
        }
        if ("11".equals(id) || name.contains("humble")) {
            return "https://www.humblebundle.com/store/search?sort=bestselling&search=" + encodedTitle;
        }
        if ("3".equals(id) || name.contains("greenman")) {
            return "https://www.greenmangaming.com/search/" + encodedTitle;
        }
        if ("15".equals(id) || name.contains("fanatical")) {
            return "https://www.fanatical.com/en/search?search=" + encodedTitle;
        }
        if ("2".equals(id) || name.contains("gamersgate")) {
            return "https://www.gamersgate.com/games/?query=" + encodedTitle;
        }
        if ("27".equals(id) || name.contains("gamesplanet")) {
            return "https://gamesplanet.com/search?query=" + encodedTitle;
        }
        if ("8".equals(id) || name.contains("ea") || name.contains("origin")) {
            return "https://www.ea.com/search?q=" + encodedTitle;
        }
        if ("13".equals(id) || name.contains("ubisoft") || name.contains("uplay")) {
            return "https://store.ubisoft.com/search?q=" + encodedTitle;
        }
        return "https://www.google.com/search?q=" + encodedTitle + "+" + java.net.URLEncoder.encode(storeName != null ? storeName : "game store", java.nio.charset.StandardCharsets.UTF_8);
    }

    private String formatImageUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return relativePath.startsWith("/") ? CHEAPSHARK_BASE_URL + relativePath : relativePath;
    }

    private double parseDoubleOrDefault(String val, double defaultValue) {
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
