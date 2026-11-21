package net.warsmash.lan.maps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.warsmash.lan.protocol.LanData;

/**
 * Finds a map the host announced among the maps this device already has. Hashes are cached by path, size and
 * modification time, so only a file that is new or changed is read again. Blocking: use it off the network thread.
 */
public final class LanMapIndex {
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final int MAX_DEPTH = 6;
	private static final int MAX_VISITED = 20000;
	private static final int MAX_ENTRIES = 4096;
	private static final long STALE_PART_MS = 7L * 24 * 3600 * 1000;

	/** The maps of the game's own data (archives, not folders of files): found by name, read when they are needed. */
	public interface VirtualMaps {
		/** The paths in the game data of the maps with this file name. */
		List<String> pathsNamed(String fileName);

		/** A file holding the map of that path, or null when it cannot be read. */
		File extract(String path) throws IOException;

		/** Changes when the game data do, so hashes remembered for the old data are not trusted. */
		long stamp();
	}

	private static final String VIRTUAL_KEY = "data:";

	private static final class Entry {
		long size;
		long modified;
		byte[] sha256;
		int crc32;
	}

	private final File cacheFile;
	private final File downloadDir;
	private final List<File> roots = new ArrayList<File>();
	private final Map<String, Entry> cache = new HashMap<String, Entry>();
	private boolean dirty;
	private int hashed;
	private VirtualMaps virtualMaps;

	/**
	 * @param cacheFile where hashes are remembered between runs; null keeps them in memory only
	 * @param downloadDir where received maps are stored ({@code Maps/Download}); searched like any root
	 */
	public LanMapIndex(final File cacheFile, final File downloadDir) {
		this.cacheFile = cacheFile;
		this.downloadDir = downloadDir;
		load();
	}

	/** How many files were read to compute a hash so far (cache misses); for diagnostics and tests. */
	public synchronized int hashesComputed() {
		return this.hashed;
	}

	public File downloadDir() {
		return this.downloadDir;
	}

	public synchronized LanMapIndex addRoot(final File dir) {
		this.roots.add(dir);
		return this;
	}

	/** Lets the search look into the game's own archives too, where the maps of the game are not files. */
	public synchronized LanMapIndex setVirtualMaps(final VirtualMaps maps) {
		this.virtualMaps = maps;
		return this;
	}

	/** The file that is exactly this map, or null. */
	public File find(final LanData.MapInfo info) throws IOException {
		if (info.sha256.length == 0 || info.size <= 0) {
			return null;
		}
		final VirtualMaps maps;
		synchronized (this) {
			final List<File> candidates = new ArrayList<File>();
			final int[] visited = new int[1];
			for (final File root : this.roots) {
				collect(root, 0, info.size, candidates, visited);
			}
			collect(this.downloadDir, 0, info.size, candidates, visited);
			Collections.sort(candidates, new Comparator<File>() {
				@Override
				public int compare(final File a, final File b) {
					final boolean an = a.getName().equalsIgnoreCase(info.name);
					final boolean bn = b.getName().equalsIgnoreCase(info.name);
					return an == bn ? 0 : an ? -1 : 1;
				}
			});
			for (final File f : candidates) {
				final Entry e = hashOf(f);
				if (e != null && Arrays.equals(e.sha256, info.sha256) && e.crc32 == info.crc32) {
					save();
					return f;
				}
			}
			save();
			maps = this.virtualMaps;
		}
		// Not under the lock: the game data's maps are read by the launcher's catalog thread, which may itself be
		// waiting for this index (describing the host's map).
		final File found = findVirtual(maps, info);
		synchronized (this) {
			save();
		}
		return found;
	}

	private File findVirtual(final VirtualMaps maps, final LanData.MapInfo info) throws IOException {
		if (maps == null || info.name.isEmpty()) {
			return null;
		}
		for (final String path : maps.pathsNamed(info.name)) {
			final String key = VIRTUAL_KEY + path;
			Entry e;
			synchronized (this) {
				e = this.cache.get(key);
			}
			if (e != null && e.modified == maps.stamp() && e.size != info.size) {
				continue;
			}
			File file = null;
			if (e == null || e.modified != maps.stamp()) {
				file = maps.extract(path);
				if (file == null || file.length() != info.size) {
					continue;
				}
				final MapHash.Result r = MapHash.compute(file);
				e = new Entry();
				e.size = r.size;
				e.modified = maps.stamp();
				e.sha256 = r.sha256;
				e.crc32 = r.crc32;
				synchronized (this) {
					this.hashed++;
					this.cache.put(key, e);
					this.dirty = true;
				}
			}
			if (e.size == info.size && Arrays.equals(e.sha256, info.sha256) && e.crc32 == info.crc32) {
				return file != null ? file : maps.extract(path);
			}
		}
		return null;
	}

	/** Announcement for a map file of the host, using cached hashes where valid. */
	public synchronized LanData.MapInfo describe(final File file) throws IOException {
		final Entry e = hashOf(file);
		if (e == null) {
			throw new IOException("cannot read " + file.getName());
		}
		save();
		return MapHash.describe(file, new MapHash.Result(e.size, e.sha256, e.crc32));
	}

	/** Records the hashes of a file that was just verified, so the next search finds it without reading it. */
	public synchronized void remember(final File file, final byte[] sha256, final int crc32) {
		final Entry e = new Entry();
		e.size = file.length();
		e.modified = file.lastModified();
		e.sha256 = sha256;
		e.crc32 = crc32;
		this.cache.put(key(file), e);
		this.dirty = true;
		save();
	}

	/** Deletes leftovers of interrupted downloads that nobody resumed for a week. */
	public synchronized void cleanStaleParts() {
		final File[] files = this.downloadDir == null ? null : this.downloadDir.listFiles();
		if (files == null) {
			return;
		}
		final long limit = System.currentTimeMillis() - STALE_PART_MS;
		for (final File f : files) {
			if (f.isFile() && f.getName().endsWith(".part") && f.lastModified() < limit) {
				f.delete();
			}
		}
	}

	private void collect(final File dir, final int depth, final int size, final List<File> out, final int[] visited) {
		if (dir == null || depth > MAX_DEPTH) {
			return;
		}
		final File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (final File f : files) {
			if (++visited[0] > MAX_VISITED) {
				return;
			}
			if (f.isDirectory()) {
				collect(f, depth + 1, size, out, visited);
			}
			else if (f.length() == size && MapRules.checkName(f.getName()) == null && !out.contains(f)) {
				out.add(f);
			}
		}
	}

	private Entry hashOf(final File f) throws IOException {
		final String key = key(f);
		final long size = f.length();
		final long modified = f.lastModified();
		Entry e = this.cache.get(key);
		if (e != null && e.size == size && e.modified == modified) {
			return e;
		}
		final MapHash.Result r = MapHash.compute(f);
		this.hashed++;
		e = new Entry();
		e.size = r.size;
		e.modified = modified;
		e.sha256 = r.sha256;
		e.crc32 = r.crc32;
		if (this.cache.size() >= MAX_ENTRIES) {
			this.cache.clear();
		}
		this.cache.put(key, e);
		this.dirty = true;
		return e;
	}

	private static String key(final File f) {
		return f.getAbsolutePath();
	}

	private void load() {
		if (this.cacheFile == null || !this.cacheFile.isFile()) {
			return;
		}
		try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(this.cacheFile), UTF8))) {
			String line;
			while ((line = r.readLine()) != null && this.cache.size() < MAX_ENTRIES) {
				final String[] p = line.split("\t");
				if (p.length != 5) {
					continue;
				}
				try {
					final Entry e = new Entry();
					e.size = Long.parseLong(p[1]);
					e.modified = Long.parseLong(p[2]);
					e.sha256 = MapHash.fromHex(p[3]);
					e.crc32 = (int) Long.parseLong(p[4]);
					if (e.sha256 != null && e.sha256.length == 32) {
						this.cache.put(p[0], e);
					}
				}
				catch (final NumberFormatException ignored) {
					// a damaged line only costs one rehash
				}
			}
		}
		catch (final IOException e) {
			this.cache.clear();
		}
	}

	private void save() {
		if (!this.dirty || this.cacheFile == null) {
			return;
		}
		this.dirty = false;
		final File tmp = new File(this.cacheFile.getPath() + ".tmp");
		try {
			final File parent = this.cacheFile.getAbsoluteFile().getParentFile();
			if (parent != null) {
				parent.mkdirs();
			}
			try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), UTF8)) {
				for (final Map.Entry<String, Entry> me : this.cache.entrySet()) {
					final Entry e = me.getValue();
					if (me.getKey().startsWith(VIRTUAL_KEY) || new File(me.getKey()).exists()) {
						w.write(me.getKey() + "\t" + e.size + "\t" + e.modified + "\t" + MapHash.hex(e.sha256) + "\t"
								+ (e.crc32 & 0xFFFFFFFFL) + "\n");
					}
				}
			}
			Files.move(tmp.toPath(), this.cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		catch (final IOException e) {
			tmp.delete();
		}
	}
}
