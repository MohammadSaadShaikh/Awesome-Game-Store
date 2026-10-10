package com.gamestore.service;

import com.gamestore.dto.response.DealResponse;
import com.gamestore.dto.response.GameLookupResponse;
import com.gamestore.dto.response.StoreResponse;

import java.util.List;

public interface DealAggregatorService {

    List<StoreResponse> getAllStores();

    List<DealResponse> getTopDeals(int limit, String sortBy);

    List<DealResponse> searchDeals(String title, String storeId, Double maxPrice);

    GameLookupResponse getGameDealsGrouped(String title);

    GameLookupResponse getGameDealsGrouped(String title, String preferredGameId, String preferredSteamAppId);
}
