package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.badlogic.gdx.files.FileHandle;

class CSaveStorageWriteTest {
	@TempDir
	File folder;

	@Test
	public void aNewSaveReplacesTheOldOne() throws IOException {
		final File file = new File(this.folder, "Save/x.w3z");
		assertTrue(CSaveStorage.writeAtomically(new FileHandle(file), out -> out.writeInt(1)));
		assertTrue(CSaveStorage.writeAtomically(new FileHandle(file), out -> out.writeInt(2)));
		assertArrayEquals(new byte[] { 0, 0, 0, 2 }, Files.readAllBytes(file.toPath()));
		assertFalse(new File(file.getPath() + ".tmp").exists());
	}

	@Test
	public void aFailedWriteLeavesThePreviousSave() throws IOException {
		final File file = new File(this.folder, "Save/y.w3z");
		assertTrue(CSaveStorage.writeAtomically(new FileHandle(file), out -> out.writeInt(7)));
		assertFalse(CSaveStorage.writeAtomically(new FileHandle(file), out -> {
			out.writeInt(8);
			throw new IOException("disk full");
		}));
		assertArrayEquals(new byte[] { 0, 0, 0, 7 }, Files.readAllBytes(file.toPath()));
		assertFalse(new File(file.getPath() + ".tmp").exists());
	}

	@Test
	public void thePatcherChangesTheBytesBeforeTheRename() throws IOException {
		final File file = new File(this.folder, "Save/z.w3z");
		assertTrue(CSaveStorage.writeAtomically(new FileHandle(file), out -> out.writeInt(0), patched -> {
			patched.seek(0);
			patched.writeInt(5);
		}));
		assertEquals(5, Files.readAllBytes(file.toPath())[3]);
	}
}
