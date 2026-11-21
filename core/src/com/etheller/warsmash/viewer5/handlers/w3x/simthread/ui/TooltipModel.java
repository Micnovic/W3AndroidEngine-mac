package com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui;

/**
 * The texts and costs a tooltip shows, already resolved by the simulation for the local player's current state.
 * Writer: the simulation thread while the frame is built. Reader: the render thread. Strings are immutable, so a copy
 * is a reference copy.
 */
public final class TooltipModel {
	public String title;
	public String body;
	/** Extra line (requirements, "Level 2 of 3", upkeep note), may be null. */
	public String extra;
	public int goldCost;
	public int lumberCost;
	public int foodCost;
	public int manaCost;
	public float cooldown;
	public int hotkey;

	public void clear() {
		title = null;
		body = null;
		extra = null;
		goldCost = 0;
		lumberCost = 0;
		foodCost = 0;
		manaCost = 0;
		cooldown = 0;
		hotkey = 0;
	}

	public void copyFrom(final TooltipModel other) {
		title = other.title;
		body = other.body;
		extra = other.extra;
		goldCost = other.goldCost;
		lumberCost = other.lumberCost;
		foodCost = other.foodCost;
		manaCost = other.manaCost;
		cooldown = other.cooldown;
		hotkey = other.hotkey;
	}
}
