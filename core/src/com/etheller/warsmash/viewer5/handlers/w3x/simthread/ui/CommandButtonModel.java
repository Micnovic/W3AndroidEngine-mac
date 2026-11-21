package com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui;

import com.badlogic.gdx.graphics.Texture;

/**
 * One button of the command card, fully computed by the simulation (what the card's logic would otherwise read from
 * the live ability objects). Writer: the simulation thread while the frame is built. Reader: the render thread (card
 * drawing, hotkeys, click pre-checks). Plain data, reused from frame to frame.
 */
public final class CommandButtonModel {
	/** Why a button cannot be used; {@link #NONE} when it can. */
	public static final int NONE = 0;
	public static final int NOT_ENOUGH_GOLD = 1;
	public static final int NOT_ENOUGH_LUMBER = 2;
	public static final int NOT_ENOUGH_MANA = 3;
	public static final int NOT_ENOUGH_FOOD = 4;
	public static final int REQUIREMENTS = 5;
	public static final int COOLDOWN = 6;
	public static final int DISABLED_OTHER = 7;

	/** Kinds of what the button does. */
	public static final int KIND_ORDER = 0;
	public static final int KIND_ABILITY = 1;
	public static final int KIND_BUILD = 2;
	public static final int KIND_TRAIN = 3;
	public static final int KIND_RESEARCH = 4;
	public static final int KIND_MENU = 5;
	public static final int KIND_ITEM = 6;

	/** Card position, column + 4 * row, or -1 for an empty slot. */
	public int slot = -1;
	public int kind;
	/** Order id, ability id or unit / upgrade type id, depending on {@link #kind}. */
	public int id;
	/** Handle of the ability that owns the button, 0 for the cancel button of a menu. */
	public int abilityHandleId;
	/** Alias (raw code) of that ability, 0 when it has none; what a script's button effect names. */
	public int abilityAlias;
	/** Order id of the auto cast toggle, 0 when the button has none. */
	public int autoCastOrderId;
	public String iconPath;
	/**
	 * Render-side textures of a script-defined button, which has no icon path to name; null for every other button.
	 * Only handed on, never drawn by the simulation.
	 */
	public Texture iconTexture;
	public Texture iconTextureDisabled;
	public boolean enabled;
	public int disabledReason;
	public boolean autocastable;
	public boolean autocastOn;
	public boolean toggledOn;
	public float cooldownRemaining;
	public float cooldownTotal;
	/** Charges left, -1 when the button has none. */
	public int charges = -1;
	public int goldCost;
	public int lumberCost;
	public int foodCost;
	public int manaCost;
	/** Hotkey as a key code, 0 when none. */
	public int hotkey;
	/** Tooltip source; the model is on the card so no extra lookup is needed on hover. */
	public final TooltipModel tooltip = new TooltipModel();

	public void clear() {
		slot = -1;
		kind = 0;
		id = 0;
		abilityHandleId = 0;
		abilityAlias = 0;
		autoCastOrderId = 0;
		iconPath = null;
		iconTexture = null;
		iconTextureDisabled = null;
		enabled = false;
		disabledReason = NONE;
		autocastable = false;
		autocastOn = false;
		toggledOn = false;
		cooldownRemaining = 0;
		cooldownTotal = 0;
		charges = -1;
		goldCost = 0;
		lumberCost = 0;
		foodCost = 0;
		manaCost = 0;
		hotkey = 0;
		tooltip.clear();
	}

	public void copyFrom(final CommandButtonModel other) {
		slot = other.slot;
		kind = other.kind;
		id = other.id;
		abilityHandleId = other.abilityHandleId;
		abilityAlias = other.abilityAlias;
		autoCastOrderId = other.autoCastOrderId;
		iconPath = other.iconPath;
		iconTexture = other.iconTexture;
		iconTextureDisabled = other.iconTextureDisabled;
		enabled = other.enabled;
		disabledReason = other.disabledReason;
		autocastable = other.autocastable;
		autocastOn = other.autocastOn;
		toggledOn = other.toggledOn;
		cooldownRemaining = other.cooldownRemaining;
		cooldownTotal = other.cooldownTotal;
		charges = other.charges;
		goldCost = other.goldCost;
		lumberCost = other.lumberCost;
		foodCost = other.foodCost;
		manaCost = other.manaCost;
		hotkey = other.hotkey;
		tooltip.copyFrom(other.tooltip);
	}
}
