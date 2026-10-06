package com.gamestore.scheduler;

import com.gamestore.service.WishlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceSyncScheduler.class);

    private final WishlistService wishlistService;

    public PriceSyncScheduler(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @Scheduled(cron = "${app.price-check.cron:0 0 */6 * * *}")
    public void runPriceCheckTask() {
        log.info("Executing scheduled price drop check...");
        wishlistService.checkPriceDrops();
    }
}
