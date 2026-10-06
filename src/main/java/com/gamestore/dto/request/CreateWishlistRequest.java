package com.gamestore.dto.request;

public record CreateWishlistRequest(
        String userEmail,
        String gameTitle,
        String gameId,
        Double targetPrice,
        String currency
) {}
