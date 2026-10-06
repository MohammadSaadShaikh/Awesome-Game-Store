package com.gamestore.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/currencies")
public class CurrencyController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> getCurrencies() {
        Map<String, Object> rates = new LinkedHashMap<>();
        rates.put("base", "USD");
        
        Map<String, Object> currencyDetails = new LinkedHashMap<>();
        currencyDetails.put("USD", Map.of("symbol", "$", "rate", 1.0, "locale", "en-US", "name", "US Dollar"));
        currencyDetails.put("EUR", Map.of("symbol", "€", "rate", 0.92, "locale", "de-DE", "name", "Euro"));
        currencyDetails.put("GBP", Map.of("symbol", "£", "rate", 0.79, "locale", "en-GB", "name", "British Pound"));
        currencyDetails.put("INR", Map.of("symbol", "₹", "rate", 83.5, "locale", "en-IN", "name", "Indian Rupee"));
        currencyDetails.put("CAD", Map.of("symbol", "CA$", "rate", 1.36, "locale", "en-CA", "name", "Canadian Dollar"));
        currencyDetails.put("AUD", Map.of("symbol", "A$", "rate", 1.52, "locale", "en-AU", "name", "Australian Dollar"));
        
        rates.put("rates", currencyDetails);
        return ResponseEntity.ok(rates);
    }
}
