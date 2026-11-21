package com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui;

/**
 * What the multi-selection grid shows of one selected unit: its icon and texts (by unit type) and its life and mana.
 * Writer: the simulation thread while the frame is built. Reader: the render thread.
 */
public final class SelectedUnitModel {
	public int unitHandle;
	public int typeId;
	public String iconPath;
	public String tip;
	public String uberTip;
	public float life;
	public float maxLife;
	public float mana;
	public float maxMana;

	public void clear() {
		unitHandle = 0;
		typeId = 0;
		iconPath = null;
		tip = null;
		uberTip = null;
		life = 0;
		maxLife = 0;
		mana = 0;
		maxMana = 0;
	}

	public void copyFrom(final SelectedUnitModel other) {
		unitHandle = other.unitHandle;
		typeId = other.typeId;
		iconPath = other.iconPath;
		tip = other.tip;
		uberTip = other.uberTip;
		life = other.life;
		maxLife = other.maxLife;
		mana = other.mana;
		maxMana = other.maxMana;
	}
}
