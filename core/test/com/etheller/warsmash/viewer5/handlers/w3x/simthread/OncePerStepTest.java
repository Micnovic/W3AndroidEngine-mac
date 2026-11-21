package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OncePerStepTest {
	@Test
	void eachKindPassesOncePerStep() {
		final OncePerStep notices = new OncePerStep(3);
		assertTrue(notices.first(0, 0));
		assertFalse(notices.first(0, 0));
		assertTrue(notices.first(2, 0));
		assertFalse(notices.first(2, 0));
		assertTrue(notices.first(0, 1));
		assertFalse(notices.first(0, 1));
		assertTrue(notices.first(1, 1));
	}

	@Test
	void aStepAfterAGapPassesAgain() {
		final OncePerStep notices = new OncePerStep(1);
		assertTrue(notices.first(0, 7));
		assertTrue(notices.first(0, 40));
		assertFalse(notices.first(0, 40));
		// a loaded game may go on from an earlier step
		assertTrue(notices.first(0, 3));
	}
}
