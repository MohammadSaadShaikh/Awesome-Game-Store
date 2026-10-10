package com.gamestore.service;

import com.gamestore.dto.response.GameDetailInfo;

public interface GameInformationService {

    GameDetailInfo getGameInformation(String gameId, String steamAppId, String title);
}
