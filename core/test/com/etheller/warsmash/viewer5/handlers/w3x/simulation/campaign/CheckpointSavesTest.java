package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.badlogic.gdx.files.FileHandle;

class CheckpointSavesTest {
	private static FileHandle save(final File folder, final String name) {
		return new FileHandle(new File(folder, name));
	}

	@Test
	void withoutALimitNothingIsPushedOut(@TempDir final File folder) {
		final CheckpointSaves saves = new CheckpointSaves();
		for (int i = 0; i < 5; i++) {
			assertTrue(saves.record(save(folder, "c" + i + ".w3z")).isEmpty());
		}
	}

	@Test
	void theOldestSavesGoWhenTheLimitIsReached(@TempDir final File folder) {
		final CheckpointSaves saves = new CheckpointSaves();
		saves.setMax(2);
		assertTrue(saves.record(save(folder, "a.w3z")).isEmpty());
		assertTrue(saves.record(save(folder, "b.w3z")).isEmpty());
		final List<FileHandle> out = saves.record(save(folder, "c.w3z"));
		assertEquals(1, out.size());
		assertEquals("a.w3z", out.get(0).name());
	}

	@Test
	void aSaveMadeAgainIsTheNewestAndLoweringTheLimitTrimsAtOnce(@TempDir final File folder) {
		final CheckpointSaves saves = new CheckpointSaves();
		saves.record(save(folder, "a.w3z"));
		saves.record(save(folder, "b.w3z"));
		saves.record(save(folder, "c.w3z"));
		saves.record(save(folder, "a.w3z"));
		final List<FileHandle> out = saves.setMax(1);
		assertEquals(2, out.size());
		assertEquals("b.w3z", out.get(0).name());
		assertEquals("c.w3z", out.get(1).name());
	}

	@Test
	void theNewestSaveStaysEvenForALimitOfZero(@TempDir final File folder) {
		final CheckpointSaves saves = new CheckpointSaves();
		saves.setMax(0);
		assertTrue(saves.record(save(folder, "a.w3z")).isEmpty());
		assertEquals(1, saves.record(save(folder, "b.w3z")).size());
	}

	@Test
	void pushedOutFilesAreDeleted(@TempDir final File folder) throws IOException {
		final File old = new File(folder, "old.w3z");
		Files.write(old.toPath(), new byte[] { 1 });
		CheckpointSaves.delete(java.util.Collections.singletonList(new FileHandle(old)));
		assertFalse(old.exists());
	}
}
