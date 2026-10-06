package com.gamestore.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "wishlist_items")
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String gameTitle;

    private String cheapSharkGameId;

    @Column(nullable = false)
    private Double targetPriceUsd;

    private Double lastCheckedPriceUsd;

    private String preferredCurrency;

    private String bestStoreName;

    private Boolean isTriggered = false;

    private Instant createdAt;

    private Instant updatedAt;

    public WishlistItem() {
    }

    public WishlistItem(Long id, String userEmail, String gameTitle, String cheapSharkGameId, Double targetPriceUsd, Double lastCheckedPriceUsd, String preferredCurrency, String bestStoreName, Boolean isTriggered, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userEmail = userEmail;
        this.gameTitle = gameTitle;
        this.cheapSharkGameId = cheapSharkGameId;
        this.targetPriceUsd = targetPriceUsd;
        this.lastCheckedPriceUsd = lastCheckedPriceUsd;
        this.preferredCurrency = preferredCurrency;
        this.bestStoreName = bestStoreName;
        this.isTriggered = isTriggered != null ? isTriggered : false;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.isTriggered == null) {
            this.isTriggered = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getGameTitle() {
        return gameTitle;
    }

    public void setGameTitle(String gameTitle) {
        this.gameTitle = gameTitle;
    }

    public String getCheapSharkGameId() {
        return cheapSharkGameId;
    }

    public void setCheapSharkGameId(String cheapSharkGameId) {
        this.cheapSharkGameId = cheapSharkGameId;
    }

    public Double getTargetPriceUsd() {
        return targetPriceUsd;
    }

    public void setTargetPriceUsd(Double targetPriceUsd) {
        this.targetPriceUsd = targetPriceUsd;
    }

    public Double getLastCheckedPriceUsd() {
        return lastCheckedPriceUsd;
    }

    public void setLastCheckedPriceUsd(Double lastCheckedPriceUsd) {
        this.lastCheckedPriceUsd = lastCheckedPriceUsd;
    }

    public String getPreferredCurrency() {
        return preferredCurrency;
    }

    public void setPreferredCurrency(String preferredCurrency) {
        this.preferredCurrency = preferredCurrency;
    }

    public String getBestStoreName() {
        return bestStoreName;
    }

    public void setBestStoreName(String bestStoreName) {
        this.bestStoreName = bestStoreName;
    }

    public Boolean getIsTriggered() {
        return isTriggered;
    }

    public void setIsTriggered(Boolean isTriggered) {
        this.isTriggered = isTriggered;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
