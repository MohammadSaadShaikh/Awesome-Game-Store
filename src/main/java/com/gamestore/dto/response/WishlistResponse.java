package com.gamestore.dto.response;

public record WishlistResponse(
        Long id,
        String userEmail,
        String gameTitle,
        String gameId,
        Double targetPrice,
        Double currentBestPrice,
        String currency,
        String bestStore,
        Double discountSavings,
        Boolean isTriggered,
        String status
) {}
