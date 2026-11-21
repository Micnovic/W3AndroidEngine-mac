package com.etheller.warsmash.viewer5.handlers.w3x.simulation.item;

import java.util.Locale;

/** The tag of an item (the item data's tag column); the ids are the numbers of ITEMTAG_TYPE_* in common.j. */
public enum CItemTag {
	UNDEFINED,
	DROPPABLE,
	QUEST_REWARD,
	BOSS_DROP,
	SECRET,
	PUZZLE,
	WORLD,
	SHOP,
	ANY;

	public static final CItemTag[] VALUES = values();

	public static CItemTag fromId(final int id) {
		return ((id >= 0) && (id < VALUES.length)) ? VALUES[id] : UNDEFINED;
	}

	/** The data writes "Boss Drop" where the constant is BOSS_DROP. */
	public static CItemTag fromName(final String name) {
		if (name != null) {
			final String key = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
			for (final CItemTag tag : VALUES) {
				if (tag.name().equals(key)) {
					return tag;
				}
			}
		}
		return UNDEFINED;
	}
}
