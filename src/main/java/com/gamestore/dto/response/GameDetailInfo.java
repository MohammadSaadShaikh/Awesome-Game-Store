package com.gamestore.dto.response;

import java.util.List;

public record GameDetailInfo(
        String description,
        String shortDescription,
        List<String> developers,
        List<String> publishers,
        String releaseDate,
        List<String> genres,
        Integer metacriticScore,
        String metacriticUrl,
        Double cheapestPriceEver,
        String cheapestPriceEverDate,
        List<String> screenshots,
        String minimumRequirements,
        String recommendedRequirements,
        String headerImage,
        String officialWebsite,
        String sourceWebsite
) {}
