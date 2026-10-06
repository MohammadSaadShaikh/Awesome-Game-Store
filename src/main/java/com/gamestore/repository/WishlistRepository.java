package com.gamestore.repository;

import com.gamestore.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUserEmail(String userEmail);

    Optional<WishlistItem> findByUserEmailAndGameTitle(String userEmail, String gameTitle);

    List<WishlistItem> findByIsTriggeredFalse();
}
