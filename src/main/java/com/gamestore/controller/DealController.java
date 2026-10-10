package com.gamestore.controller;

import com.gamestore.dto.response.DealResponse;
import com.gamestore.dto.response.GameDetailInfo;
import com.gamestore.dto.response.GameLookupResponse;
import com.gamestore.service.DealAggregatorService;
import com.gamestore.service.GameInformationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deals")
public class DealController {

    private final DealAggregatorService dealAggregatorService;
    private final GameInformationService gameInformationService;

    public DealController(DealAggregatorService dealAggregatorService, GameInformationService gameInformationService) {
        this.dealAggregatorService = dealAggregatorService;
        this.gameInformationService = gameInformationService;
    }

    @GetMapping
    public ResponseEntity<List<DealResponse>> getTopDeals(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "sortBy", defaultValue = "Deal Rating") String sortBy
    ) {
        List<DealResponse> deals = dealAggregatorService.getTopDeals(limit, sortBy);
        return ResponseEntity.ok(deals);
    }

    @GetMapping("/search")
    public ResponseEntity<List<DealResponse>> searchDeals(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "storeId", required = false) String storeId,
            @RequestParam(name = "maxPrice", required = false) Double maxPrice
    ) {
        List<DealResponse> deals = dealAggregatorService.searchDeals(query, storeId, maxPrice);
        return ResponseEntity.ok(deals);
    }

    @GetMapping("/compare")
    public ResponseEntity<GameLookupResponse> compareDeals(
            @RequestParam(name = "title") String title,
            @RequestParam(name = "gameId", required = false) String gameId,
            @RequestParam(name = "steamAppId", required = false) String steamAppId
    ) {
        GameLookupResponse lookup = dealAggregatorService.getGameDealsGrouped(title, gameId, steamAppId);
        return ResponseEntity.ok(lookup);
    }

    @GetMapping("/info")
    public ResponseEntity<GameDetailInfo> getGameInfo(
            @RequestParam(name = "title") String title,
            @RequestParam(name = "gameId", required = false) String gameId,
            @RequestParam(name = "steamAppId", required = false) String steamAppId
    ) {
        GameDetailInfo info = gameInformationService.getGameInformation(gameId, steamAppId, title);
        return ResponseEntity.ok(info);
    }
}
