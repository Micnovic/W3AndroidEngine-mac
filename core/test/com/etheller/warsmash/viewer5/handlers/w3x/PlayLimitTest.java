package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayLimitTest {
	@Test
	void playsPastTheLimitAreRefusedUntilOneEnds() {
		final PlayLimit limit = new PlayLimit();
		assertTrue(limit.tryStart(0, 1000, 2));
		assertTrue(limit.tryStart(100, 1000, 2));
		assertFalse(limit.tryStart(200, 1000, 2));
		// the first play has ended
		assertTrue(limit.tryStart(1000, 1000, 2));
		assertFalse(limit.tryStart(1050, 1000, 2));
	}

	@Test
	void aRefusedPlayTakesNoPlace() {
		final PlayLimit limit = new PlayLimit();
		assertTrue(limit.tryStart(0, 500, 1));
		assertFalse(limit.tryStart(100, 5000, 1));
		assertTrue(limit.tryStart(500, 500, 1));
	}

	@Test
	void aPlayOfUnknownLengthIsNeverLimited() {
		final PlayLimit limit = new PlayLimit();
		for (int i = 0; i < 20; i++) {
			assertTrue(limit.tryStart(0, 0, 1));
		}
	}
}
