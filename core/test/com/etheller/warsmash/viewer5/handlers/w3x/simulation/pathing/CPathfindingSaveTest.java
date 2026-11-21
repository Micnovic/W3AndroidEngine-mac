package com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.parsers.w3x.wpm.War3MapWpm;
import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.PathingFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedObjectGraph;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWorldCollision;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;

/** A search that is saved between two ticks goes on, after loading, to the path it would have found. */
class CPathfindingSaveTest {
	private static final int SIZE = 96;
	private static final float CELL = 32f;

	private static final class Result {
		List<CPathPoint> waypoints;
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
		}
	}

	private static final class Holder {
		CPathfindingProcessor.SharedGraph graph;
		CPathfindingProcessor processor;
		List<Result> results;
	}

	private static final class Rules implements SavedObjectGraph.Rules {
		@Override
		public String externalName(final Object object) {
			return null;
		}

		@Override
		public boolean isInPlace(final Object object) {
			return false;
		}

		@Override
		public boolean isDropped(final Object object) {
			return (object instanceof PathingGrid) || (object instanceof CWorldCollision);
		}

		@Override
		public Object replace(final Object object) {
			return object;
		}

		@Override
		public Object resolveExternal(final String name) {
			return null;
		}

		@Override
		public void reportDropped(final Class<?> holder, final String field, final Object object) {
		}
	}

	private static PathingGrid grid() throws IOException {
		final War3MapWpm wpm = new War3MapWpm(null);
		wpm.getSize()[0] = SIZE;
		wpm.getSize()[1] = SIZE;
		final short[] pathing = new short[SIZE * SIZE];
		for (int y = 0; y < (SIZE - 6); y++) {
			pathing[(y * SIZE) + 30] = (short) (PathingFlags.UNWALKABLE | PathingFlags.UNBUILDABLE);
			pathing[((SIZE - 1 - y) * SIZE) + 60] = (short) (PathingFlags.UNWALKABLE | PathingFlags.UNBUILDABLE);
		}
		wpm.setPathing(pathing);
		return new PathingGrid(wpm, new float[] { 0, 0 });
	}

	private static Holder load(final Holder saved, final PathingGrid grid, final CWorldCollision collision)
			throws IOException {
		final ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
		final SavedObjectGraph.Writer writer;
		try (DataOutputStream bodies = new DataOutputStream(bodyBytes)) {
			writer = new SavedObjectGraph.Writer(new Rules(), bodies);
			writer.writeRoot(saved);
		}
		final byte[] table = writer.finishTable();
		SavedObjectGraph.checkClassesMatch(table);
		final Holder loaded;
		try (DataInputStream bodies = new DataInputStream(new ByteArrayInputStream(bodyBytes.toByteArray()))) {
			loaded = (Holder) new SavedObjectGraph.Reader(new Rules(), table, bodies).readRoot();
		}
		TestObjects.set(loaded.processor, "pathingGrid", grid);
		TestObjects.set(loaded.processor, "worldCollision", collision);
		return loaded;
	}

	@Test
	void searchSavedBetweenTicksGoesOn() throws IOException {
		final PathingGrid grid = grid();
		final CWorldCollision collision = new CWorldCollision(new Rectangle(0, 0, SIZE * CELL, SIZE * CELL), 64f);
		final CSimulation simulation = TestObjects.allocate(CSimulation.class);
		final Holder live = new Holder();
		live.graph = new CPathfindingProcessor.SharedGraph();
		live.processor = new CPathfindingProcessor(grid, collision, live.graph);
		live.results = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			final Result result = new Result();
			live.results.add(result);
			live.processor.findNaiveSlowPath(null, null, 40 + (i * 20), 40, new CPathPoint(SIZE * CELL * 0.95f, 80),
					MovementType.FOOT, (i % 2) == 0 ? 8f : 16f, true, new RecordingMove(result));
		}
		TestObjects.set(simulation, "gameTurnTick", 0);
		live.processor.update(simulation);
		assertTrue(live.processor.getMoveQueue().get(0).isJobStarted(), "no search under way to save");
		assertNull(live.results.get(0).waypoints, "search finished before the save");

		final Holder loaded = load(live, grid, collision);
		for (int tick = 1; tick < 500; tick++) {
			TestObjects.set(simulation, "gameTurnTick", tick);
			live.processor.update(simulation);
			loaded.processor.update(simulation);
		}
		for (int i = 0; i < 4; i++) {
			final List<CPathPoint> expected = live.results.get(i).waypoints;
			final List<CPathPoint> actual = loaded.results.get(i).waypoints;
			assertNotNull(expected);
			assertNotNull(actual);
			assertEquals(expected.size(), actual.size());
			for (int p = 0; p < expected.size(); p++) {
				assertEquals(expected.get(p).x, actual.get(p).x);
				assertEquals(expected.get(p).y, actual.get(p).y);
			}
		}
	}
}
