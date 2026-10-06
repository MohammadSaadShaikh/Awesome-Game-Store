package com.gamestore.service.impl;

import com.gamestore.dto.request.CreateWishlistRequest;
import com.gamestore.dto.response.GameLookupResponse;
import com.gamestore.dto.response.WishlistResponse;
import com.gamestore.model.WishlistItem;
import com.gamestore.repository.WishlistRepository;
import com.gamestore.service.DealAggregatorService;
import com.gamestore.service.WishlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class WishlistServiceImpl implements WishlistService {

    private static final Logger log = LoggerFactory.getLogger(WishlistServiceImpl.class);

    private static final Map<String, Double> CURRENCY_RATES = Map.of(
            "USD", 1.0,
            "EUR", 0.92,
            "GBP", 0.79,
            "INR", 83.5,
            "CAD", 1.36,
            "AUD", 1.52
    );

    private final WishlistRepository wishlistRepository;
    private final DealAggregatorService dealAggregatorService;

    public WishlistServiceImpl(WishlistRepository wishlistRepository, DealAggregatorService dealAggregatorService) {
        this.wishlistRepository = wishlistRepository;
        this.dealAggregatorService = dealAggregatorService;
    }

    @Override
    @Transactional
    public WishlistResponse addToWishlist(CreateWishlistRequest request) {
        if (request.userEmail() == null || request.userEmail().isBlank()) {
            throw new IllegalArgumentException("User email is required");
        }
        if (request.gameTitle() == null || request.gameTitle().isBlank()) {
            throw new IllegalArgumentException("Game title is required");
        }
        if (request.targetPrice() == null || request.targetPrice() < 0) {
            throw new IllegalArgumentException("Target price must be non-negative");
        }

        String currency = (request.currency() != null && CURRENCY_RATES.containsKey(request.currency().toUpperCase()))
                ? request.currency().toUpperCase() : "USD";

        double rate = CURRENCY_RATES.getOrDefault(currency, 1.0);
        double targetPriceUsd = request.targetPrice() / rate;

        // Fetch current live deal price for this game
        GameLookupResponse lookup = dealAggregatorService.getGameDealsGrouped(request.gameTitle());
        double currentBestPriceUsd = lookup.cheapestPrice();
        String bestStoreName = "Unknown Store";
        if (lookup.storeDeals() != null && !lookup.storeDeals().isEmpty()) {
            bestStoreName = lookup.storeDeals().get(0).storeName();
        }

        boolean isTriggered = (currentBestPriceUsd > 0 && currentBestPriceUsd <= targetPriceUsd);

        // Check if item already exists for user & title
        WishlistItem item = wishlistRepository.findByUserEmailAndGameTitle(request.userEmail(), request.gameTitle())
                .orElseGet(WishlistItem::new);

        item.setUserEmail(request.userEmail());
        item.setGameTitle(request.gameTitle());
        item.setCheapSharkGameId(request.gameId() != null ? request.gameId() : lookup.gameId());
        item.setTargetPriceUsd(targetPriceUsd);
        item.setLastCheckedPriceUsd(currentBestPriceUsd);
        item.setPreferredCurrency(currency);
        item.setBestStoreName(bestStoreName);
        item.setIsTriggered(isTriggered);

        WishlistItem saved = wishlistRepository.save(item);
        log.info("Saved wishlist item ID {} for email {}", saved.getId(), saved.getUserEmail());

        return mapToResponse(saved, currency);
    }

    @Override
    public List<WishlistResponse> getWishlistByUser(String email, String displayCurrency) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        String currency = (displayCurrency != null && CURRENCY_RATES.containsKey(displayCurrency.toUpperCase()))
                ? displayCurrency.toUpperCase() : "USD";

        List<WishlistItem> items = wishlistRepository.findByUserEmail(email);
        return items.stream()
                .map(item -> mapToResponse(item, currency))
                .toList();
    }

    @Override
    @Transactional
    public void removeFromWishlist(Long id) {
        wishlistRepository.deleteById(id);
        log.info("Removed wishlist item ID {}", id);
    }

    @Override
    @Transactional
    public void checkPriceDrops() {
        log.info("Starting Price Sync Engine evaluation for untriggered wishlist items...");
        List<WishlistItem> pendingItems = wishlistRepository.findByIsTriggeredFalse();

        if (pendingItems.isEmpty()) {
            log.info("No pending wishlist items to check.");
            return;
        }

        int alertsTriggered = 0;

        for (WishlistItem item : pendingItems) {
            try {
                GameLookupResponse lookup = dealAggregatorService.getGameDealsGrouped(item.getGameTitle());
                if (lookup == null || lookup.storeDeals() == null || lookup.storeDeals().isEmpty()) {
                    continue;
                }

                double currentPriceUsd = lookup.cheapestPrice();
                String bestStoreName = lookup.storeDeals().get(0).storeName();

                item.setLastCheckedPriceUsd(currentPriceUsd);
                item.setBestStoreName(bestStoreName);

                if (currentPriceUsd > 0 && currentPriceUsd <= item.getTargetPriceUsd()) {
                    item.setIsTriggered(true);
                    alertsTriggered++;
                    log.info("[PRICE ALERT] Game '{}' dropped to ${} on {}! Notifying {}...",
                            item.getGameTitle(), currentPriceUsd, bestStoreName, item.getUserEmail());
                }

                wishlistRepository.save(item);
            } catch (Exception e) {
                log.error("Error evaluating price drop for item ID {}: {}", item.getId(), e.getMessage());
            }
        }

        log.info("Price Sync Engine run completed. Total alerts triggered: {}", alertsTriggered);
    }

    private WishlistResponse mapToResponse(WishlistItem item, String displayCurrency) {
        double rate = CURRENCY_RATES.getOrDefault(displayCurrency, 1.0);

        double targetPriceConverted = Math.round(item.getTargetPriceUsd() * rate * 100.0) / 100.0;
        double currentPriceConverted = Math.round((item.getLastCheckedPriceUsd() != null ? item.getLastCheckedPriceUsd() : 0.0) * rate * 100.0) / 100.0;

        double savingsPercent = 0.0;
        if (targetPriceConverted > 0 && currentPriceConverted < targetPriceConverted) {
            savingsPercent = Math.round(((targetPriceConverted - currentPriceConverted) / targetPriceConverted) * 100.0 * 100.0) / 100.0;
        }

        String status = item.getIsTriggered() ? "TARGET MET" : "TRACKING";

        return new WishlistResponse(
                item.getId(),
                item.getUserEmail(),
                item.getGameTitle(),
                item.getCheapSharkGameId(),
                targetPriceConverted,
                currentPriceConverted,
                displayCurrency,
                item.getBestStoreName(),
                savingsPercent,
                item.getIsTriggered(),
                status
        );
    }
}
