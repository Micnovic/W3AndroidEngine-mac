package com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.parsers.w3x.wpm.War3MapWpm;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;

/** Cells beyond the map edges are outside, not the neighbouring row or column, and read as the map boundary. */
class PathingGridEdgeTest {
	private static final int SIZE = 8;

	private static PathingGrid grid(final float offset) throws IOException {
		final War3MapWpm wpm = new War3MapWpm(null);
		wpm.getSize()[0] = SIZE;
		wpm.getSize()[1] = SIZE;
		wpm.setPathing(new short[SIZE * SIZE]);
		return new PathingGrid(wpm, new float[] { offset, offset });
	}

	@Test
	void cellsFloorBelowTheLowEdge() throws IOException {
		final PathingGrid grid = grid(-128f);
		assertEquals(0, grid.getCellX(-128f));
		assertEquals(0, grid.getCellX(-96.5f));
		assertEquals(-1, grid.getCellX(-128.5f));
		assertEquals(-1, grid.getCellY(-160f));
		assertEquals(-2, grid.getCellY(-160.5f));
		assertEquals(SIZE - 1, grid.getCellX(-128f + (SIZE * 32f) - 0.5f));
		assertEquals(SIZE, grid.getCellX(-128f + (SIZE * 32f)));
	}

	@Test
	void outsideCellsDoNotWrapToTheNeighbouringRow() throws IOException {
		final PathingGrid grid = grid(0f);
		grid.setCellPathing(SIZE - 1, 0, PathingGrid.PathingFlags.UNWALKABLE);
		grid.setCellPathing(0, 1, PathingGrid.PathingFlags.UNBUILDABLE);
		// x = -1 on row 1 is the last cell of row 0; x = SIZE on row 0 is the first cell of row 1
		assertFalse(grid.isCellPathable(-1, 1, MovementType.FOOT));
		assertFalse(grid.isCellPathable(SIZE, 0, MovementType.FOOT));
		assertFalse(grid.isPathable(-10f, 40f, MovementType.FOOT));
		assertFalse(grid.isPathable(SIZE * 32f + 5f, 10f, MovementType.FLY));
		assertFalse(grid.isPathable(100f, -1f, MovementType.FOOT));
		assertFalse(grid.isPathable(100f, SIZE * 32f, MovementType.FLY));
		assertTrue(grid.isPathable(0f, 0f, MovementType.FOOT));
		assertTrue(grid.isPathable((SIZE * 32f) - 1f, (SIZE * 32f) - 1f, MovementType.FOOT));
		assertEquals(0, grid.getTerrainPathing(5f, 5f));
		assertTrue(grid.getTerrainPathing(-1f, 5f) != 0);
		assertTrue(grid.isBlockVision(-1f, 40f));
	}

	@Test
	void writesOutsideTheMapAreIgnored() throws IOException {
		final PathingGrid grid = grid(0f);
		grid.setCellPathing(-1, 1, PathingGrid.PathingFlags.UNWALKABLE);
		grid.setBlighted(-1f, 40f, true);
		assertEquals(0, grid.getCellPathing(SIZE - 1, 0));
		assertEquals(0, grid.getCellPathing(SIZE - 1, 1));
	}
}
