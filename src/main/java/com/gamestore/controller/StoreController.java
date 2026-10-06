package com.gamestore.controller;

import com.gamestore.dto.response.StoreResponse;
import com.gamestore.service.DealAggregatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final DealAggregatorService dealAggregatorService;

    public StoreController(DealAggregatorService dealAggregatorService) {
        this.dealAggregatorService = dealAggregatorService;
    }

    @GetMapping
    public ResponseEntity<List<StoreResponse>> getAllStores() {
        List<StoreResponse> stores = dealAggregatorService.getAllStores();
        return ResponseEntity.ok(stores);
    }
}
