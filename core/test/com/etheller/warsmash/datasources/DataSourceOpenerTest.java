package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.util.StreamBytes;

/**
 * A file is opened on another thread as the data source would open it on its own, and lookups may come from several
 * threads at once.
 */
class DataSourceOpenerTest {
	@TempDir
	File temp;

	@Test
	void theOpenerReadsTheFileTheSourceReads() throws Exception {
		final File low = folder("low", "a.txt", "low a", "b.txt", "low b");
		final File high = folder("high", "a.txt", "high a");
		final CompoundDataSource data = new CompoundDataSource(
				Arrays.asList(new FolderDataSource(low.toPath()), new FolderDataSource(high.toPath())));
		for (final String path : new String[] { "a.txt", "b.txt", "A.TXT" }) {
			final IndependentStream.Opener opener = data.opener(path);
			assertNotNull(opener, path);
			assertArrayEquals(read(data.getResourceAsStream(path)), read(opener.open()), path);
		}
		assertNull(data.opener("c.txt"));
	}

	@Test
	void anOpeningStreamOpensOnItsFirstRead() throws Exception {
		final int[] opened = new int[1];
		final File file = new File(folder("f", "a.txt", "abc"), "a.txt");
		try (InputStream stream = new IndependentStream.Opening(() -> {
			opened[0]++;
			return new IndependentStream.FromFile(file);
		})) {
			assertEquals(0, opened[0]);
			assertEquals('a', stream.read());
			assertEquals("bc", new String(StreamBytes.readAll(stream), "UTF-8"));
			assertEquals(1, opened[0]);
		}
		// one that is never read is never opened
		new IndependentStream.Opening(() -> {
			opened[0]++;
			return null;
		}).close();
		assertEquals(1, opened[0]);
	}

	@Test
	void aSourceThatOnlyItsOwnThreadReadsGivesNoOpener() throws Exception {
		final DataSource shared = new OneThreadOnly();
		final CompoundDataSource data = new CompoundDataSource(
				Arrays.asList(new FolderDataSource(folder("f", "b.txt", "b").toPath()), shared));
		assertTrue(data.has("a.txt"));
		assertNull(data.opener("a.txt"));
		assertNotNull(data.opener("b.txt"));
	}

	@Test
	void lookupsFromSeveralThreadsAgree() throws Exception {
		final String[] names = new String[400];
		final String[] contents = new String[names.length * 2];
		for (int i = 0; i < names.length; i++) {
			names[i] = "f" + i + ".txt";
			contents[2 * i] = names[i];
			contents[(2 * i) + 1] = Integer.toString(i);
		}
		final CompoundDataSource data = new CompoundDataSource(
				Arrays.asList(new FolderDataSource(folder("a", contents).toPath())));
		final ExecutorService executor = Executors.newFixedThreadPool(4);
		try {
			final List<Future<Boolean>> results = new java.util.ArrayList<>();
			for (int t = 0; t < 4; t++) {
				results.add(executor.submit(() -> {
					for (int round = 0; round < 20; round++) {
						for (int i = 0; i < names.length; i++) {
							if (!data.has(names[i]) || data.has("missing" + i + ".txt")
									|| !names[i].equals(data.findFirst("missing.txt", names[i]))) {
								return false;
							}
						}
					}
					return true;
				}));
			}
			for (final Future<Boolean> result : results) {
				assertTrue(result.get());
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	private File folder(final String name, final String... namesAndContents) throws IOException {
		final File folder = new File(this.temp, name);
		assertTrue(folder.mkdirs());
		for (int i = 0; i < namesAndContents.length; i += 2) {
			Files.write(new File(folder, namesAndContents[i]).toPath(), namesAndContents[i + 1].getBytes("UTF-8"));
		}
		return folder;
	}

	private static byte[] read(final InputStream stream) throws IOException {
		try (InputStream in = stream) {
			return StreamBytes.readAll(in);
		}
	}

	/** Like an archive read through one shared channel: it has a.txt, but only its own thread may open it. */
	private static final class OneThreadOnly implements DataSource {
		@Override
		public InputStream getResourceAsStream(final String filepath) {
			return has(filepath) ? new java.io.ByteArrayInputStream(new byte[] { 1 }) : null;
		}

		@Override
		public File getFile(final String filepath) {
			return null;
		}

		@Override
		public File getDirectory(final String filepath) {
			return null;
		}

		@Override
		public ByteBuffer read(final String path) {
			return has(path) ? ByteBuffer.wrap(new byte[] { 1 }) : null;
		}

		@Override
		public boolean has(final String filepath) {
			return "a.txt".equalsIgnoreCase(filepath);
		}

		@Override
		public Collection<String> getListfile() {
			return Collections.singletonList("a.txt");
		}

		@Override
		public void close() {
		}
	}
}
