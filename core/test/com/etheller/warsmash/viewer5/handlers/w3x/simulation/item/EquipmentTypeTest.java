package com.etheller.warsmash.viewer5.handlers.w3x.simulation.item;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The 3.0 item data names and the numbers common.j gives them. */
class EquipmentTypeTest {
	@Test
	void dataNamesGiveTheConstantsOfCommonJ() {
		assertEquals(0, CEquipmentType.fromName("None").ordinal());
		assertEquals(1, CEquipmentType.fromName("Head").ordinal());
		assertEquals(5, CEquipmentType.fromName("Ring").ordinal());
		assertEquals(6, CEquipmentType.fromName(" Primary ").ordinal());
		assertEquals(8, CEquipmentType.fromName("Trinket").ordinal());
		assertEquals(CEquipmentType.NONE, CEquipmentType.fromName("Wings"));
		assertEquals(CEquipmentType.NONE, CEquipmentType.fromName(null));
		assertEquals(CEquipmentType.ANY, CEquipmentType.fromId(9));
		assertEquals(CEquipmentType.NONE, CEquipmentType.fromId(-1));
		assertEquals(CItemTag.BOSS_DROP, CItemTag.fromName("Boss Drop"));
		assertEquals(CItemTag.QUEST_REWARD, CItemTag.fromName("Quest Reward"));
		assertEquals(3, CItemTag.fromName("Boss Drop").ordinal());
		assertEquals(CItemTag.UNDEFINED, CItemTag.fromName("Undefined"));
		assertEquals(CItemTag.UNDEFINED, CItemTag.fromId(42));
	}

	@Test
	void loadoutSlotsFollowTheSlotConstants() {
		assertArrayEquals(new int[] { 0 }, CEquipmentType.HEAD.getLoadoutSlots());
		assertArrayEquals(new int[] { 4, 5 }, CEquipmentType.RING.getLoadoutSlots());
		assertArrayEquals(new int[] { 8 }, CEquipmentType.TRINKET.getLoadoutSlots());
		assertTrue(CEquipmentType.RING.fitsSlot(5));
		assertFalse(CEquipmentType.HEAD.fitsSlot(1));
		assertFalse(CEquipmentType.NONE.isWearable());
		assertFalse(CEquipmentType.ANY.isWearable());
		assertTrue(CEquipmentType.OFFHAND.isWearable());
		assertEquals(9, CEquipmentType.LOADOUT_SLOTS);
		for (final CEquipmentType type : CEquipmentType.VALUES) {
			for (final int slot : type.getLoadoutSlots()) {
				assertTrue(slot >= 0 && slot < CEquipmentType.LOADOUT_SLOTS);
			}
		}
	}
}
