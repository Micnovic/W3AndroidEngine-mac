package com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.parsers.w3x.wpm.War3MapWpm;
import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.PathingFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWorldCollision;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;

/**
 * Runs the searches of the arrays version and of the object version it replaced side by side over random terrain: the
 * same search must end with the same path, point for point.
 */
class CPathfindingEquivalenceTest {
	private static final float CELL = 32f;
	private static final float[] COLLISIONS = { 0f, 8f, 16f, 24f, 32f, 48f, 64f };
	private static final MovementType[] MOVEMENTS = { MovementType.FOOT, MovementType.FOOT, MovementType.FOOT,
			MovementType.FLOAT, MovementType.FLY, MovementType.AMPHIBIOUS };

	private static final class Result {
		List<CPathPoint> waypoints;
		boolean reachesGoal;
	}

	private static final class RecordingMove extends CBehaviorMove {
		private final Result result;

		RecordingMove(final Result result) {
			super(null);
			this.result = result;
		}

		@Override
		public void pathFound(final CPathPoint searchedGoal, final List<CPathPoint> waypoints,
				final boolean reachesGoal, final CSimulation simulation) {
			this.result.waypoints = new ArrayList<>(waypoints);
			this.result.reachesGoal = reachesGoal;
		}
	}

	private static PathingGrid grid(final int width, final int height, final short[] pathing) throws IOException {
		final War3MapWpm wpm = new War3MapWpm(null);
		wpm.getSize()[0] = width;
		wpm.getSize()[1] = height;
		wpm.setPathing(pathing);
		return new PathingGrid(wpm, new float[] { 0, 0 });
	}

	private static short[] terrain(final Random random, final int width, final int height) {
		final short[] pathing = new short[width * height];
		final double density = random.nextDouble() * 0.4;
		for (int i = 0; i < pathing.length; i++) {
			if (random.nextDouble() < density) {
				pathing[i] = (short) (PathingFlags.UNWALKABLE | PathingFlags.UNBUILDABLE);
			}
			else if (random.nextDouble() < density) {
				pathing[i] = PathingFlags.UNSWIMABLE;
			}
			else if (random.nextDouble() < (density / 4)) {
				pathing[i] = PathingFlags.UNFLYABLE;
			}
		}
		final int walls = random.nextInt(6);
		for (int wall = 0; wall < walls; wall++) {
			final boolean vertical = random.nextBoolean();
			final int at = random.nextInt(vertical ? width : height);
			final int gap = random.nextInt(vertical ? height : width);
			final int gapSize = 1 + random.nextInt(4);
			for (int i = 0; i < (vertical ? height : width); i++) {
				if ((i < gap) || (i >= (gap + gapSize))) {
					pathing[vertical ? ((i * width) + at) : ((at * width) + i)] = (short) (PathingFlags.UNWALKABLE
							| PathingFlags.UNBUILDABLE);
				}
			}
		}
		return pathing;
	}

	@Test
	void arraysSearchLikeObjects() throws IOException {
		final Random random = new Random(20261113L);
		int paths = 0;
		int reached = 0;
		int multiTick = 0;
		for (int map = 0; map < 120; map++) {
			final int width = 12 + random.nextInt(70);
			final int height = 12 + random.nextInt(70);
			final PathingGrid grid = grid(width, height, terrain(random, width, height));
			final CWorldCollision collision = new CWorldCollision(new Rectangle(0, 0, width * CELL, height * CELL),
					64f);
			final CSimulation simulation = TestObjects.allocate(CSimulation.class);
			final CPathfindingProcessor.SharedGraph graph = new CPathfindingProcessor.SharedGraph(grid);
			final LegacyPathfindingProcessor.SharedGraph legacyGraph = new LegacyPathfindingProcessor.SharedGraph(
					grid);
			final CPathfindingProcessor[] processors = { new CPathfindingProcessor(grid, collision, graph),
					new CPathfindingProcessor(grid, collision, graph) };
			final LegacyPathfindingProcessor[] legacy = { new LegacyPathfindingProcessor(grid, collision, legacyGraph),
					new LegacyPathfindingProcessor(grid, collision, legacyGraph) };
			for (int round = 0; round < 3; round++) {
				final int jobs = 1 + random.nextInt(6);
				final List<Result> results = new ArrayList<>();
				final List<Result> legacyResults = new ArrayList<>();
				for (int job = 0; job < jobs; job++) {
					final float startX = random.nextFloat() * width * CELL;
					final float startY = random.nextFloat() * height * CELL;
					final float goalX = random.nextFloat() * width * CELL;
					final float goalY = random.nextFloat() * height * CELL;
					final float collisionSize = COLLISIONS[random.nextInt(COLLISIONS.length)];
					final MovementType movement = MOVEMENTS[random.nextInt(MOVEMENTS.length)];
					final boolean smoothing = random.nextBoolean();
					final int processor = random.nextInt(2);
					final Result result = new Result();
					final Result legacyResult = new Result();
					results.add(result);
					legacyResults.add(legacyResult);
					processors[processor].findNaiveSlowPath(null, null, startX, startY,
							new CPathPoint(goalX, goalY), movement, collisionSize, smoothing,
							new RecordingMove(result));
					legacy[processor].findNaiveSlowPath(null, null, startX, startY, new CPathPoint(goalX, goalY),
							movement, collisionSize, smoothing, new RecordingMove(legacyResult));
				}
				int tick = 0;
				for (; tick < 4000; tick++) {
					TestObjects.set(simulation, "gameTurnTick", tick);
					boolean done = true;
					for (int i = 0; i < jobs; i++) {
						done &= (results.get(i).waypoints != null) && (legacyResults.get(i).waypoints != null);
					}
					if (done) {
						break;
					}
					processors[0].update(simulation);
					legacy[0].update(simulation);
					processors[1].update(simulation);
					legacy[1].update(simulation);
					for (int i = 0; i < jobs; i++) {
						assertTrue((legacyResults.get(i).waypoints == null) || (results.get(i).waypoints != null),
								"map " + map + " round " + round + " job " + i + " finished later than the one-budget search");
					}
				}
				if (tick > 1) {
					multiTick++;
				}
				for (int i = 0; i < jobs; i++) {
					final Result expected = legacyResults.get(i);
					final Result actual = results.get(i);
					assertNotNull(expected.waypoints, "search did not finish");
					assertNotNull(actual.waypoints, "search did not finish");
					assertEquals(expected.reachesGoal, actual.reachesGoal, "map " + map + " job " + i);
					assertEquals(expected.waypoints.size(), actual.waypoints.size(), "map " + map + " job " + i);
					for (int p = 0; p < expected.waypoints.size(); p++) {
						assertEquals(Float.floatToIntBits(expected.waypoints.get(p).x),
								Float.floatToIntBits(actual.waypoints.get(p).x), "map " + map + " job " + i);
						assertEquals(Float.floatToIntBits(expected.waypoints.get(p).y),
								Float.floatToIntBits(actual.waypoints.get(p).y), "map " + map + " job " + i);
					}
					paths++;
					if (expected.reachesGoal) {
						reached++;
					}
				}
				if (random.nextInt(4) == 0) {
					graph.dropStaleNodes();
				}
			}
		}
		System.out.println("equivalence: " + paths + " paths, " + reached + " reach the goal, " + multiTick
				+ " rounds over several ticks");
	}
}
