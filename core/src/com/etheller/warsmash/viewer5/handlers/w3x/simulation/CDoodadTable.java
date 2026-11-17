package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.etheller.warsmash.parsers.w3x.doo.Doodad;
import com.etheller.warsmash.util.War3ID;

/**
 * The doodads of the map as the game rules see them, read from the map's doodad table (war3map.doo) and the terrain
 * data, in file order. Scripts that read doodads get their values from here, never from the drawn doodads, so every
 * player of a game reads the same numbers whatever graphics set is in use.
 */
public final class CDoodadTable {
	private static final int SHOWN_FLAG = 0x2;
	private static final float SAMPLE_RADIUS = 4;

	private final List<Entry> entries;

	private CDoodadTable(final List<Entry> entries) {
		this.entries = entries;
	}

	public static final class Entry {
		public final War3ID typeId;
		public final int variation;
		public final float x;
		public final float y;
		public final float z;
		public final float scaleX;
		public final float scaleY;
		public final float scaleZ;
		public final float yaw;
		public final float pitch;
		public final float roll;

		private Entry(final Doodad doodad, final float maxPitch, final float maxRoll, final CTerrainData terrain) {
			final float[] location = doodad.getLocation();
			final float[] scale = doodad.getScale();
			this.typeId = doodad.getId();
			this.variation = doodad.getVariation();
			this.x = location[0];
			this.y = location[1];
			this.z = location[2];
			this.scaleX = scale[0];
			this.scaleY = scale[1];
			this.scaleZ = scale[2];
			this.yaw = doodad.getAngle();
			this.pitch = tilt(terrain, this.x, this.y, this.yaw, maxPitch);
			this.roll = tilt(terrain, this.x, this.y, this.yaw + (float) (Math.PI / 2), maxRoll);
		}

		private static float tilt(final CTerrainData terrain, final float x, final float y, final float direction,
				final float limit) {
			if (terrain == null) {
				return 0;
			}
			final float dx = SAMPLE_RADIUS * (float) StrictMath.cos(direction);
			final float dy = SAMPLE_RADIUS * (float) StrictMath.sin(direction);
			final float rise = terrain.getGroundZ(x + dx, y + dy) - terrain.getGroundZ(x - dx, y - dy);
			final float angle = (float) StrictMath.atan2(rise, SAMPLE_RADIUS * 2);
			return Math.max(-limit, Math.min(limit, angle));
		}
	}

	/**
	 * @param tilts gives the maximum pitch and roll (radians) of a doodad type, or null when the type is not a
	 *              doodad (a destructable, or unknown), which then is not listed.
	 */
	public static CDoodadTable build(final List<Doodad> doodads, final Function<War3ID, float[]> tilts,
			final CTerrainData terrain) {
		final List<Entry> entries = new ArrayList<>();
		for (final Doodad doodad : doodads) {
			if ((doodad.getFlags() & SHOWN_FLAG) == 0) {
				continue;
			}
			final float[] limits = tilts.apply(doodad.getId());
			if (limits != null) {
				entries.add(new Entry(doodad, limits[0], limits[1], terrain));
			}
		}
		return new CDoodadTable(Collections.unmodifiableList(entries));
	}

	public static CDoodadTable empty() {
		return new CDoodadTable(Collections.emptyList());
	}

	public int size() {
		return this.entries.size();
	}

	/** The doodad at the index of the file order, or null when there is none. */
	public Entry get(final int index) {
		return (index >= 0) && (index < this.entries.size()) ? this.entries.get(index) : null;
	}
}
