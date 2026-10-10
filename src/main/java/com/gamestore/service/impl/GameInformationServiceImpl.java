package com.gamestore.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamestore.dto.response.GameDetailInfo;
import com.gamestore.service.GameInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class GameInformationServiceImpl implements GameInformationService {

    private static final Logger log = LoggerFactory.getLogger(GameInformationServiceImpl.class);

    private final RestClient externalRestClient;
    private final ObjectMapper objectMapper;
    private final java.util.Map<String, GameDetailInfo> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public GameInformationServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(6000);
        factory.setReadTimeout(6000);

        this.externalRestClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "GameStoreAggregator/1.0 (contact@gamestore.example.com)")
                .build();
    }

    @Override
    public GameDetailInfo getGameInformation(String gameId, String steamAppId, String title) {
        String cacheKey = (title != null ? title.trim().toLowerCase() : "") + "#" 
                + (steamAppId != null ? steamAppId : "") + "#" 
                + (gameId != null ? gameId : "");

        GameDetailInfo cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String resolvedSteamAppId = steamAppId;
        CheapSharkGameInfo cheapSharkData = null;

        if (gameId != null && !gameId.isBlank()) {
            cheapSharkData = fetchCheapSharkGameInfo(gameId);
        }

        if (cheapSharkData == null && title != null && !title.isBlank()) {
            cheapSharkData = fetchCheapSharkGameByTitle(title);
        }

        if (cheapSharkData != null && cheapSharkData.steamAppId != null && !cheapSharkData.steamAppId.isBlank()) {
            if (resolvedSteamAppId == null || resolvedSteamAppId.isBlank() || "0".equals(resolvedSteamAppId) || "null".equalsIgnoreCase(resolvedSteamAppId)) {
                resolvedSteamAppId = cheapSharkData.steamAppId;
            }
        }

        if (resolvedSteamAppId == null || resolvedSteamAppId.isBlank() || "0".equals(resolvedSteamAppId) || "null".equalsIgnoreCase(resolvedSteamAppId)) {
            resolvedSteamAppId = searchSteamAppIdByTitle(title);
        }

        SteamDetails steamData = null;
        if (resolvedSteamAppId != null && !resolvedSteamAppId.isBlank()) {
            steamData = fetchSteamAppDetails(resolvedSteamAppId);
        }

        GameDetailInfo result = mergeGameInformation(title, steamData, cheapSharkData);
        if (result != null) {
            cache.put(cacheKey, result);
            if (title != null && !title.isBlank()) {
                cache.put(title.trim().toLowerCase(), result);
            }
        }
        return result;
    }

    private CheapSharkGameInfo fetchCheapSharkGameByTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        try {
            String encoded = URLEncoder.encode(title, StandardCharsets.UTF_8);
            String url = "https://www.cheapshark.com/api/1.0/games?title=" + encoded + "&limit=1";
            String response = externalRestClient.get().uri(url).retrieve().body(String.class);
            if (response != null && !response.isBlank()) {
                JsonNode array = objectMapper.readTree(response);
                if (array.isArray() && !array.isEmpty()) {
                    JsonNode first = array.get(0);
                    String foundGameId = first.path("gameID").asText(null);
                    String foundSteamAppId = first.path("steamAppID").asText(null);
                    if (foundGameId != null && !foundGameId.isBlank()) {
                        CheapSharkGameInfo info = fetchCheapSharkGameInfo(foundGameId);
                        if (info != null) {
                            return info;
                        }
                    }
                    if (foundSteamAppId != null && !foundSteamAppId.isBlank() && !"0".equals(foundSteamAppId)) {
                        return new CheapSharkGameInfo(foundSteamAppId, null, null);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to lookup CheapShark info by title '{}': {}", title, e.getMessage());
        }
        return null;
    }

    private String searchSteamAppIdByTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            String url = "https://store.steampowered.com/api/storesearch/?term=" + encodedTitle + "&l=english&cc=US";

            String response = externalRestClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (response != null && !response.isBlank()) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode items = root.path("items");
                if (items.isArray() && !items.isEmpty()) {
                    JsonNode firstItem = items.get(0);
                    long id = firstItem.path("id").asLong(0);
                    if (id > 0) {
                        return String.valueOf(id);
                    }
                }
            }

            // Fallback: strip punctuation/special editions if not found
            String stripped = title.replaceAll("\\(.*?\\)", "").replaceAll("\\[.*?\\]", "").trim();
            if (!stripped.equalsIgnoreCase(title.trim()) && !stripped.isBlank()) {
                String encodedStripped = URLEncoder.encode(stripped, StandardCharsets.UTF_8);
                String fallbackUrl = "https://store.steampowered.com/api/storesearch/?term=" + encodedStripped + "&l=english&cc=US";
                String fallbackResp = externalRestClient.get().uri(fallbackUrl).retrieve().body(String.class);
                if (fallbackResp != null && !fallbackResp.isBlank()) {
                    JsonNode root = objectMapper.readTree(fallbackResp);
                    JsonNode items = root.path("items");
                    if (items.isArray() && !items.isEmpty()) {
                        long id = items.get(0).path("id").asLong(0);
                        if (id > 0) return String.valueOf(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to lookup Steam App ID for title '{}': {}", title, e.getMessage());
        }
        return null;
    }

    private SteamDetails fetchSteamAppDetails(String steamAppId) {
        try {
            String url = "https://store.steampowered.com/api/appdetails?appids=" + steamAppId;

            String response = externalRestClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode appNode = root.path(steamAppId);
            if (!appNode.path("success").asBoolean(false)) {
                return null;
            }

            JsonNode data = appNode.path("data");

            String shortDesc = data.path("short_description").asText("");
            String detailedDesc = data.path("detailed_description").asText("");

            List<String> developers = new ArrayList<>();
            JsonNode devNodes = data.path("developers");
            if (devNodes.isArray()) {
                devNodes.forEach(d -> developers.add(d.asText()));
            }

            List<String> publishers = new ArrayList<>();
            JsonNode pubNodes = data.path("publishers");
            if (pubNodes.isArray()) {
                pubNodes.forEach(p -> publishers.add(p.asText()));
            }

            String releaseDate = data.path("release_date").path("date").asText("");

            List<String> genres = new ArrayList<>();
            JsonNode genreNodes = data.path("genres");
            if (genreNodes.isArray()) {
                genreNodes.forEach(g -> genres.add(g.path("description").asText()));
            }

            Integer metacriticScore = null;
            String metacriticUrl = null;
            if (data.has("metacritic")) {
                JsonNode meta = data.path("metacritic");
                metacriticScore = meta.path("score").asInt();
                metacriticUrl = meta.path("url").asText(null);
            }

            List<String> screenshots = new ArrayList<>();
            JsonNode ssNodes = data.path("screenshots");
            if (ssNodes.isArray()) {
                for (JsonNode s : ssNodes) {
                    String imgUrl = s.path("path_full").asText("");
                    if (!imgUrl.isBlank()) {
                        screenshots.add(imgUrl);
                    }
                    if (screenshots.size() >= 8) break; // limit to 8 screenshots
                }
            }

            String minReq = data.path("pc_requirements").path("minimum").asText("");
            String recReq = data.path("pc_requirements").path("recommended").asText("");
            String headerImg = data.path("header_image").asText("");
            String website = data.path("website").asText("");

            return new SteamDetails(
                    shortDesc,
                    detailedDesc,
                    developers,
                    publishers,
                    releaseDate,
                    genres,
                    metacriticScore,
                    metacriticUrl,
                    screenshots,
                    minReq,
                    recReq,
                    headerImg,
                    website
            );
        } catch (Exception e) {
            log.warn("Failed to fetch Steam app details for AppID '{}': {}", steamAppId, e.getMessage());
            return null;
        }
    }

    private CheapSharkGameInfo fetchCheapSharkGameInfo(String gameId) {
        try {
            String url = "https://www.cheapshark.com/api/1.0/games?id=" + gameId;

            String response = externalRestClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);

            String steamAppId = root.path("info").path("steamAppID").asText(null);
            Double cheapestPriceEver = null;
            String cheapestPriceEverDate = null;

            if (root.has("cheapestPriceEver")) {
                JsonNode cpe = root.path("cheapestPriceEver");
                if (cpe.has("price")) {
                    cheapestPriceEver = cpe.path("price").asDouble();
                }
                if (cpe.has("date")) {
                    long epoch = cpe.path("date").asLong(0);
                    if (epoch > 0) {
                        cheapestPriceEverDate = Instant.ofEpochSecond(epoch)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                                .toString();
                    }
                }
            }

            return new CheapSharkGameInfo(steamAppId, cheapestPriceEver, cheapestPriceEverDate);
        } catch (Exception e) {
            log.warn("Failed to fetch CheapShark game info for GameID '{}': {}", gameId, e.getMessage());
            return null;
        }
    }

    private GameDetailInfo mergeGameInformation(String title, SteamDetails steam, CheapSharkGameInfo cheapShark) {
        String description = steam != null && !steam.detailedDesc.isBlank() ? steam.detailedDesc : null;
        String shortDescription = steam != null && !steam.shortDesc.isBlank() ? steam.shortDesc : null;

        if (shortDescription == null && description == null) {
            shortDescription = "Official video game price comparison and store tracking for " + title + ". Live storefront pricing updated in real-time across Steam, Epic Games Store, GOG, and top authorized digital stores.";
        }

        List<String> developers = steam != null ? steam.developers : List.of();
        List<String> publishers = steam != null ? steam.publishers : List.of();
        String releaseDate = steam != null ? steam.releaseDate : null;
        List<String> genres = steam != null ? steam.genres : List.of();

        Integer metacriticScore = steam != null ? steam.metacriticScore : null;
        String metacriticUrl = steam != null ? steam.metacriticUrl : null;

        Double cheapestPriceEver = cheapShark != null ? cheapShark.cheapestPriceEver : null;
        String cheapestPriceEverDate = cheapShark != null ? cheapShark.cheapestPriceEverDate : null;

        List<String> screenshots = steam != null ? steam.screenshots : List.of();
        String minimumRequirements = steam != null ? steam.minReq : null;
        String recommendedRequirements = steam != null ? steam.recReq : null;
        String headerImage = steam != null ? steam.headerImg : null;
        String officialWebsite = steam != null ? steam.website : null;

        String sourceWebsite = "Steam Storefront (Official API) & CheapShark";

        return new GameDetailInfo(
                description,
                shortDescription,
                developers,
                publishers,
                releaseDate,
                genres,
                metacriticScore,
                metacriticUrl,
                cheapestPriceEver,
                cheapestPriceEverDate,
                screenshots,
                minimumRequirements,
                recommendedRequirements,
                headerImage,
                officialWebsite,
                sourceWebsite
        );
    }

    private record SteamDetails(
            String shortDesc,
            String detailedDesc,
            List<String> developers,
            List<String> publishers,
            String releaseDate,
            List<String> genres,
            Integer metacriticScore,
            String metacriticUrl,
            List<String> screenshots,
            String minReq,
            String recReq,
            String headerImg,
            String website
    ) {}

    private record CheapSharkGameInfo(
            String steamAppId,
            Double cheapestPriceEver,
            String cheapestPriceEverDate
    ) {}
}
