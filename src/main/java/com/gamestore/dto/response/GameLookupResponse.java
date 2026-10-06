package com.gamestore.dto.response;

import java.util.List;

public record GameLookupResponse(
        String gameId,
        String title,
        String thumbnail,
        double cheapestPrice,
        List<DealResponse> storeDeals
) {}
