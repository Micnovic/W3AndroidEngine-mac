package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.etheller.warsmash.viewer5.handlers.w3x.simthread.UnitFrameState;
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
	private final int unitHandle;
	private boolean visible = true;
	private boolean orphanDestroy;
	// Where the unit was in the frames of the game, and whether any frame had it: a unit the frames no longer
	// have is gone from the game.
	private float followedX;
	private float followedY;
	private boolean seenInFrames;

	public ScriptMinimapIcon(final float x, final float y, final CUnit unit, final int red, final int green,
			final int blue, final String path, final int fogMask) {
		this.fixedX = x;
		this.fixedY = y;
		this.unit = unit;
		this.unitHandle = unit == null ? 0 : unit.getHandleId();
		this.followedX = x;
		this.followedY = y;
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

	/**
	 * Takes the unit's place from the frame being shown, where the game is shown from frames; true once the icon is to
	 * go with its unit and the unit is dead or gone. A unit that no frame has had yet leaves the icon where it was.
	 */
	public boolean follow(final UnitFrameState frameUnits) {
		if (this.unit == null) {
			return false;
		}
		final int row = frameUnits.indexOf(this.unitHandle);
		if (row < 0) {
			return this.orphanDestroy && this.seenInFrames;
		}
		this.seenInFrames = true;
		this.followedX = frameUnits.x[row];
		this.followedY = frameUnits.y[row];
		return this.orphanDestroy && ((frameUnits.flags[row] & UnitFrameState.DEAD) != 0);
	}

	public float getFollowedX() {
		return this.followedX;
	}

	public float getFollowedY() {
		return this.followedY;
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
