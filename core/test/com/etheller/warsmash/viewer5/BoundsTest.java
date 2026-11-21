package com.etheller.warsmash.viewer5;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The extent of a model that has not been read yet. */
class BoundsTest {
	@Test
	void anUnreadModelHasNoHeight() {
		assertEquals(0f, new Bounds().getEstimatedMaxZ());
	}

	@Test
	void aReadModelHasItsTopAsHeight() {
		final Bounds bounds = new Bounds();
		bounds.fromExtents(new float[] { -1, -2, -3 }, new float[] { 4, 5, 60 }, 10f);
		assertEquals(60f, bounds.getEstimatedMaxZ());
	}
}
