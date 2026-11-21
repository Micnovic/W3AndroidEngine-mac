package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABBuffStun;

class CUnitBuffRemoveTest {
	@Test
	@SuppressWarnings("unchecked")
	void removeTakesTheSameInstanceNotAnEqualOne() {
		final CUnit unit = TestObjects.allocate(CUnit.class);
		final List<CAbility> abilities = new ArrayList<>();
		TestObjects.set(unit, "abilities", abilities);
		final CBuff first = TestObjects.allocate(ABBuffStun.class);
		final CBuff second = TestObjects.allocate(ABBuffStun.class);
		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
		abilities.add((CAbility) first);
		abilities.add((CAbility) second);
		try {
			unit.remove(null, second);
		}
		catch (final RuntimeException expectedWithoutSimulation) {
			// the list is already updated by the time the unit notifies the simulation
		}
		assertEquals(1, abilities.size());
		assertSame(first, abilities.get(0));
	}
}
