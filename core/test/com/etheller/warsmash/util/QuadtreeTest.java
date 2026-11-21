package com.etheller.warsmash.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Rectangle;

class QuadtreeTest {

	class Thing {
		String name;
		Rectangle bounds;

		public Thing(final String name, final Rectangle bounds) {
			this.name = name;
			this.bounds = bounds;
		}
	}

	@Test
	void testUnitLeavingAndEnteringTheWorld() {
		final Quadtree<Thing> tree = new Quadtree<>(new Rectangle(0, 0, 1024, 1024));
		final Thing[] things = new Thing[10];
		for (int i = 0; i < things.length; i++) {
			things[i] = new Thing("Thing" + i, new Rectangle(10 + (i * 20f), 100f, 0.002f, 0.002f));
			tree.add(things[i], things[i].bounds);
		}
		// Off the west edge, then back in: neither move may throw, and the unit is found again once inside.
		tree.translate(things[0], things[0].bounds, -50f, 0f);
		tree.translate(things[0], things[0].bounds, -50f, 0f);
		tree.translate(things[0], things[0].bounds, 200f, 0f);
		final int[] found = new int[1];
		tree.intersect(new Rectangle(90f, 90f, 40f, 40f), (thing) -> {
			if (thing == things[0]) {
				found[0]++;
			}
			return false;
		});
		assertEquals(1, found[0]);
	}

	@Test
	void testSevenRects() {
		final Quadtree<Thing> myQuadtree = new Quadtree<>(new Rectangle(-8192, -8192, 16284, 16284));

		final Thing[] things = new Thing[7];
		for (int i = 0; i < 7; i++) {
			things[i] = new Thing("Thing" + i, new Rectangle(3952.6816f, 4246.4473f, 32.0f, 32.0f));
			myQuadtree.add(things[i], things[i].bounds);
		}
		for (int k = 0; k < 100; k++) {
			for (int i = 0; i < 7; i++) {
				final double dang = i * (Math.PI / 7);
				final double xShift = Math.cos(dang) * 20;
				final double yShift = Math.sin(dang) * 20;
				myQuadtree.translate(things[i], things[i].bounds, (float) xShift, (float) yShift);
			}
		}
	}

	@Test
	void testThousandRects() {
		final Quadtree<Thing> myQuadtree = new Quadtree<>(new Rectangle(-8192, -8192, 16284, 16284));

		final int count = 1000;
		final Thing[] things = new Thing[count];
		for (int i = 0; i < count; i++) {
			final double dang = i * ((2 * Math.PI) / count);
			final double x = Math.cos(dang) * 4096;
			final double y = Math.sin(dang) * 4096;
			things[i] = new Thing("Thing" + i, new Rectangle((float) (x - 16), (float) (y - 16), 32.0f, 32.0f));
			myQuadtree.add(things[i], things[i].bounds);
		}
		for (int k = 0; k < 8192; k++) {
			for (int i = 0; i < count; i++) {
				final double dang = i * ((2 * Math.PI) / count);
				final double x = -Math.cos(dang);
				final double y = -Math.sin(dang);
				myQuadtree.translate(things[i], things[i].bounds, (float) x, (float) y);
			}
		}
	}

}
