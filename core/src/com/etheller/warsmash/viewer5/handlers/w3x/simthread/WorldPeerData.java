package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/**
 * What the render side builds the peer of a new unit, item or destructable from: the values the simulation had at the
 * moment it made the object, copied, so building the peer later (on the render thread, from the creation event) needs
 * no read of the simulation. Each object also names the simulation object the peer is keyed by; the render side must
 * only hand it on as a key, never read it.
 */
public final class WorldPeerData {
	private WorldPeerData() {
	}

	/** Level value of {@link Unit#creepLevel} for a unit that is not one of the creeps the minimap starts from. */
	public static final int NO_CREEP = -1;

	public static final class Unit {
		public final CUnit unit;
		public final War3ID typeId;
		/** Where the simulation put the unit, which can differ from the position asked for. */
		public final float x;
		public final float y;
		public final float requestedX;
		public final float requestedY;
		public final float flyHeight;
		public final int playerIndex;
		/** -1 for the type's or the player's colour. */
		public final int customTeamColor;
		public final int playerColor;
		/** Level of a creep made in the first tick, for the minimap's camps; {@link #NO_CREEP} otherwise. */
		public final int creepLevel;

		public Unit(final CUnit unit, final War3ID typeId, final float x, final float y, final float requestedX,
				final float requestedY, final float flyHeight, final int playerIndex, final int customTeamColor,
				final int playerColor, final int creepLevel) {
			this.unit = unit;
			this.typeId = typeId;
			this.x = x;
			this.y = y;
			this.requestedX = requestedX;
			this.requestedY = requestedY;
			this.flyHeight = flyHeight;
			this.playerIndex = playerIndex;
			this.customTeamColor = customTeamColor;
			this.playerColor = playerColor;
			this.creepLevel = creepLevel;
		}
	}

	/** What a unit's peer needs to change to another type: where the unit is and the colour of its owner. */
	public static final class TypeChange {
		public final int playerColor;
		public final float x;
		public final float y;
		public final float flyHeight;

		public TypeChange(final int playerColor, final float x, final float y, final float flyHeight) {
			this.playerColor = playerColor;
			this.x = x;
			this.y = y;
			this.flyHeight = flyHeight;
		}
	}

	/**
	 * What the art of a projectile that was just launched needs from its source and from the missile itself, copied
	 * when it was launched: the missile may be over before the art is made.
	 */
	public static final class ProjectileLaunch {
		public final float x;
		public final float y;
		public final float flyHeight;
		/** The unit type's launch height. */
		public final float typeLaunchZ;
		/** Colour of the source's owner, for when the source's peer is gone. */
		public final int playerColor;
		/** The number the frames of the game give the missile; 0 when the missile has none. */
		public final int projectileId;
		public final float targetX;
		public final float targetY;
		/** Fly height and impact height of the widget the missile chases (0 for a point). */
		public final float targetFlyHeight;
		public final float targetImpactZ;
		/** The step the missile was launched in. */
		public final int startTick;

		public ProjectileLaunch(final float x, final float y, final float flyHeight, final float typeLaunchZ,
				final int playerColor, final int projectileId, final float targetX, final float targetY,
				final float targetFlyHeight, final float targetImpactZ, final int startTick) {
			this.x = x;
			this.y = y;
			this.flyHeight = flyHeight;
			this.typeLaunchZ = typeLaunchZ;
			this.playerColor = playerColor;
			this.projectileId = projectileId;
			this.targetX = targetX;
			this.targetY = targetY;
			this.targetFlyHeight = targetFlyHeight;
			this.targetImpactZ = targetImpactZ;
			this.startTick = startTick;
		}
	}

	/** What the effect of an instant attack needs from its source and target. */
	public static final class InstantAttack {
		public final String missileArt;
		/** Where the attack leaves the source, already turned by its facing. */
		public final float sourceX;
		public final float sourceY;
		public final float targetX;
		public final float targetY;
		public final float targetFlyHeight;
		public final float targetImpactZ;
		public final int sourcePlayerIndex;

		public InstantAttack(final String missileArt, final float sourceX, final float sourceY, final float targetX,
				final float targetY, final float targetFlyHeight, final float targetImpactZ,
				final int sourcePlayerIndex) {
			this.missileArt = missileArt;
			this.sourceX = sourceX;
			this.sourceY = sourceY;
			this.targetX = targetX;
			this.targetY = targetY;
			this.targetFlyHeight = targetFlyHeight;
			this.targetImpactZ = targetImpactZ;
			this.sourcePlayerIndex = sourcePlayerIndex;
		}
	}

	public static final class Item {
		public final CItem item;
		public final War3ID typeId;
		public final float x;
		public final float y;
		public final float angleRadians;

		public Item(final CItem item, final War3ID typeId, final float x, final float y, final float angleRadians) {
			this.item = item;
			this.typeId = typeId;
			this.x = x;
			this.y = y;
			this.angleRadians = angleRadians;
		}
	}

	public static final class Destructable {
		public final CDestructable destructable;
		public final War3ID typeId;
		/** Position after the run time snapping. */
		public final float x;
		public final float y;
		/** NaN when the render side is to find the height itself. */
		public final float z;
		public final float facingDegrees;
		public final float scale;
		public final int variation;

		public Destructable(final CDestructable destructable, final War3ID typeId, final float x, final float y,
				final float z, final float facingDegrees, final float scale, final int variation) {
			this.destructable = destructable;
			this.typeId = typeId;
			this.x = x;
			this.y = y;
			this.z = z;
			this.facingDegrees = facingDegrees;
			this.scale = scale;
			this.variation = variation;
		}
	}
}
