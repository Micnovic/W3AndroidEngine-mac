package com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.parsers.w3x.wpm.War3MapWpm;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.PathingFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.RemovablePathingMapInstance;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CPathfindingProcessor.Reachability;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CPathfindingProcessor.SharedGraph;

/**
 * A reachability kept up to date over changes of the overlay must answer as one found from scratch: the same points
 * open, the same points in one part, and the parts it has found whole, whatever was built or removed in between.
 */
class ReachabilityRepairTest {
	private static final int SIZE = 72;
	private static final float[] COLLISIONS = { 0f, 16f, 32f, 48f };

	private static PathingGrid grid(final Random random, final double density) throws IOException {
		final War3MapWpm wpm = new War3MapWpm(null);
		wpm.getSize()[0] = SIZE;
		wpm.getSize()[1] = SIZE;
		final short[] pathing = new short[SIZE * SIZE];
		for (int i = 0; i < pathing.length; i++) {
			if (random.nextDouble() < density) {
				pathing[i] = (short) (PathingFlags.UNWALKABLE | PathingFlags.UNBUILDABLE);
			}
		}
		for (int wall = 0; wall < 3; wall++) {
			final boolean vertical = random.nextBoolean();
			final int at = 8 + random.nextInt(SIZE - 16);
			final int gap = random.nextInt(SIZE - 6);
			for (int i = 0; i < SIZE; i++) {
				if ((i < gap) || (i >= (gap + 2))) {
					pathing[vertical ? ((i * SIZE) + at) : ((at * SIZE) + i)] = (short) (PathingFlags.UNWALKABLE
							| PathingFlags.UNBUILDABLE);
				}
			}
		}
		wpm.setPathing(pathing);
		return new PathingGrid(wpm, new float[] { 0, 0 });
	}

	private static ArgbImage texture(final Random random) {
		final int width = 1 + random.nextInt(7);
		final int height = 1 + random.nextInt(7);
		final int[] pixels = new int[width * height];
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = random.nextInt(4) == 0 ? 0 : 0xFFFF0000;
		}
		return new ArgbImage(width, height, pixels);
	}

	/** A line of buildings across the map with one gap, parts on both sides of it found again when it is taken down. */
	private static RemovablePathingMapInstance wall(final Random random, final PathingGrid grid) {
		final int[] pixels = new int[SIZE];
		java.util.Arrays.fill(pixels, 0xFFFF0000);
		pixels[random.nextInt(SIZE)] = 0;
		final boolean vertical = random.nextBoolean();
		final int at = 4 + random.nextInt(SIZE - 8);
		return grid.blitRemovablePathingOverlayTexture(vertical ? world(at) : world(SIZE / 2),
				vertical ? world(SIZE / 2) : world(at), 0, vertical ? new ArgbImage(1, SIZE, pixels)
						: new ArgbImage(SIZE, 1, pixels));
	}

	private static float world(final int cell) {
		return (cell * 32f) + 16f;
	}

	private static void assertSameAnswers(final Random random, final SharedGraph graph, final PathingGrid grid,
			final Reachability kept, final Reachability fresh, final int width, final int height) {
		final int samples = 24;
		final int[] keptParts = new int[samples];
		final int[] freshParts = new int[samples];
		final int[] xs = new int[samples];
		final int[] ys = new int[samples];
		for (int i = 0; i < samples; i++) {
			xs[i] = random.nextInt(width);
			ys[i] = random.nextInt(height);
			keptParts[i] = kept.part(graph, grid, xs[i], ys[i]);
			freshParts[i] = fresh.part(graph, grid, xs[i], ys[i]);
			assertEquals(freshParts[i] == 0, keptParts[i] == 0, "open at " + xs[i] + "," + ys[i]);
		}
		for (int i = 0; i < samples; i++) {
			for (int j = 0; j < samples; j++) {
				assertEquals(freshParts[i] == freshParts[j], keptParts[i] == keptParts[j],
						"one part " + xs[i] + "," + ys[i] + " and " + xs[j] + "," + ys[j]);
			}
		}
		// what a search asks of a part it found: whether any point is in it, without finding more
		for (int i = 0; i < samples; i++) {
			if (freshParts[i] == 0) {
				continue;
			}
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					assertEquals(fresh.label(x, y) == freshParts[i], kept.label(x, y) == keptParts[i],
							"label at " + x + "," + y + " of the part at " + xs[i] + "," + ys[i]);
				}
			}
		}
	}

	@Test
	void keptReachabilityAnswersAsOneFoundAgain() throws IOException {
		for (int seed = 0; seed < 40; seed++) {
			final Random random = new Random(seed);
			final PathingGrid grid = grid(random, 0.02 + (random.nextDouble() * 0.12));
			final SharedGraph graph = new SharedGraph();
			final float collision = COLLISIONS[random.nextInt(COLLISIONS.length)];
			final boolean corners = random.nextInt(4) == 0;
			final Reachability kept = new Reachability(grid, MovementType.FOOT, collision, corners);
			final int width = SIZE + (corners ? 1 : 0);
			final int height = width;
			final List<RemovablePathingMapInstance> built = new ArrayList<>();
			for (int round = 0; round < 60; round++) {
				// fill parts so that there is something to keep
				for (int i = 0; i < 10; i++) {
					kept.part(graph, grid, random.nextInt(width), random.nextInt(height));
				}
				final int changes = 1 + random.nextInt(random.nextInt(8) == 0 ? 12 : 3);
				for (int change = 0; change < changes; change++) {
					final int kind = random.nextInt(10);
					if (kind == 0) {
						built.add(wall(random, grid));
					}
					else if ((kind < 5) || built.isEmpty()) {
						built.add(grid.blitRemovablePathingOverlayTexture(world(random.nextInt(SIZE)),
								world(random.nextInt(SIZE)), random.nextInt(4) * 90, texture(random)));
					}
					else if (kind < 9) {
						built.remove(random.nextInt(built.size())).remove();
					}
					else {
						grid.setTerrainFlag(world(random.nextInt(SIZE)), world(random.nextInt(SIZE)),
								PathingFlags.UNWALKABLE, random.nextBoolean());
					}
				}
				kept.validate(graph, grid);
				final Reachability fresh = new Reachability(grid, MovementType.FOOT, collision, corners);
				assertSameAnswers(random, graph, grid, kept, fresh, width, height);
				if (random.nextInt(3) == 0) {
					// asked about again without a change in between
					kept.validate(graph, grid);
					assertSameAnswers(random, graph, grid, kept, fresh, width, height);
				}
			}
		}
	}

	@Test
	void aSmallBuildingKeepsTheOtherPartsFound() throws IOException {
		final War3MapWpm wpm = new War3MapWpm(null);
		wpm.getSize()[0] = SIZE;
		wpm.getSize()[1] = SIZE;
		wpm.setPathing(new short[SIZE * SIZE]);
		final PathingGrid grid = new PathingGrid(wpm, new float[] { 0, 0 });
		final SharedGraph graph = new SharedGraph();
		final Reachability reach = new Reachability(grid, MovementType.FOOT, 16f, false);
		final int before = reach.part(graph, grid, 3, 3);
		final int[] pixels = new int[16];
		java.util.Arrays.fill(pixels, 0xFFFF0000);
		final RemovablePathingMapInstance building = grid.blitRemovablePathingOverlayTexture(world(50), world(50), 0,
				new ArgbImage(4, 4, pixels));
		reach.validate(graph, grid);
		assertEquals(before, reach.label(3, 3), "the part was found again, not kept");
		assertEquals(before, reach.part(graph, grid, 60, 60));
		assertEquals(0, reach.part(graph, grid, 50, 50));
		building.remove();
		reach.validate(graph, grid);
		assertEquals(before, reach.label(3, 3), "the part was found again, not kept");
		assertEquals(before, reach.part(graph, grid, 50, 50));
	}
}
