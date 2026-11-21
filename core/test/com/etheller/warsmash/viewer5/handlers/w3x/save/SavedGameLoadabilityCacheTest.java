package com.etheller.warsmash.viewer5.handlers.w3x.save;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.badlogic.gdx.files.FileHandle;

class SavedGameLoadabilityCacheTest {
	@TempDir
	File folder;

	@AfterEach
	public void forget() {
		SavedGame.clearCheckedCache();
	}

	private static void write(final File file, final int magic, final int version) throws IOException {
		try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
			out.writeInt(magic);
			out.writeInt(version);
		}
	}

	@Test
	public void aFileThatIsNotASaveIsDamaged() throws IOException {
		final File file = new File(this.folder, "a.w3z");
		write(file, 1, 1);
		assertEquals(SavedGame.Loadability.DAMAGED, SavedGame.loadability(new FileHandle(file)));
	}

	@Test
	public void anUnchangedFileIsNotReadAgainAndAChangedOneIs() throws IOException {
		final File file = new File(this.folder, "b.w3z");
		write(file, 0x57534D53, 9999);
		final long stamp = file.lastModified();
		final FileHandle handle = new FileHandle(file);
		assertEquals(SavedGame.Loadability.OTHER_VERSION, SavedGame.loadability(handle));
		// Same length and time, other content: only a cached answer can still say the old thing.
		write(file, 1, 1);
		file.setLastModified(stamp);
		assertEquals(SavedGame.Loadability.OTHER_VERSION, SavedGame.loadability(handle));
		file.setLastModified(stamp + 5000);
		assertEquals(SavedGame.Loadability.DAMAGED, SavedGame.loadability(handle));
	}

	@Test
	public void aDamagedAnswerIsNotKept() throws IOException {
		final File file = new File(this.folder, "c.w3z");
		write(file, 1, 1);
		final long stamp = file.lastModified();
		final FileHandle handle = new FileHandle(file);
		assertEquals(SavedGame.Loadability.DAMAGED, SavedGame.loadability(handle));
		write(file, 0x57534D53, 9999);
		file.setLastModified(stamp);
		assertEquals(SavedGame.Loadability.OTHER_VERSION, SavedGame.loadability(handle));
		Files.delete(file.toPath());
	}
}
