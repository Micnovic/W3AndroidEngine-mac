package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderCheckCacheTest {
	@TempDir
	File temp;

	private File data;
	private File cacheFile;

	@BeforeEach
	void setUp() throws IOException {
		FolderCheckCache.settleMillis = 0;
		this.data = new File(this.temp, "data");
		this.cacheFile = new File(this.temp, "folder-check.txt");
		write("units/UnitData.slk", "a");
		write("ui/WorldEditGameStrings.txt", "b");
		write("maps/deep/er/(2)Map.w3x", "c");
		write("config.txt", "d");
		age(this.data);
		DataFolderCheck.useCache(this.cacheFile, "v1");
		DataFolderCheck.resetMemory();
	}

	@AfterEach
	void tearDown() {
		FolderCheckCache.settleMillis = 2500;
		DataFolderCheck.useCache(null, "");
		DataFolderCheck.resetMemory();
	}

	private void write(final String name, final String text) throws IOException {
		final File file = new File(this.data, name);
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	/** Folder times of a minute ago, as a folder that nothing has touched for a while. */
	static void age(final File folder) {
		final File[] children = folder.listFiles();
		if (children != null) {
			for (final File child : children) {
				if (child.isDirectory()) {
					age(child);
				}
			}
		}
		folder.setLastModified(System.currentTimeMillis() - 60_000);
	}

	/** A change of a folder that leaves a different modification time, also on a file system with coarse times. */
	static void touch(final File folder) {
		folder.setLastModified(folder.lastModified() + 7_000);
	}

	private DataFolderCheck.Readability walk() {
		return DataFolderCheck.readability(this.data, Integer.MAX_VALUE, 3);
	}

	private static void restart() {
		DataFolderCheck.resetMemory();
	}

	@Test
	void aFolderWalkedBeforeIsNotWalkedAgainAfterTheAppRestarts() {
		final DataFolderCheck.Readability first = walk();
		assertEquals(0, first.unreadable);
		final int walks = DataFolderCheck.WALKS.get();
		assertTrue(this.cacheFile.isFile());
		restart();
		final DataFolderCheck.Readability second = walk();
		assertEquals(walks, DataFolderCheck.WALKS.get());
		assertEquals(first.files, second.files);
		assertEquals(first.fileCount, second.fileCount);
	}

	@Test
	void theCountIsReadFromTheCacheWithoutAWalk() {
		walk();
		final int walks = DataFolderCheck.WALKS.get();
		restart();
		assertEquals("4 files", DataFolderCheck.describe(DataFolderCheck.Kind.FOLDER, this.data));
		assertEquals(walks, DataFolderCheck.WALKS.get());
		assertNotNull(DataFolderCheck.current(this.data));
	}

	@Test
	void aFileAddedInASubfolderBelowTheTopLevelIsFound() throws IOException {
		walk();
		restart();
		write("maps/deep/er/new.txt", "x");
		touch(new File(this.data, "maps/deep/er"));
		assertNull(DataFolderCheck.current(this.data));
		final int walks = DataFolderCheck.WALKS.get();
		assertEquals(5, walk().fileCount);
		assertEquals(walks + 1, DataFolderCheck.WALKS.get());
	}

	@Test
	void aFileDeletedIsFound() {
		walk();
		restart();
		assertTrue(new File(this.data, "config.txt").delete());
		touch(this.data);
		assertEquals(3, walk().fileCount);
	}

	@Test
	void aFolderRenamedAndANewFolderAreFound() {
		walk();
		restart();
		assertTrue(new File(this.data, "ui").renameTo(new File(this.data, "ui2")));
		touch(this.data);
		assertNull(DataFolderCheck.current(this.data));
		final DataFolderCheck.Readability walked = walk();
		assertEquals(4, walked.fileCount);
		age(this.data);
		restart();
		new File(this.data, "empty").mkdirs();
		touch(this.data);
		assertEquals(walked.files + 1, walk().files);
	}

	@Test
	void aFileAddedInAnotherFolderInvalidatesTheResult() throws IOException {
		walk();
		restart();
		write("units/other.slk", "x");
		touch(new File(this.data, "units"));
		assertNull(DataFolderCheck.current(this.data));
	}

	@Test
	void aKeyFileThatChangedInvalidatesTheResult() throws IOException {
		walk();
		restart();
		assertNotNull(DataFolderCheck.current(this.data));
		write("units/UnitData.slk", "a longer file");
		assertNull(DataFolderCheck.current(this.data));
	}

	@Test
	void aFolderThatChangedTooRecentlyIsNotKept() throws IOException {
		FolderCheckCache.settleMillis = 5_000;
		write("units/fresh.slk", "x");
		walk();
		assertFalse(this.cacheFile.isFile()
				&& new String(Files.readAllBytes(this.cacheFile.toPath()), StandardCharsets.UTF_8).contains("folder\t"));
		restart();
		assertNull(DataFolderCheck.current(this.data));
	}

	@Test
	void anotherVersionOfTheAppReadsTheFolderAgain() {
		walk();
		restart();
		DataFolderCheck.useCache(this.cacheFile, "v2");
		assertNull(DataFolderCheck.current(this.data));
	}

	@Test
	void aDamagedCacheFileIsIgnored() throws IOException {
		walk();
		Files.write(this.cacheFile.toPath(),
				"warsmash-folder-check 1\nv1\nfolder\tx\ndir\tnot-a-number\ty\n".getBytes(StandardCharsets.UTF_8));
		restart();
		assertNull(DataFolderCheck.current(this.data));
		assertEquals(4, walk().fileCount);
	}

	@Test
	void aFolderWithFilesTheGameCannotReadIsNotKept() {
		final File hidden = new File(this.data, "config.txt");
		Assumptions.assumeTrue(hidden.setReadable(false) && !hidden.canRead());
		try {
			assertEquals(1, walk().unreadable);
			restart();
			assertNull(DataFolderCheck.current(this.data));
		}
		finally {
			hidden.setReadable(true);
		}
	}

	@Test
	void theContentProblemsAreKeptWithTheResultAndGoWithIt() throws IOException {
		walk();
		DataFolderCheck.cacheProblems(this.data, Arrays.asList("SCRIPTS", "NO_MELEE_MAPS"));
		restart();
		final List<String> kept = DataFolderCheck.cachedProblems(this.data);
		assertEquals(Arrays.asList("SCRIPTS", "NO_MELEE_MAPS"), kept);
		write("maps/extra.txt", "x");
		touch(new File(this.data, "maps"));
		assertNull(DataFolderCheck.cachedProblems(this.data));
	}

	@Test
	void forgettingAFolderDropsItsCachedResult() {
		walk();
		DataFolderCheck.forget(this.data);
		restart();
		assertNull(DataFolderCheck.current(this.data));
	}
}
