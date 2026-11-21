package com.etheller.warsmash.viewer5.handlers.mdx;

import java.nio.CharBuffer;
import java.nio.FloatBuffer;

/**
 * The triangles of a geoset sorted into a grid over the model's ground plane, so that a vertical ray (the height of
 * the ground under a unit or an item) only tests the triangles of the cell it passes instead of every triangle of
 * the model. A triangle is in every cell its bounds, widened by {@link #MARGIN}, touch, in ascending face order; the
 * cell of a point therefore holds every triangle a vertical ray through it can hit.
 */
public final class PickGrid {
	/** How far past a triangle's bounds a ray still counts as passing it, in model units. */
	static final float MARGIN = 0.05f;
	private static final int MAX_CELLS = 1 << 18;
	private static final float TRIANGLES_PER_CELL = 2;

	private final float minX;
	private final float minY;
	private final float invCellX;
	private final float invCellY;
	private final int columns;
	private final int rows;
	private final int[] cellStart;
	private final int[] cellFaces;

	PickGrid(final FloatBuffer vertices, final CharBuffer indices, final int vertexSize) {
		final int triangles = indices.limit() / 3;
		final float[] bounds = new float[triangles * 4];
		float lowX = Float.MAX_VALUE;
		float lowY = Float.MAX_VALUE;
		float highX = -Float.MAX_VALUE;
		float highY = -Float.MAX_VALUE;
		for (int t = 0; t < triangles; t++) {
			final int a = indices.get(t * 3) * vertexSize;
			final int b = indices.get((t * 3) + 1) * vertexSize;
			final int c = indices.get((t * 3) + 2) * vertexSize;
			final float ax = vertices.get(a);
			final float bx = vertices.get(b);
			final float cx = vertices.get(c);
			final float ay = vertices.get(a + 1);
			final float by = vertices.get(b + 1);
			final float cy = vertices.get(c + 1);
			final float x0 = Math.min(ax, Math.min(bx, cx)) - MARGIN;
			final float x1 = Math.max(ax, Math.max(bx, cx)) + MARGIN;
			final float y0 = Math.min(ay, Math.min(by, cy)) - MARGIN;
			final float y1 = Math.max(ay, Math.max(by, cy)) + MARGIN;
			bounds[t * 4] = x0;
			bounds[(t * 4) + 1] = y0;
			bounds[(t * 4) + 2] = x1;
			bounds[(t * 4) + 3] = y1;
			lowX = Math.min(lowX, x0);
			lowY = Math.min(lowY, y0);
			highX = Math.max(highX, x1);
			highY = Math.max(highY, y1);
		}
		if (triangles == 0) {
			lowX = lowY = 0;
			highX = highY = 1;
		}
		final float width = Math.max(highX - lowX, 0.001f);
		final float height = Math.max(highY - lowY, 0.001f);
		final float cells = Math.max(1, Math.min(MAX_CELLS, triangles / TRIANGLES_PER_CELL));
		final float cellSize = (float) Math.sqrt((width * height) / cells);
		this.columns = Math.max(1, Math.min(1024, (int) Math.ceil(width / cellSize)));
		this.rows = Math.max(1, Math.min(1024, (int) Math.ceil(height / cellSize)));
		this.minX = lowX;
		this.minY = lowY;
		this.invCellX = this.columns / width;
		this.invCellY = this.rows / height;

		final int[] counts = new int[(this.columns * this.rows) + 1];
		for (int t = 0; t < triangles; t++) {
			final int c0 = column(bounds[t * 4]);
			final int c1 = column(bounds[(t * 4) + 2]);
			final int r0 = row(bounds[(t * 4) + 1]);
			final int r1 = row(bounds[(t * 4) + 3]);
			for (int r = r0; r <= r1; r++) {
				for (int c = c0; c <= c1; c++) {
					counts[(r * this.columns) + c + 1]++;
				}
			}
		}
		for (int i = 1; i < counts.length; i++) {
			counts[i] += counts[i - 1];
		}
		this.cellStart = counts;
		this.cellFaces = new int[counts[counts.length - 1]];
		final int[] fill = new int[counts.length - 1];
		for (int t = 0; t < triangles; t++) {
			final int c0 = column(bounds[t * 4]);
			final int c1 = column(bounds[(t * 4) + 2]);
			final int r0 = row(bounds[(t * 4) + 1]);
			final int r1 = row(bounds[(t * 4) + 3]);
			for (int r = r0; r <= r1; r++) {
				for (int c = c0; c <= c1; c++) {
					final int cell = (r * this.columns) + c;
					this.cellFaces[this.cellStart[cell] + fill[cell]++] = t * 3;
				}
			}
		}
	}

	private int column(final float x) {
		return Math.max(0, Math.min(this.columns - 1, (int) ((x - this.minX) * this.invCellX)));
	}

	private int row(final float y) {
		return Math.max(0, Math.min(this.rows - 1, (int) ((y - this.minY) * this.invCellY)));
	}

	/** The first cell the point is in, or -1 when it is outside every triangle's bounds. */
	public int cellAt(final float x, final float y) {
		if ((x < this.minX) || (y < this.minY) || (x > (this.minX + (this.columns / this.invCellX)))
				|| (y > (this.minY + (this.rows / this.invCellY)))) {
			return -1;
		}
		return (row(y) * this.columns) + column(x);
	}

	public int start(final int cell) {
		return this.cellStart[cell];
	}

	public int end(final int cell) {
		return this.cellStart[cell + 1];
	}

	/** The first corners of the triangles of all cells, a cell's from {@link #start} to {@link #end}. */
	public int[] faces() {
		return this.cellFaces;
	}
}
