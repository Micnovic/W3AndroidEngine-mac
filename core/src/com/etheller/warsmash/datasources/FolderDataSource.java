package com.etheller.warsmash.datasources;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.utils.IntSet;
import com.etheller.warsmash.util.Log;

public class FolderDataSource implements DataSource {

	private final Path folderPath;
	private volatile Set<String> listfile;
	/**
	 * Lower case relative path to the path as stored on disk. Lookups are frequent
	 * and mostly miss, so they must not touch the disk, and game data paths do not
	 * match the case of the files on case sensitive file systems.
	 */
	private volatile Map<String, String> lowerCaseToStoredPath;
	/** The hash codes of the keys of {@link #lowerCaseToStoredPath}, which answer most misses without a key. */
	private volatile IntSet keyHashes;

	public FolderDataSource(final Path folderPath) {
		this.folderPath = folderPath;
		useCurrentIndex();
	}

	private void useCurrentIndex() {
		FolderIndex index;
		synchronized (INDEXES) {
			index = INDEXES.get(this.folderPath);
			if ((index == null) || !index.isCurrent()) {
				index = new FolderIndex(this.folderPath);
				INDEXES.put(this.folderPath, index);
			}
		}
		this.keyHashes = index.keyHashes;
		this.lowerCaseToStoredPath = index.lowerCaseToStoredPath;
		this.listfile = index.listfile;
	}

	/**
	 * The data sources are made again for every game, and listing a data folder of tens of thousands of files took
	 * seconds on phones. An index is reused while no folder in it has changed (adding or removing a file changes its
	 * folder's modification time). Folders that cannot be read are left out instead of failing the whole source.
	 */
	private static final Map<Path, FolderIndex> INDEXES = new HashMap<>();

	private static final long SETTLE_MILLIS = 2500;

	private static final class FolderIndex {
		private final Set<String> listfile = new HashSet<>();
		private final Map<String, String> lowerCaseToStoredPath = new HashMap<>();
		private final IntSet keyHashes = new IntSet();
		private final Map<Path, FileTime> folderTimes = new HashMap<>();
		/**
		 * False when a folder changed so shortly before the listing that a file added right after it would leave the
		 * modification time as it was (file systems stamp folders coarsely, FAT in two seconds).
		 */
		private boolean settled = true;

		private FolderIndex(final Path folderPath) {
			final long listedAt = System.currentTimeMillis();
			try {
				Files.walkFileTree(folderPath, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
						FolderIndex.this.folderTimes.put(dir, attrs.lastModifiedTime());
						if (listedAt - attrs.lastModifiedTime().toMillis() < SETTLE_MILLIS) {
							FolderIndex.this.settled = false;
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
						if (attrs.isRegularFile()) {
							final String relativePath = folderPath.relativize(file).toString();
							FolderIndex.this.listfile.add(relativePath);
							final String key = relativePath.toLowerCase(Locale.US);
							FolderIndex.this.lowerCaseToStoredPath.put(key, relativePath);
							FolderIndex.this.keyHashes.add(key.hashCode());
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
						Log.error("Data folder entry left out, it cannot be read: " + exc);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
						if (exc != null) {
							Log.error("Data folder listed only in part, it cannot be read: " + exc);
						}
						return FileVisitResult.CONTINUE;
					}
				});
			}
			catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}

		private boolean isCurrent() {
			if (!this.settled) {
				return false;
			}
			for (final Map.Entry<Path, FileTime> folder : this.folderTimes.entrySet()) {
				try {
					if (!Files.getLastModifiedTime(folder.getKey()).equals(folder.getValue())) {
						return false;
					}
				}
				catch (final IOException e) {
					return false;
				}
			}
			return true;
		}
	}

	@Override
	public InputStream getResourceAsStream(String filepath) throws IOException {
		final String storedPath = findStoredPath(filepath);
		if (storedPath == null) {
			return null;
		}
		return new IndependentStream.FromFile(this.folderPath.resolve(storedPath).toFile());
	}

	@Override
	public IndependentStream.Opener opener(final String filepath) {
		final String storedPath = findStoredPath(filepath);
		if (storedPath == null) {
			return null;
		}
		final File file = this.folderPath.resolve(storedPath).toFile();
		return () -> new IndependentStream.FromFile(file);
	}

	@Override
	public File getFile(String filepath) throws IOException {
		final String storedPath = findStoredPath(filepath);
		if (storedPath == null) {
			return null;
		}
		return new File(this.folderPath.toString() + File.separatorChar + storedPath);
	}

	@Override
	public File getDirectory(String filepath) throws IOException {
		filepath = fixFilepath(filepath);
		// Paths come from map and data content: ".." or an absolute path must not leave the data folder.
		if (!this.folderPath.resolve(filepath).normalize().startsWith(this.folderPath.normalize())) {
			return null;
		}
		File file = new File(this.folderPath.toString() + File.separatorChar + filepath);
		if(!file.exists() || !file.isDirectory()) {
			return null;
		}
		return file;
	}

	@Override
	public ByteBuffer read(String path) throws IOException {
		final String storedPath = findStoredPath(path);
		if (storedPath == null) {
			return null;
		}
		// Reads through a channel (Files.readAllBytes, Files.newInputStream) keep a direct buffer as big as the largest
		// read on every thread that made one; Android counts those on the Java heap.
		final File file = new File(this.folderPath.toString(), storedPath);
		try (DataInputStream stream = new DataInputStream(new FileInputStream(file))) {
			final byte[] bytes = new byte[(int) file.length()];
			stream.readFully(bytes);
			return ByteBuffer.wrap(bytes);
		}
	}

	@Override
	public boolean has(final String filepath) {
		return findStoredPath(filepath) != null;
	}

	private String findStoredPath(final String filepath) {
		final int hash = asciiKeyHash(filepath);
		if ((hash != NOT_ASCII) && !this.keyHashes.contains(hash)) {
			return null;
		}
		return this.lowerCaseToStoredPath.get(lookupKey(filepath));
	}

	private static final int NOT_ASCII = 0;

	/**
	 * The hash code of lookupKey(filepath) without making the key, or NOT_ASCII for a path that is not plain ASCII
	 * (or whose key happens to hash to it), which is looked up the long way.
	 */
	private static int asciiKeyHash(final String filepath) {
		int hash = 0;
		final int length = filepath.length();
		for (int i = 0; i < length; i++) {
			char c = filepath.charAt(i);
			if (c >= 128) {
				return NOT_ASCII;
			}
			if ((c == '\\') || (c == '/') || (c == ':')) {
				c = File.separatorChar;
			}
			else if ((c >= 'A') && (c <= 'Z')) {
				c += 'a' - 'A';
			}
			hash = (31 * hash) + c;
		}
		return hash;
	}

	// The same key as fixFilepath(filepath).toLowerCase(Locale.US), built in one pass for ASCII paths: every file
	// request asks each folder source in turn, which made the three copies a visible part of game start on phones.
	private static String lookupKey(final String filepath) {
		final int length = filepath.length();
		final char[] key = new char[length];
		for (int i = 0; i < length; i++) {
			char c = filepath.charAt(i);
			if (c >= 128) {
				return fixFilepath(filepath).toLowerCase(Locale.US);
			}
			if ((c == '\\') || (c == '/') || (c == ':')) {
				c = File.separatorChar;
			}
			else if ((c >= 'A') && (c <= 'Z')) {
				c += 'a' - 'A';
			}
			key[i] = c;
		}
		return new String(key);
	}

	@Override
	public Collection<String> getListfile() {
		// A source kept open while files were added or removed (a map dropped into the data folder) lists them anew.
		useCurrentIndex();
		return this.listfile;
	}

	@Override
	public void close() {
	}

	private static String fixFilepath(final String filepath) {
		return filepath.replace('\\', File.separatorChar).replace('/', File.separatorChar).replace(':',
				File.separatorChar);
	}
}
