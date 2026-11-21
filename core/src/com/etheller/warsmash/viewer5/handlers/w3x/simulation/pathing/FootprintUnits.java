package com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWorldCollision;

/**
 * The units a footprint test of a search can meet, gathered from the collision trees once for each square of the
 * map the search reaches and kept in arrays: the same answer as CWorldCollision.intersectsFootprintOtherThan, without
 * a walk of the trees for each of the thousands of points of a search. Valid for one search in one tick (units do
 * not move within it); anything that changed the units since, or a different kind of footprint, starts it afresh.
 */
final class FootprintUnits {
	private static final float BUCKET = 256f;
	/** More than the float rounding of the test area can move its edge. */
	private static final float MARGIN = 2f;
	private static final boolean CHECK = Boolean.getBoolean("warsmash.footprintUnitsCheck");

	private static final class Bucket {
		private int stamp;
		private int count;
		private CUnit[] units = new CUnit[8];
		/** The square of each unit (x, y, width, height), then its first footprint cell and cell count (x, y, cells). */
		private float[] squares = new float[32];
		private int[] cells = new int[24];
	}

	private final List<CUnit> found = new ArrayList<>();
	private final Rectangle gatherArea = new Rectangle();
	private Bucket[] buckets = new Bucket[0];
	private int columns;
	private int rows;
	private float originX;
	private float originY;
	private PathingGrid grid;
	private CWorldCollision collision;
	private int stamp;
	private int epoch = -1;
	private int tick;
	private int footprintCells;
	private MovementType movementType;
	private int unitChanges;
	private float reach;

	void prepare(final PathingGrid pathingGrid, final CWorldCollision worldCollision, final int searchEpoch,
			final int gameTurnTick, final int cells, final MovementType type) {
		final int changes = worldCollision.getUnitChanges();
		if ((pathingGrid == this.grid) && (worldCollision == this.collision) && (searchEpoch == this.epoch)
				&& (gameTurnTick == this.tick) && (cells == this.footprintCells) && (type == this.movementType)
				&& (changes == this.unitChanges)) {
			return;
		}
		if ((pathingGrid != this.grid) || (this.buckets.length == 0)) {
			this.columns = ((pathingGrid.getWidth() * 32) / (int) BUCKET) + 2;
			this.rows = ((pathingGrid.getHeight() * 32) / (int) BUCKET) + 2;
			this.buckets = new Bucket[this.columns * this.rows];
			this.originX = pathingGrid.getWorldX(0) - 16f;
			this.originY = pathingGrid.getWorldY(0) - 16f;
			this.stamp = 0;
		}
		this.grid = pathingGrid;
		this.collision = worldCollision;
		this.epoch = searchEpoch;
		this.tick = gameTurnTick;
		this.footprintCells = cells;
		this.movementType = type;
		this.unitChanges = changes;
		this.reach = worldCollision.footprintCandidateReach(cells);
		this.stamp++;
	}

	/** Whether a unit other than the two given stands where a footprint at the point would be blocked by it. */
	boolean intersects(final float x, final float y, final CUnit firstToIgnore, final CUnit secondToIgnore) {
		final boolean answer = lookUp(x, y, firstToIgnore, secondToIgnore);
		if (CHECK) {
			final boolean expected = this.collision.intersectsFootprintOtherThan(this.grid, x, y, this.footprintCells,
					firstToIgnore, secondToIgnore, this.movementType, this.tick);
			if (expected != answer) {
				throw new IllegalStateException("footprint units differ at " + x + ", " + y + ": " + answer);
			}
		}
		return answer;
	}

	private boolean lookUp(final float x, final float y, final CUnit firstToIgnore, final CUnit secondToIgnore) {
		final float column = (x - this.originX) / BUCKET;
		final float row = (y - this.originY) / BUCKET;
		if (!(column >= 0f) || !(row >= 0f) || (column >= this.columns) || (row >= this.rows)) {
			return this.collision.intersectsFootprintOtherThan(this.grid, x, y, this.footprintCells, firstToIgnore,
					secondToIgnore, this.movementType, this.tick);
		}
		final int bucketX = (int) column;
		final int bucketY = (int) row;
		final int slot = (bucketY * this.columns) + bucketX;
		Bucket bucket = this.buckets[slot];
		if (bucket == null) {
			bucket = new Bucket();
			this.buckets[slot] = bucket;
		}
		if (bucket.stamp != this.stamp) {
			fill(bucket, bucketX, bucketY);
		}
		final int cells = this.footprintCells;
		final int firstX = this.grid.getFootprintCellX(x, cells);
		final int firstY = this.grid.getFootprintCellY(y, cells);
		final float areaX = x - this.reach;
		final float areaY = y - this.reach;
		final float areaSize = this.reach * 2;
		final float[] squares = bucket.squares;
		final int[] others = bucket.cells;
		final CUnit[] units = bucket.units;
		for (int i = 0; i < bucket.count; i++) {
			final int at = i * 4;
			final float squareX = squares[at];
			final float squareY = squares[at + 1];
			if ((squareX < (areaX + areaSize)) && ((squareX + squares[at + 2]) > areaX)
					&& (squareY < (areaY + areaSize)) && ((squareY + squares[at + 3]) > areaY)) {
				final int otherAt = i * 3;
				final int otherX = others[otherAt];
				final int otherY = others[otherAt + 1];
				final int otherCells = others[otherAt + 2];
				if ((otherX < (firstX + cells)) && (firstX < (otherX + otherCells)) && (otherY < (firstY + cells))
						&& (firstY < (otherY + otherCells)) && (units[i] != firstToIgnore)
						&& (units[i] != secondToIgnore)) {
					return true;
				}
			}
		}
		return false;
	}

	private void fill(final Bucket bucket, final int bucketX, final int bucketY) {
		final float span = this.reach + MARGIN;
		this.gatherArea.set((this.originX + (bucketX * BUCKET)) - span, (this.originY + (bucketY * BUCKET)) - span,
				BUCKET + (span * 2), BUCKET + (span * 2));
		this.found.clear();
		this.collision.collectFootprintCandidates(this.gatherArea,
				this.movementType == MovementType.AMPHIBIOUS, this.tick, this.found);
		bucket.stamp = this.stamp;
		bucket.count = 0;
		for (int i = 0; i < this.found.size(); i++) {
			final CUnit unit = this.found.get(i);
			final float collisionSize = unit.getUnitType().getCollisionSize();
			if (collisionSize <= 0f) {
				continue;
			}
			boolean seen = false;
			for (int j = 0; j < bucket.count; j++) {
				if (bucket.units[j] == unit) {
					seen = true;
					break;
				}
			}
			if (seen) {
				continue;
			}
			final int n = bucket.count++;
			if (n == bucket.units.length) {
				bucket.units = java.util.Arrays.copyOf(bucket.units, n * 2);
				bucket.squares = java.util.Arrays.copyOf(bucket.squares, n * 8);
				bucket.cells = java.util.Arrays.copyOf(bucket.cells, n * 6);
			}
			final Rectangle square = unit.getCollisionRectangle();
			final int cells = PathingGrid.getFootprintCells(collisionSize);
			bucket.units[n] = unit;
			bucket.squares[n * 4] = square.x;
			bucket.squares[(n * 4) + 1] = square.y;
			bucket.squares[(n * 4) + 2] = square.width;
			bucket.squares[(n * 4) + 3] = square.height;
			bucket.cells[n * 3] = this.grid.getFootprintCellX(unit.getX(), cells);
			bucket.cells[(n * 3) + 1] = this.grid.getFootprintCellY(unit.getY(), cells);
			bucket.cells[(n * 3) + 2] = cells;
		}
		this.found.clear();
	}
}
