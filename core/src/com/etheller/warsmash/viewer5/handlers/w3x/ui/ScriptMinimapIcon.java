package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/**
 * An icon a map script put on the minimap (CreateMinimapIcon*): fixed at a point or following a unit, drawn with the
 * texture of its path tinted by the colour, and only shown where the local player's fog of war is one of the states
 * of its fog mask.
 */
public final class ScriptMinimapIcon {
	private final float fixedX;
	private final float fixedY;
	private final CUnit unit;
	private final String path;
	private final float red;
	private final float green;
	private final float blue;
	private final int fogMask;
	private boolean visible = true;
	private boolean orphanDestroy;

	public ScriptMinimapIcon(final float x, final float y, final CUnit unit, final int red, final int green,
			final int blue, final String path, final int fogMask) {
		this.fixedX = x;
		this.fixedY = y;
		this.unit = unit;
		this.red = (red & 0xFF) / 255f;
		this.green = (green & 0xFF) / 255f;
		this.blue = (blue & 0xFF) / 255f;
		this.path = path;
		this.fogMask = fogMask;
	}

	/** Icons on a unit follow it, and stay where it was once it is dead. */
	public float getX() {
		return this.unit == null ? this.fixedX : this.unit.getX();
	}

	public float getY() {
		return this.unit == null ? this.fixedY : this.unit.getY();
	}

	public CUnit getUnit() {
		return this.unit;
	}

	public String getPath() {
		return this.path;
	}

	public float getRed() {
		return this.red;
	}

	public float getGreen() {
		return this.green;
	}

	public float getBlue() {
		return this.blue;
	}

	public int getFogMask() {
		return this.fogMask;
	}

	public boolean isVisible() {
		return this.visible;
	}

	public void setVisible(final boolean visible) {
		this.visible = visible;
	}

	public boolean isOrphanDestroy() {
		return this.orphanDestroy;
	}

	public void setOrphanDestroy(final boolean orphanDestroy) {
		this.orphanDestroy = orphanDestroy;
	}

	/** An icon that is to go with its unit has lost it once the unit is dead or its handle is gone. */
	public boolean isOrphaned() {
		return this.orphanDestroy && (this.unit != null) && (this.unit.isDead() || this.unit.isRemovedFromGame());
	}
}
