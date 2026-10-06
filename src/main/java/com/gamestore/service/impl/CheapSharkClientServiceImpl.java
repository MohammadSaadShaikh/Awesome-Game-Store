package com.gamestore.service.impl;

import com.gamestore.dto.CheapSharkDealDto;
import com.gamestore.dto.CheapSharkStoreDto;
import com.gamestore.service.GameDealClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CheapSharkClientServiceImpl implements GameDealClientService {

    private static final Logger log = LoggerFactory.getLogger(CheapSharkClientServiceImpl.class);

    private final RestClient restClient;

    public CheapSharkClientServiceImpl(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<CheapSharkStoreDto> getActiveStores() {
        try {
            List<CheapSharkStoreDto> stores = restClient.get()
                    .uri("/stores")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<CheapSharkStoreDto>>() {});

            if (stores == null) {
                return List.of();
            }

            return stores.stream()
                    .filter(store -> store.isActive() == 1)
                    .collect(Collectors.toList());
        } catch (RestClientException e) {
            log.error("Error fetching stores from CheapShark API: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<CheapSharkDealDto> getTopDeals(int pageSize) {
        try {
            List<CheapSharkDealDto> deals = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/deals")
                            .queryParam("pageSize", pageSize)
                            .queryParam("sortBy", "Deal Rating")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<CheapSharkDealDto>>() {});

            return deals != null ? deals : List.of();
        } catch (RestClientException e) {
            log.error("Error fetching top deals from CheapShark API: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<CheapSharkDealDto> searchDealsByTitle(String title, int limit) {
        try {
            List<CheapSharkDealDto> deals = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/deals")
                            .queryParam("title", title)
                            .queryParam("pageSize", limit)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<CheapSharkDealDto>>() {});

            return deals != null ? deals : List.of();
        } catch (RestClientException e) {
            log.error("Error searching deals for title '{}' from CheapShark API: {}", title, e.getMessage(), e);
            return List.of();
        }
    }
}
