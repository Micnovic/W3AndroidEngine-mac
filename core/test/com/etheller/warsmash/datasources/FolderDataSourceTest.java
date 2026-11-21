package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderDataSourceTest {
	@TempDir
	File temp;

	@Test
	void aFileAddedRightAfterTheFolderWasListedIsListed() throws Exception {
		for (int i = 0; i < 200; i++) {
			final File folder = new File(this.temp, "f" + i);
			assertTrue(folder.mkdirs());
			Files.write(new File(folder, "a.txt").toPath(), new byte[] { 1 });
			final FolderDataSource source = new FolderDataSource(folder.toPath());
			assertTrue(source.getListfile().contains("a.txt"));
			Files.write(new File(folder, "b.txt").toPath(), new byte[] { 2 });
			assertTrue(source.getListfile().contains("b.txt"), "round " + i);
		}
	}

	@Test
	void aDirectoryOutsideTheFolderIsNotReturned() throws Exception {
		final File root = new File(this.temp, "root");
		assertTrue(new File(root, "inside").mkdirs());
		assertTrue(new File(this.temp, "sibling").mkdirs());
		final FolderDataSource source = new FolderDataSource(root.toPath());
		assertNotNull(source.getDirectory("inside"));
		assertNotNull(source.getDirectory("inside\\..\\inside"));
		assertNull(source.getDirectory(".."));
		assertNull(source.getDirectory("..\\sibling"));
		assertNull(source.getDirectory("../sibling"));
		assertNull(source.getDirectory(this.temp.getAbsolutePath()));
	}
}
