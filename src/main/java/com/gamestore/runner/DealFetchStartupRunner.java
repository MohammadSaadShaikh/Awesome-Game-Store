package com.gamestore.runner;

import com.gamestore.dto.CheapSharkDealDto;
import com.gamestore.dto.CheapSharkStoreDto;
import com.gamestore.service.GameDealClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DealFetchStartupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DealFetchStartupRunner.class);

    private final GameDealClientService gameDealClientService;

    public DealFetchStartupRunner(GameDealClientService gameDealClientService) {
        this.gameDealClientService = gameDealClientService;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=================================================");
        log.info("Starting CheapShark API Startup Verification Runner");
        log.info("=================================================");

        // Step 1: Fetch active stores and create lookup map
        List<CheapSharkStoreDto> activeStores = gameDealClientService.getActiveStores();
        Map<String, String> storeMap = activeStores.stream()
                .collect(Collectors.toMap(
                        CheapSharkStoreDto::storeID,
                        CheapSharkStoreDto::storeName,
                        (existing, replacement) -> existing
                ));

        // Step 2: Log active stores count
        log.info("Total active stores found: {}", storeMap.size());

        // Step 3: Fetch and log top 5 deals
        log.info("-------------------------------------------------");
        log.info("Fetching Top 5 Current Deals:");
        log.info("-------------------------------------------------");
        List<CheapSharkDealDto> topDeals = gameDealClientService.getTopDeals(5);
        for (CheapSharkDealDto deal : topDeals) {
            String storeName = storeMap.getOrDefault(deal.storeID(), "Store #" + deal.storeID());
            String formattedSavings = formatSavings(deal.savings());
            log.info("-> [{}] {} | Normal: ${} | Sale: ${} ({})",
                    storeName, deal.title(), deal.normalPrice(), deal.salePrice(), formattedSavings);
        }

        // Step 4: Search deals for Cyberpunk 2077
        log.info("-------------------------------------------------");
        log.info("Searching Deals for 'Cyberpunk 2077':");
        log.info("-------------------------------------------------");
        List<CheapSharkDealDto> cyberpunkDeals = gameDealClientService.searchDealsByTitle("Cyberpunk 2077", 5);
        for (CheapSharkDealDto deal : cyberpunkDeals) {
            String storeName = storeMap.getOrDefault(deal.storeID(), "Store #" + deal.storeID());
            String formattedSavings = formatSavings(deal.savings());
            log.info("-> [{}] {} | Normal: ${} | Sale: ${} ({})",
                    storeName, deal.title(), deal.normalPrice(), deal.salePrice(), formattedSavings);
        }

        log.info("=================================================");
        log.info("CheapShark API Verification Runner Completed");
        log.info("=================================================");
    }

    private String formatSavings(String savingsRaw) {
        if (savingsRaw == null || savingsRaw.isBlank()) {
            return "0% OFF";
        }
        try {
            double savings = Double.parseDouble(savingsRaw);
            return String.format("%.0f%% OFF", savings);
        } catch (NumberFormatException e) {
            return savingsRaw + "% OFF";
        }
    }
}
