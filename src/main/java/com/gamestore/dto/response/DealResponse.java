package com.gamestore.dto.response;

public record DealResponse(
        String dealId,
        String gameId,
        String title,
        String storeId,
        String storeName,
        double normalPrice,
        double salePrice,
        double savingsPercent,
        String ratingText,
        String thumbnail,
        String steamAppId,
        String storeUrl
) {}
