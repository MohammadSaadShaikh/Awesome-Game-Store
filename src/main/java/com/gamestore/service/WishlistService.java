package com.gamestore.service;

import com.gamestore.dto.request.CreateWishlistRequest;
import com.gamestore.dto.response.WishlistResponse;

import java.util.List;

public interface WishlistService {

    WishlistResponse addToWishlist(CreateWishlistRequest request);

    List<WishlistResponse> getWishlistByUser(String email, String displayCurrency);

    void removeFromWishlist(Long id);

    void checkPriceDrops();
}
