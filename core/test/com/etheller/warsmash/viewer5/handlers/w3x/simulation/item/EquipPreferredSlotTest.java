package com.etheller.warsmash.viewer5.handlers.w3x.simulation.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;

/** A campaign unit wears its items in the slots it wore them in, and a refused item is not left to the caller. */
class EquipPreferredSlotTest {
	private static final class Hero extends CUnit {
		private Hero() {
			super(0, 0, 0, 0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0, null);
		}

		@Override
		public void onEquipItem(final com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation game,
				final CItem item) {
		}
	}

	private static final class Worn extends CItem {
		private CEquipmentType type;

		private Worn() {
			super(0, 0, 0, 0, null, null);
		}

		@Override
		public CEquipmentType getEquipmentType() {
			return this.type;
		}

		@Override
		public boolean isDead() {
			return false;
		}

		@Override
		public List<War3ID> getAbilityIds() {
			return Collections.emptyList();
		}
	}

	private static Worn item(final CEquipmentType type) {
		final Worn item = TestObjects.allocate(Worn.class);
		item.type = type;
		return item;
	}

	private static CAbilityInventory inventory() {
		return new CAbilityInventory(1, null, null, true, true, false, false, 6, null);
	}

	@Test
	void theItemGoesToItsOwnSlotWhenTheTypeHasIt() {
		final CAbilityInventory inventory = inventory();
		final CUnit hero = TestObjects.allocate(Hero.class);
		final Worn ring = item(CEquipmentType.RING);
		assertTrue(inventory.equipItem(null, hero, ring, 5));
		assertSame(ring, inventory.getItemInLoadoutSlot(5));
		assertNull(inventory.getItemInLoadoutSlot(4));
	}

	@Test
	void aTakenOrForeignSlotFallsBackToTheFirstFreeOne() {
		final CAbilityInventory inventory = inventory();
		final CUnit hero = TestObjects.allocate(Hero.class);
		assertTrue(inventory.equipItem(null, hero, item(CEquipmentType.RING), 4));
		final Worn second = item(CEquipmentType.RING);
		assertTrue(inventory.equipItem(null, hero, second, 4));
		assertSame(second, inventory.getItemInLoadoutSlot(5));
		final Worn head = item(CEquipmentType.HEAD);
		assertTrue(inventory.equipItem(null, hero, head, 4));
		assertSame(head, inventory.getItemInLoadoutSlot(0));
	}

	@Test
	void anItemWithNoFreeSlotIsRefused() {
		final CAbilityInventory inventory = inventory();
		final CUnit hero = TestObjects.allocate(Hero.class);
		assertTrue(inventory.equipItem(null, hero, item(CEquipmentType.HEAD), 0));
		assertFalse(inventory.equipItem(null, hero, item(CEquipmentType.HEAD), 0));
	}
}
