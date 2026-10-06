package com.gamestore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CheapSharkDealDto(
        @JsonProperty("gameID") String gameID,
        @JsonProperty("dealID") String dealID,
        @JsonProperty("storeID") String storeID,
        @JsonProperty("title") String title,
        @JsonProperty("salePrice") String salePrice,
        @JsonProperty("normalPrice") String normalPrice,
        @JsonProperty("savings") String savings,
        @JsonProperty("metacriticScore") String metacriticScore,
        @JsonProperty("steamRatingText") String steamRatingText,
        @JsonProperty("steamRatingPercent") String steamRatingPercent,
        @JsonProperty("steamAppID") String steamAppID,
        @JsonProperty("thumb") String thumb
) {}
