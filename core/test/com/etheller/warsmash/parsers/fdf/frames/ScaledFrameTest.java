package com.etheller.warsmash.parsers.fdf.frames;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A scaled frame is hit where it is drawn, and a script's hiding is a layer over the game's own showing. */
class ScaledFrameTest {
	@Test
	void scaleMapsPointsAboutTheCenter() {
		final SimpleFrame frame = new SimpleFrame("scaled", null);
		frame.getRenderBounds().set(10, 10, 20, 20);
		assertEquals(35f, AbstractRenderableFrame.localX(frame, 35f));
		frame.setRenderScale(2f);
		assertEquals(20f, AbstractRenderableFrame.localX(frame, 20f));
		assertEquals(22.5f, AbstractRenderableFrame.localX(frame, 25f));
		assertEquals(22.5f, AbstractRenderableFrame.localY(frame, 25f));
	}

	@Test
	void scriptHidingKeepsTheGamesOwnState() {
		final SimpleFrame frame = new SimpleFrame("hidden", null);
		frame.setScriptHidden(true);
		assertFalse(frame.isVisible());
		frame.setVisible(true);
		assertFalse(frame.isVisible());
		frame.setScriptHidden(false);
		assertTrue(frame.isVisible());
	}
}
