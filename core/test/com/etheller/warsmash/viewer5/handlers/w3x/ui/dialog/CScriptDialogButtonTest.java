package com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A quit button keeps where it leaves to, so a loaded game's open end dialog exits like a fresh one. */
class CScriptDialogButtonTest {
	@Test
	void quitButtonRemembersScoreScreenExit() {
		final CScriptDialogButton button = new CScriptDialogButton(null, null, "Continue", 0);
		assertFalse(button.isQuitButton());
		button.setQuitAction(true, () -> {
		});
		assertTrue(button.isQuitButton());
		assertTrue(button.isQuitToScoreScreen());
	}

	@Test
	void quitButtonWithoutScoreScreen() {
		final CScriptDialogButton button = new CScriptDialogButton(null, null, "Quit", 0);
		button.setQuitAction(false, () -> {
		});
		assertTrue(button.isQuitButton());
		assertFalse(button.isQuitToScoreScreen());
	}
}
