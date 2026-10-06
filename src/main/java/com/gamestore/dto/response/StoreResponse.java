package com.gamestore.dto.response;

public record StoreResponse(
        String id,
        String name,
        boolean active,
        String bannerUrl,
        String logoUrl,
        String iconUrl
) {}
