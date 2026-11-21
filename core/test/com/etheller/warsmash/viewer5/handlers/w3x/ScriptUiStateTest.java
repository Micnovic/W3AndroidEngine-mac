package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScriptUiStateTest {
	@Test
	void everythingIsShownAndActiveUntilAScriptSaysOtherwise() {
		final ScriptUiState state = new ScriptUiState();
		assertTrue(state.isTerrainShown());
		assertTrue(state.isSkyBoxShown());
		assertTrue(state.isClientActive());
		assertTrue(state.isSelectionEnabled());
		assertFalse(state.isPauseThematicMusicOnFocusLost());
		assertNull(state.getMinimapTerrainTexture());
		assertTrue(state.getCommandButtonEffects().isEmpty());
	}

	@Test
	void theSwitchesKeepWhatWasSet() {
		final ScriptUiState state = new ScriptUiState();
		state.setTerrainShown(false);
		state.setSkyBoxShown(false);
		state.setClientActive(false);
		state.setSelectionEnabled(false);
		assertFalse(state.isTerrainShown());
		assertFalse(state.isSkyBoxShown());
		assertFalse(state.isClientActive());
		assertFalse(state.isSelectionEnabled());
	}

	@Test
	void theMusicIsHeldOnlyWhenTheMapAskedForItAndTheFocusIsLost() {
		final ScriptUiState state = new ScriptUiState();
		state.setClientActive(false);
		assertFalse(state.isMusicHeldByFocus());
		state.setPauseThematicMusicOnFocusLost(true);
		assertTrue(state.isMusicHeldByFocus());
		state.setClientActive(true);
		assertFalse(state.isMusicHeldByFocus());
	}
}
