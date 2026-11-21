package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.parsers.w3x.wpm.War3MapWpm;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedObjectGraph;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;

/** The fog a loaded game shows is the fog the game keeps updating, not a copy that stays as it was saved. */
class CPlayerFogOfWarSaveTest {
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
			return false;
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

	private static CPlayerFogOfWar fog() throws IOException {
		final War3MapWpm wpm = new War3MapWpm(null);
		wpm.getSize()[0] = 64;
		wpm.getSize()[1] = 64;
		wpm.setPathing(new short[64 * 64]);
		return new CPlayerFogOfWar(new PathingGrid(wpm, new float[] { 0, 0 }));
	}

	private static CPlayerFogOfWar roundTrip(final CPlayerFogOfWar fog) throws IOException {
		final ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
		final SavedObjectGraph.Writer writer;
		try (DataOutputStream bodies = new DataOutputStream(bodyBytes)) {
			writer = new SavedObjectGraph.Writer(new Rules(), bodies);
			writer.writeRoot(fog);
		}
		final byte[] table = writer.finishTable();
		SavedObjectGraph.checkClassesMatch(table);
		try (DataInputStream bodies = new DataInputStream(new ByteArrayInputStream(bodyBytes.toByteArray()))) {
			return (CPlayerFogOfWar) new SavedObjectGraph.Reader(new Rules(), table, bodies).readRoot();
		}
	}

	@Test
	void aLoadedFogKeepsItsCellsAndTheBufferFollowsLaterChanges() throws IOException {
		final CPlayerFogOfWar fog = fog();
		final CPlayerFogOfWar loaded = roundTrip(fog);
		assertEquals(fog.getFogOfWarBuffer().capacity(), loaded.getFogOfWarBuffer().capacity());
		assertEquals(-1, loaded.getFogOfWarBuffer().get(0));

		loaded.revealAll();
		assertEquals(0, loaded.getFogOfWarBuffer().get(0));
		assertEquals(0, loaded.getFogOfWarBuffer().get(loaded.getFogOfWarBuffer().capacity() - 1));
	}

	@Test
	void aBufferOfAnotherArrayIsWrappedAgain() throws Exception {
		final CPlayerFogOfWar fog = fog();
		final ByteBuffer before = fog.getFogOfWarBuffer();
		assertSame(before, fog.getFogOfWarBuffer());

		// Loading into the game that is running replaces the cells and leaves what it held about them.
		final Field cells = CPlayerFogOfWar.class.getDeclaredField("fogOfWar");
		cells.setAccessible(true);
		final byte[] replaced = new byte[before.capacity()];
		cells.set(fog, replaced);
		replaced[3] = 5;
		assertEquals(5, fog.getFogOfWarBuffer().get(3));
	}

	@Test
	void theExploredCellsComeBackAsSaved() throws IOException {
		final CPlayerFogOfWar fog = fog();
		final ByteBuffer cells = fog.getFogOfWarBuffer();
		for (int i = 0; i < cells.capacity(); i++) {
			// Visible, fogged and masked cells in a pattern, so a lost or reset mask shows.
			cells.put(i, (byte) ((i % 3) == 0 ? 0 : ((i % 3) == 1 ? 1 : -1)));
		}
		final CPlayerFogOfWar loaded = roundTrip(fog);
		for (int i = 0; i < cells.capacity(); i++) {
			assertEquals(cells.get(i), loaded.getFogOfWarBuffer().get(i), "cell " + i);
		}
	}
}
