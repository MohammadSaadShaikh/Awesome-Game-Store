package com.gamestore.controller;

import com.gamestore.dto.request.CreateWishlistRequest;
import com.gamestore.dto.response.WishlistResponse;
import com.gamestore.service.WishlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @PostMapping
    public ResponseEntity<WishlistResponse> addToWishlist(@RequestBody CreateWishlistRequest request) {
        WishlistResponse response = wishlistService.addToWishlist(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<WishlistResponse>> getWishlist(
            @RequestParam(name = "email") String email,
            @RequestParam(name = "currency", defaultValue = "USD") String currency
    ) {
        List<WishlistResponse> items = wishlistService.getWishlistByUser(email, currency);
        return ResponseEntity.ok(items);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeFromWishlist(@PathVariable(name = "id") Long id) {
        wishlistService.removeFromWishlist(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync-now")
    public ResponseEntity<Map<String, String>> triggerSyncNow() {
        wishlistService.checkPriceDrops();
        return ResponseEntity.ok(Map.of("message", "Price sync evaluation completed successfully."));
    }
}
