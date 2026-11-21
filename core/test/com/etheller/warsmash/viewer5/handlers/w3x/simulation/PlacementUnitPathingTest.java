package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.PathingFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;

/**
 * The building ghost colours each cell of the footprint with the units standing on that cell; the build order checks
 * the whole footprint at once. A footprint is refused for a unit exactly when some cell of the ghost shows it, and only
 * the cells the unit's collision reaches are shown blocked.
 */
class PlacementUnitPathingTest {
	private static final int CELLS = 8;

	private static CUnit unit(final float x, final float y, final float collisionSize) {
		final CUnitType type = TestObjects.allocate(CUnitType.class);
		TestObjects.set(type, "collisionSize", collisionSize);
		TestObjects.set(type, "movementType", MovementType.FOOT);
		final CUnit unit = TestObjects.allocate(CUnit.class);
		TestObjects.set(unit, "unitType", type);
		TestObjects.set(unit, "placement", new CUnitPlacementState());
		TestObjects.set(unit, "death", new CUnitDeathState());
		TestObjects.set(unit, "movement", new CUnitMovementState());
		TestObjects.set(unit, "moveBehavior", TestObjects.allocate(CBehaviorMove.class));
		unit.setX(x);
		unit.setY(y);
		return unit;
	}

	private static short footprint(final CWorldCollision collision, final float minX, final float minY) {
		return PathingGrid.unitPlacementPathing(new Rectangle(minX, minY, CELLS * 32, CELLS * 32), collision, null,
				null, -1);
	}

	private static short[] cells(final CWorldCollision collision, final float minX, final float minY) {
		final short[] cells = new short[CELLS * CELLS];
		for (int j = 0; j < CELLS; j++) {
			for (int i = 0; i < CELLS; i++) {
				cells[(j * CELLS) + i] = PathingGrid.unitPlacementPathing(
						new Rectangle(minX + (i * 32), minY + (j * 32), 32, 32), collision, null, null, -1);
			}
		}
		return cells;
	}

	private static short union(final short[] cells) {
		short union = 0;
		for (final short cell : cells) {
			union |= cell;
		}
		return union;
	}

	@Test
	void cellsOfTheGhostAddUpToTheFootprintCheck() {
		final CWorldCollision collision = new CWorldCollision(new Rectangle(0, 0, 2048, 2048), 64f);
		collision.addUnit(unit(700, 700, 16f));
		collision.addUnit(unit(1500, 300, 48f));
		for (float minX = 384; minX <= 1536; minX += 32) {
			for (float minY = 128; minY <= 768; minY += 32) {
				assertEquals(footprint(collision, minX, minY), union(cells(collision, minX, minY)),
						"footprint at " + minX + ", " + minY);
			}
		}
	}

	@Test
	void onlyTheCellsUnderTheUnitAreBlocked() {
		final CWorldCollision collision = new CWorldCollision(new Rectangle(0, 0, 2048, 2048), 64f);
		// A Footman's square of 32 around (700, 700) lies in the cells from 672 to 736 on both axes.
		collision.addUnit(unit(700, 700, 16f));
		final short[] cells = cells(collision, 576, 576);
		int blocked = 0;
		for (int j = 0; j < CELLS; j++) {
			for (int i = 0; i < CELLS; i++) {
				final short cell = cells[(j * CELLS) + i];
				final boolean expected = ((i == 3) || (i == 4)) && ((j == 3) || (j == 4));
				assertEquals(expected, cell != 0, "cell " + i + ", " + j);
				if (cell != 0) {
					assertTrue(PathingFlags.isPathingFlag(cell, PathingFlags.UNBUILDABLE));
					blocked++;
				}
			}
		}
		assertEquals(4, blocked);
		assertEquals(0, footprint(collision, 1024, 1024), "no unit, nothing blocked");
	}
}
