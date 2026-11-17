package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScriptMinimapIconTest {
	@Test
	void fixedIconKeepsItsPointColourAndFogMask() {
		final ScriptMinimapIcon icon = new ScriptMinimapIcon(10f, -20f, null, 255, 0, 51, "ping.blp", 4);
		assertEquals(10f, icon.getX());
		assertEquals(-20f, icon.getY());
		assertEquals(1f, icon.getRed());
		assertEquals(0f, icon.getGreen());
		assertEquals(0.2f, icon.getBlue(), 1e-6f);
		assertEquals("ping.blp", icon.getPath());
		assertEquals(4, icon.getFogMask());
	}

	@Test
	void startsShownAndNeverOrphanedWithoutAUnit() {
		final ScriptMinimapIcon icon = new ScriptMinimapIcon(0f, 0f, null, 255, 255, 255, "", 7);
		assertTrue(icon.isVisible());
		icon.setVisible(false);
		assertFalse(icon.isVisible());
		icon.setOrphanDestroy(true);
		assertFalse(icon.isOrphaned());
	}
}
