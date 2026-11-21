package com.etheller.warsmash.viewer5.handlers.w3x.simulation.item;

import java.util.Locale;

/**
 * The equipment type of an item (the item data's equipment column) and the loadout slots a unit has for it. The ids
 * are the numbers common.j gives to EQUIPMENT_TYPE_* and EQUIPMENT_LOADOUT_SLOT_*.
 */
public enum CEquipmentType {
	NONE(),
	HEAD(0),
	CHEST(1),
	GLOVES(2),
	BOOTS(3),
	RING(4, 5),
	PRIMARY(6),
	OFFHAND(7),
	TRINKET(8),
	ANY();

	public static final int LOADOUT_SLOTS = 9;
	public static final CEquipmentType[] VALUES = values();

	private final int[] loadoutSlots;

	CEquipmentType(final int... loadoutSlots) {
		this.loadoutSlots = loadoutSlots;
	}

	/** The loadout slots an item of this type can take, in the order they are tried. */
	public int[] getLoadoutSlots() {
		return this.loadoutSlots.clone();
	}

	public boolean fitsSlot(final int slot) {
		for (final int candidate : this.loadoutSlots) {
			if (candidate == slot) {
				return true;
			}
		}
		return false;
	}

	/** Whether items of this type can be worn at all: ANY and NONE only describe filters and plain items. */
	public boolean isWearable() {
		return this.loadoutSlots.length > 0;
	}

	public static CEquipmentType fromId(final int id) {
		return ((id >= 0) && (id < VALUES.length)) ? VALUES[id] : NONE;
	}

	/** The type an item data cell names; anything unknown is a plain item. */
	public static CEquipmentType fromName(final String name) {
		if (name != null) {
			final String key = name.trim().toUpperCase(Locale.ROOT);
			for (final CEquipmentType type : VALUES) {
				if (type.name().equals(key)) {
					return type;
				}
			}
		}
		return NONE;
	}
}
