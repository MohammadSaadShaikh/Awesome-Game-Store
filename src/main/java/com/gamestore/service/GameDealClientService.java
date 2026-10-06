package com.gamestore.service;

import com.gamestore.dto.CheapSharkDealDto;
import com.gamestore.dto.CheapSharkStoreDto;

import java.util.List;

public interface GameDealClientService {

    List<CheapSharkStoreDto> getActiveStores();

    List<CheapSharkDealDto> getTopDeals(int pageSize);

    List<CheapSharkDealDto> searchDealsByTitle(String title, int limit);
}
