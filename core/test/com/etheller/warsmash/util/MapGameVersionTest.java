package com.etheller.warsmash.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The game a map is played in is worked out from the map alone, for that map's game; nothing of the last map loaded
 * carries over to the next one.
 */
class MapGameVersionTest {
	@Test
	void reignOfChaosCampaignByFileName() {
		assertEquals(0, WarsmashConstants.mapGameVersion("Maps\\Campaign\\Human01.w3m", -1, true));
		assertEquals(0, WarsmashConstants.mapGameVersion("Maps/Campaign/Reforged/ROC/Human01.w3x", -1, true));
		assertEquals(WarsmashConstants.GAME_VERSION,
				WarsmashConstants.mapGameVersion("Maps\\FrozenThrone\\Campaign\\Human01x.w3x", -1, true));
	}

	@Test
	void gameDataVersionOfACampaignMapWins() {
		assertEquals(Math.min(1, WarsmashConstants.GAME_VERSION),
				WarsmashConstants.mapGameVersion("Maps/Campaign/Reforged/ROC/Human01.w3x", 1, true));
		assertEquals(0, WarsmashConstants.mapGameVersion("Maps\\Campaign\\Human01.w3x", 0, true));
	}

	@Test
	void otherMapsPlayTheConfiguredGame() {
		assertEquals(WarsmashConstants.GAME_VERSION, WarsmashConstants.mapGameVersion("Maps\\(2)BootyBay.w3m", 0, false));
		assertEquals(WarsmashConstants.GAME_VERSION, WarsmashConstants.mapGameVersion(null, -1, false));
	}

	@Test
	void theFlagNotTheFolderDecides() {
		assertEquals(WarsmashConstants.GAME_VERSION,
				WarsmashConstants.mapGameVersion("Maps/Campaign/Human01.w3m", -1, false));
		assertEquals(0, WarsmashConstants.mapGameVersion("Downloads/Custom.w3m", -1, true));
	}
}
