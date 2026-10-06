package com.gamestore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CheapSharkStoreDto(
        @JsonProperty("storeID") String storeID,
        @JsonProperty("storeName") String storeName,
        @JsonProperty("isActive") int isActive,
        @JsonProperty("images") StoreImages images
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StoreImages(
            @JsonProperty("banner") String banner,
            @JsonProperty("logo") String logo,
            @JsonProperty("icon") String icon
    ) {}
}
