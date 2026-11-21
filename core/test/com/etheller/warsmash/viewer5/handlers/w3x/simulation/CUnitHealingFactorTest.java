package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.testing.TestObjects;

class CUnitHealingFactorTest {
	private static CUnit unit() {
		final CUnit unit = TestObjects.allocate(CUnit.class);
		TestObjects.set(unit, "healingReceivedFactors", new ArrayList<Float>());
		TestObjects.set(unit, "healingReceivedFactor", 1f);
		return unit;
	}

	private static float factor(final CUnit unit) {
		return (Float) TestObjects.get(unit, "healingReceivedFactor");
	}

	@Test
	void zeroFactorLeavesNoInfinityOrNaN() {
		final CUnit unit = unit();
		unit.addHealingReceivedFactor(0f);
		assertEquals(0f, factor(unit));
		unit.addHealingReceivedFactor(0.5f);
		unit.removeHealingReceivedFactor(0f);
		assertEquals(0.5f, factor(unit));
		unit.removeHealingReceivedFactor(0.5f);
		assertEquals(1f, factor(unit));
		assertFalse(Float.isNaN(factor(unit)) || Float.isInfinite(factor(unit)));
	}

	@Test
	void addRemovePairsDoNotDrift() {
		final CUnit unit = unit();
		for (int i = 0; i < 1000; i++) {
			unit.addHealingReceivedFactor(0.3f);
			unit.addHealingReceivedFactor(1.7f);
			unit.removeHealingReceivedFactor(0.3f);
			unit.removeHealingReceivedFactor(1.7f);
		}
		assertEquals(1f, factor(unit));
	}
}
