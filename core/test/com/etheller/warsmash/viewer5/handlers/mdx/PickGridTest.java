package com.etheller.warsmash.viewer5.handlers.mdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.CharBuffer;
import java.nio.FloatBuffer;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.etheller.warsmash.util.RenderMathUtils;

class PickGridTest {
	@Test
	void verticalRaysFindTheSameGroundAsTestingEveryTriangle() {
		final Random random = new Random(7);
		final int size = 40;
		final float[] vertices = new float[size * size * 3];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				final int v = ((y * size) + x) * 3;
				vertices[v] = (x * 16) + (random.nextFloat() * 6);
				vertices[v + 1] = (y * 16) + (random.nextFloat() * 6);
				vertices[v + 2] = random.nextFloat() * 200;
			}
		}
		final char[] faces = new char[(size - 1) * (size - 1) * 6];
		int f = 0;
		for (int y = 0; y < (size - 1); y++) {
			for (int x = 0; x < (size - 1); x++) {
				final char a = (char) ((y * size) + x);
				final char b = (char) (a + 1);
				final char c = (char) (a + size);
				final char d = (char) (c + 1);
				faces[f++] = a;
				faces[f++] = b;
				faces[f++] = c;
				faces[f++] = b;
				faces[f++] = d;
				faces[f++] = c;
			}
		}
		final FloatBuffer vertexBuffer = FloatBuffer.wrap(vertices);
		final CharBuffer faceBuffer = CharBuffer.wrap(faces);
		final PickGrid grid = new PickGrid(vertexBuffer, faceBuffer, 3);
		final Ray ray = new Ray();
		final Vector3 expected = new Vector3();
		final Vector3 actual = new Vector3();
		int hits = 0;
		for (int i = 0; i < 4000; i++) {
			ray.set((random.nextFloat() * 700) - 30, (random.nextFloat() * 700) - 30, 4096, 0, 0, -8192);
			final boolean all = RenderMathUtils.intersectRayTriangles(ray, vertexBuffer, faceBuffer, 3, expected);
			final int cell = grid.cellAt(ray.origin.x, ray.origin.y);
			final boolean fast = (cell >= 0) && RenderMathUtils.intersectRayTriangles(ray, vertexBuffer, faceBuffer, 3,
					grid.faces(), grid.start(cell), grid.end(cell), actual);
			assertEquals(all, fast);
			if (all) {
				hits++;
				assertEquals(expected.z, actual.z, 1e-3f);
			}
		}
		assertTrue(hits > 1000);
	}
}
