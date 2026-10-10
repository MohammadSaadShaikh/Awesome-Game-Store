package com.gamestore.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamestore.dto.CheapSharkDealDto;
import com.gamestore.dto.CheapSharkStoreDto;
import com.gamestore.dto.response.DealResponse;
import com.gamestore.dto.response.GameDetailInfo;
import com.gamestore.dto.response.GameLookupResponse;
import com.gamestore.dto.response.StoreResponse;
import com.gamestore.service.DealAggregatorService;
import com.gamestore.service.GameDealClientService;
import com.gamestore.service.GameInformationService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DealAggregatorServiceImpl implements DealAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(DealAggregatorServiceImpl.class);
    private static final String CHEAPSHARK_BASE_URL = "https://www.cheapshark.com";

    private final GameDealClientService gameDealClientService;
    private final GameInformationService gameInformationService;
    private final ObjectMapper objectMapper;
    private final RestClient steamRestClient;
    private final Map<String, CheapSharkStoreDto> storeCache = new ConcurrentHashMap<>();

    private volatile List<DealResponse> cachedTrendingDeals = null;
    private volatile long lastTrendingFetchTime = 0;
    private static final long TRENDING_CACHE_TTL_MS = 15 * 60 * 1000; // 15 mins

    public DealAggregatorServiceImpl(
            GameDealClientService gameDealClientService,
            GameInformationService gameInformationService,
            ObjectMapper objectMapper
    ) {
        this.gameDealClientService = gameDealClientService;
        this.gameInformationService = gameInformationService;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(6000);
        factory.setReadTimeout(6000);

        this.steamRestClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "GameStoreAggregator/1.0 (contact@gamestore.example.com)")
                .build();
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
        long now = System.currentTimeMillis();

        if (cachedTrendingDeals != null && (now - lastTrendingFetchTime < TRENDING_CACHE_TTL_MS)) {
            return cachedTrendingDeals.stream().limit(limit).collect(Collectors.toList());
        }

        // Fetch dynamic real-time trending games from Steam storefront & cross-reference
        List<DealResponse> trending = fetchDynamicTrendingDeals(limit);
        if (trending != null && !trending.isEmpty()) {
            cachedTrendingDeals = trending;
            lastTrendingFetchTime = now;
            return trending.stream().limit(limit).collect(Collectors.toList());
        }

        // Fallback to CheapShark top deals (filtered & deduplicated to 1 lowest price per game)
        List<CheapSharkDealDto> rawDeals = gameDealClientService.getTopDeals(limit * 2);
        List<DealResponse> mapped = rawDeals.stream()
                .map(this::mapToDealResponse)
                .collect(Collectors.toList());
        List<DealResponse> deduplicated = filterAndDeduplicateDeals(mapped, "");
        return deduplicated.stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<DealResponse> searchDeals(String title, String storeId, Double maxPrice) {
        ensureStoreCacheLoaded();
        if (title == null || title.isBlank()) {
            return List.of();
        }

        List<CheapSharkDealDto> rawDeals = gameDealClientService.searchDealsByTitle(title, 60);

        List<DealResponse> mappedDeals = rawDeals.stream()
                .map(this::mapToDealResponse)
                .filter(deal -> storeId == null || storeId.isBlank() || deal.storeId().equalsIgnoreCase(storeId))
                .filter(deal -> maxPrice == null || deal.salePrice() <= maxPrice)
                .collect(Collectors.toList());

        // Deduplicate so only 1 game card is shown in search results with lowest price
        return filterAndDeduplicateDeals(mappedDeals, title);
    }

    @Override
    public GameLookupResponse getGameDealsGrouped(String title) {
        return getGameDealsGrouped(title, null, null);
    }

    @Override
    public GameLookupResponse getGameDealsGrouped(String title, String preferredGameId, String preferredSteamAppId) {
        ensureStoreCacheLoaded();

        // 1. Fetch raw deals for this game title
        List<CheapSharkDealDto> rawDeals = gameDealClientService.searchDealsByTitle(title, 60);
        List<DealResponse> allDeals = rawDeals.stream()
                .map(this::mapToDealResponse)
                .filter(d -> !isMicrotransactionOrCurrency(d.title(), title))
                .collect(Collectors.toList());

        // 2. Filter deals specific to the target game
        List<DealResponse> targetGameDeals = filterDealsForTargetGame(allDeals, title, preferredGameId, preferredSteamAppId);

        String resolvedGameId = preferredGameId;
        String resolvedSteamAppId = preferredSteamAppId;
        String resolvedThumbnail = null;
        String resolvedTitle = title;
        double cheapestPrice = 0.0;

        if (!targetGameDeals.isEmpty()) {
            DealResponse cheapestDeal = targetGameDeals.stream()
                    .min(Comparator.comparingDouble(DealResponse::salePrice))
                    .orElse(targetGameDeals.get(0));

            // Select clean title
            DealResponse bestTitleDeal = targetGameDeals.stream()
                    .filter(d -> d.title().equalsIgnoreCase(title.trim()))
                    .findFirst()
                    .orElse(cheapestDeal);

            resolvedTitle = bestTitleDeal.title();
            resolvedThumbnail = cheapestDeal.thumbnail();
            cheapestPrice = cheapestDeal.salePrice();

            if (resolvedGameId == null || resolvedGameId.isBlank()) {
                resolvedGameId = cheapestDeal.gameId();
            }
            if (resolvedSteamAppId == null || resolvedSteamAppId.isBlank()) {
                resolvedSteamAppId = targetGameDeals.stream()
                        .map(DealResponse::steamAppId)
                        .filter(id -> id != null && !id.isBlank() && !"0".equals(id) && !"null".equalsIgnoreCase(id))
                        .findFirst()
                        .orElse(null);
            }
        }

        // 3. Fetch verified game details from Steam & CheapShark
        GameDetailInfo gameInfo = null;
        try {
            gameInfo = gameInformationService.getGameInformation(resolvedGameId, resolvedSteamAppId, resolvedTitle);
        } catch (Exception e) {
            log.warn("Failed to load game info for '{}': {}", resolvedTitle, e.getMessage());
        }

        if (targetGameDeals.isEmpty()) {
            return new GameLookupResponse(resolvedGameId, resolvedTitle, resolvedThumbnail, 0.0, List.of(), gameInfo);
        }

        // Sort store deals with cheapest store first
        targetGameDeals.sort(Comparator.comparingDouble(DealResponse::salePrice));

        return new GameLookupResponse(
                resolvedGameId,
                resolvedTitle,
                resolvedThumbnail,
                cheapestPrice,
                targetGameDeals,
                gameInfo
        );
    }

    /**
     * Filters out in-game purchases/currency packs and groups deals so that
     * only ONE card per game is shown with its lowest price across all stores.
     */
    private List<DealResponse> filterAndDeduplicateDeals(List<DealResponse> deals, String query) {
        if (deals == null || deals.isEmpty()) {
            return List.of();
        }

        // 1. Filter out microtransactions and in-game currency
        List<DealResponse> filtered = deals.stream()
                .filter(d -> !isMicrotransactionOrCurrency(d.title(), query))
                .collect(Collectors.toList());

        // 2. Group by canonical game (by steamAppId or normalized title)
        Map<String, List<DealResponse>> grouped = new LinkedHashMap<>();
        for (DealResponse deal : filtered) {
            String key = getCanonicalGameKey(deal);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(deal);
        }

        // 3. For each game group, select the deal with the lowest sale price
        List<DealResponse> distinctGames = new ArrayList<>();
        for (List<DealResponse> gameDeals : grouped.values()) {
            DealResponse cheapestDeal = gameDeals.stream()
                    .min(Comparator.comparingDouble(DealResponse::salePrice))
                    .orElse(gameDeals.get(0));
            distinctGames.add(cheapestDeal);
        }

        // 4. Sort by relevance to query if search query is provided
        if (query != null && !query.isBlank()) {
            String q = query.trim().toLowerCase();
            distinctGames.sort(Comparator.comparingInt(d -> {
                String t = d.title().toLowerCase().trim();
                if (t.equalsIgnoreCase(q)) return 0;
                if (t.startsWith(q)) return 1;
                if (t.contains(q)) return 2;
                return 3;
            }));
        }

        return distinctGames;
    }

    private String getCanonicalGameKey(DealResponse deal) {
        if (deal.steamAppId() != null && !deal.steamAppId().isBlank()
                && !"0".equals(deal.steamAppId()) && !"null".equalsIgnoreCase(deal.steamAppId())) {
            return "steam:" + deal.steamAppId();
        }
        String norm = deal.title().toLowerCase()
                .replaceAll("(?i)\\b(standard|deluxe|ultimate|gold|goty|game of the year|collector's)\\s+edition\\b", "")
                .replaceAll("[:\\-–—]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return "title:" + norm;
    }

    /**
     * Determines whether a title corresponds to an in-game purchase, virtual currency pack,
     * microtransaction, or soundtrack.
     */
    private boolean isMicrotransactionOrCurrency(String title, String query) {
        if (title == null || title.isBlank()) return false;
        String q = query != null ? query.toLowerCase().trim() : "";
        if (q.contains("credit") || q.contains("point") || q.contains("coin") ||
                q.contains("token") || q.contains("currency") || q.contains("dlc") ||
                q.contains("soundtrack") || q.contains("pass")) {
            return false;
        }

        String t = title.toLowerCase();

        // 1. Numerical credit/point packs: "500 Credits", "2000 Halo Credits", "10,000 Credits", etc.
        if (t.matches(".*\\b\\d+[\\s,]*\\d*\\s*(halo\\s+)?(credits?|points?|coins?|tokens?|v-bucks|fc\\s+points?|cod\\s+points?|bonus).*")) {
            return true;
        }

        // 2. Specific known in-game currency phrases
        if (t.matches(".*\\b(halo\\s+credits?|fc\\s+points?|fifa\\s+points?|cod\\s+points?|apex\\s+coins?|minecoins?|pitcoins?|shark\\s+(cash\\s+)?cards?|virtual\\s+currency|in-game\\s+currency|microtransaction).*")) {
            return true;
        }

        if (t.contains("halo credits") || t.contains("fc points") || t.contains("cod points") ||
                t.contains("shark card") || t.contains("shark cash card")) {
            return true;
        }

        // 3. Soundtracks, artbooks, and season passes when not explicitly searched
        if (t.matches(".*\\b(soundtrack|artbook|season pass\\s*-\\s*|bonus content)\\b.*")) {
            return true;
        }

        return false;
    }

    private List<DealResponse> filterDealsForTargetGame(List<DealResponse> deals, String title, String preferredGameId, String preferredSteamAppId) {
        if (deals == null || deals.isEmpty()) {
            return List.of();
        }

        String targetTitle = title != null ? title.trim().toLowerCase() : "";

        // Match by steamAppId if known
        if (preferredSteamAppId != null && !preferredSteamAppId.isBlank() && !"0".equals(preferredSteamAppId) && !"null".equalsIgnoreCase(preferredSteamAppId)) {
            List<DealResponse> steamMatches = deals.stream()
                    .filter(d -> preferredSteamAppId.equals(d.steamAppId()))
                    .collect(Collectors.toList());
            if (!steamMatches.isEmpty()) {
                return steamMatches;
            }
        }

        // Match by exact title
        List<DealResponse> exactMatches = deals.stream()
                .filter(d -> d.title().equalsIgnoreCase(targetTitle))
                .collect(Collectors.toList());
        if (!exactMatches.isEmpty()) {
            return exactMatches;
        }

        // Match normalized clean title
        String cleanTarget = targetTitle.replaceAll("[:\\-–—]", " ").replaceAll("\\s+", " ").trim();
        List<DealResponse> cleanMatches = deals.stream()
                .filter(d -> {
                    String cleanDeal = d.title().toLowerCase().replaceAll("[:\\-–—]", " ").replaceAll("\\s+", " ").trim();
                    return cleanDeal.equals(cleanTarget) || cleanDeal.startsWith(cleanTarget + " ");
                })
                .collect(Collectors.toList());
        if (!cleanMatches.isEmpty()) {
            return cleanMatches;
        }

        return deals;
    }

    /**
     * Fetches dynamic real-time trending games from Steam storefront (specials & top sellers)
     * and maps each to the cheapest deal available across stores.
     */
    private List<DealResponse> fetchDynamicTrendingDeals(int limit) {
        try {
            String url = "https://store.steampowered.com/api/featuredcategories?l=en&cc=US";
            String response = steamRestClient.get().uri(url).retrieve().body(String.class);
            if (response == null || response.isBlank()) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response);
            List<SteamTrendingItem> items = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();

            // 1. Live Specials (top discounted games trending now)
            JsonNode specials = root.path("specials").path("items");
            if (specials.isArray()) {
                for (JsonNode n : specials) {
                    addTrendingItemIfValid(n, items, seenIds);
                }
            }

            // 2. Global Top Sellers
            JsonNode topSellers = root.path("top_sellers").path("items");
            if (topSellers.isArray()) {
                for (JsonNode n : topSellers) {
                    addTrendingItemIfValid(n, items, seenIds);
                }
            }

            if (items.isEmpty()) {
                return List.of();
            }

            List<DealResponse> result = new ArrayList<>();
            for (SteamTrendingItem item : items) {
                DealResponse bestDeal = findBestCrossStoreDealForTrending(item);
                if (bestDeal != null) {
                    result.add(bestDeal);
                } else {
                    result.add(new DealResponse(
                            "steam-" + item.id(),
                            item.id(),
                            item.name(),
                            "1",
                            "Steam",
                            item.origPrice(),
                            item.finalPrice(),
                            item.discount(),
                            "Trending",
                            item.headerImg(),
                            item.id(),
                            "https://store.steampowered.com/app/" + item.id() + "/"
                    ));
                }
                if (result.size() >= limit * 2) {
                    break;
                }
            }

            return filterAndDeduplicateDeals(result, "");
        } catch (Exception e) {
            log.warn("Failed to fetch dynamic Steam trends: {}", e.getMessage());
            return List.of();
        }
    }

    private void addTrendingItemIfValid(JsonNode node, List<SteamTrendingItem> list, Set<String> seenIds) {
        String id = node.path("id").asText("");
        String name = node.path("name").asText("");
        if (id.isBlank() || name.isBlank() || seenIds.contains(id)) {
            return;
        }

        if (isMicrotransactionOrCurrency(name, "")) {
            return;
        }

        seenIds.add(id);
        double finalPrice = node.path("final_price").asDouble(0.0) / 100.0;
        double origPrice = node.path("original_price").asDouble(0.0) / 100.0;
        double discount = node.path("discount_percent").asDouble(0.0);
        String headerImg = node.path("header_image").asText("");

        list.add(new SteamTrendingItem(id, name, origPrice > 0 ? origPrice : finalPrice, finalPrice, discount, headerImg));
    }

    private DealResponse findBestCrossStoreDealForTrending(SteamTrendingItem item) {
        try {
            List<CheapSharkDealDto> csDeals = gameDealClientService.searchDealsByTitle(item.name(), 5);
            if (csDeals == null || csDeals.isEmpty()) {
                return null;
            }

            List<DealResponse> validDeals = csDeals.stream()
                    .filter(d -> !isMicrotransactionOrCurrency(d.title(), ""))
                    .map(this::mapToDealResponse)
                    .filter(d -> d.title().equalsIgnoreCase(item.name()) ||
                            item.id().equals(d.steamAppId()) ||
                            d.title().toLowerCase().contains(item.name().toLowerCase()))
                    .sorted(Comparator.comparingDouble(DealResponse::salePrice))
                    .collect(Collectors.toList());

            if (!validDeals.isEmpty()) {
                DealResponse csBest = validDeals.get(0);
                if (item.finalPrice() > 0 && item.finalPrice() < csBest.salePrice()) {
                    return new DealResponse(
                            csBest.dealId(),
                            csBest.gameId(),
                            item.name(),
                            "1",
                            "Steam",
                            item.origPrice() > 0 ? item.origPrice() : csBest.normalPrice(),
                            item.finalPrice(),
                            item.discount() > 0 ? item.discount() : csBest.savingsPercent(),
                            "Trending",
                            item.headerImg() != null && !item.headerImg().isBlank() ? item.headerImg() : csBest.thumbnail(),
                            item.id(),
                            "https://store.steampowered.com/app/" + item.id() + "/"
                    );
                }
                return csBest;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
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
            return "https://www.gog.com/en/games?query=${encodedTitle}".replace("${encodedTitle}", encodedTitle);
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

    private record SteamTrendingItem(
            String id,
            String name,
            double origPrice,
            double finalPrice,
            double discount,
            String headerImg
    ) {}
}
