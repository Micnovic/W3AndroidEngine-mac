package com.etheller.warsmash.viewer5.handlers.blp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

import com.badlogic.gdx.Gdx;
import com.etheller.warsmash.util.Log;

/**
 * Turns the decoded textures of the HD and DE graphics into ETC2 blocks, which every OpenGL ES 3.0 GPU reads
 * directly: a texture of four bytes a pixel takes a quarter (opaque) or half (with alpha) of the memory it did.
 * Encoding costs CPU time, so every result is kept in a cache folder, keyed by the texture's path, the file's size
 * and checksum, the size limit and the cache version, and the next run reads the blocks from there.
 */
public final class TextureTranscoder {
	/** Raise whenever the encoder or the file layout changes, which makes every old cache file unused. */
	static final int VERSION = 1;
	/** Smaller textures stay as they are: their memory is small and block artifacts show most on them. */
	static final int MIN_SIZE = 128;
	/** The cache folder holds at most this many bytes; the files used longest ago go first. */
	private static final long MAX_CACHE_BYTES = 3L << 30;
	private static final int MAGIC = 0x58545357; // "WSTX"
	private static final String PROPERTY_FOLDER = "warsmash.textureCache";

	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "texture-cache-writer");
		thread.setDaemon(true);
		return thread;
	});
	private static final long MAX_PENDING_WRITE_BYTES = 8L << 20;
	private static final AtomicLong PENDING_WRITE_BYTES = new AtomicLong();
	private static File folder;
	private static boolean folderTried;

	private TextureTranscoder() {
	}

	/** Decodes a texture file to RGBA levels. */
	public interface Decoder {
		MipLevels decode() throws IOException;
	}

	/**
	 * The texture as blocks, from the cache or encoded now (and cached), or null when it stays as plain pixels.
	 *
	 * @param path    the path of the texture in the game data
	 * @param file    the bytes of the DDS or BLP file
	 * @param limit   the texture size limit, 0 for none
	 * @param decoder decodes the file to RGBA when the cache lacks it
	 * @param normals whether the image is a two channel normal map, which is stored as EAC RG11
	 */
	public static CompressedMips transcode(final String path, final byte[] file, final int limit,
			final Decoder decoder, final boolean normals) {
		try {
			final File cacheFile = cacheFile(path, file, limit);
			if ((cacheFile != null) && cacheFile.isFile()) {
				final CompressedMips cached = read(cacheFile);
				if (cached != null) {
					return cached;
				}
			}
			final MipLevels levels = decoder.decode();
			if (Math.max(levels.getWidth(0), levels.getHeight(0)) < MIN_SIZE) {
				return null;
			}
			final CompressedMips blocks = encode(levels, normals);
			if (cacheFile != null) {
				queueWrite(cacheFile, blocks);
			}
			return blocks;
		}
		catch (final IOException | RuntimeException | OutOfMemoryError e) {
			Log.warn("Texture left uncompressed: " + path + " (" + e + ")");
			return null;
		}
	}

	/**
	 * The queued blocks stay on the Java heap until they are written, and a map loads textures faster than a phone's
	 * storage takes them, so past a limit the loading thread writes the file itself.
	 */
	private static void queueWrite(final File cacheFile, final CompressedMips blocks) {
		final long bytes = blocks.byteCount();
		if (PENDING_WRITE_BYTES.addAndGet(bytes) > MAX_PENDING_WRITE_BYTES) {
			try {
				write(cacheFile, blocks);
			}
			finally {
				PENDING_WRITE_BYTES.addAndGet(-bytes);
			}
			return;
		}
		WRITER.execute(() -> {
			try {
				write(cacheFile, blocks);
			}
			finally {
				PENDING_WRITE_BYTES.addAndGet(-bytes);
			}
		});
	}

	static CompressedMips encode(final MipLevels levels, final boolean normals) {
		final byte[] top = levels.getArray(0);
		final int format = normals ? CompressedMips.FORMAT_RG11_EAC
				: hasAlpha(top) ? CompressedMips.FORMAT_RGBA8_ETC2_EAC : CompressedMips.FORMAT_RGB8_ETC2;
		final CompressedMips out = new CompressedMips(format);
		final int count = levels.count();
		final byte[][] pixels = new byte[count][];
		final int[] widths = new int[count];
		final int[] heights = new int[count];
		for (int level = 0; level < count; level++) {
			widths[level] = levels.getWidth(level);
			heights[level] = levels.getHeight(level);
			pixels[level] = level == 0 ? top : levels.getArray(level);
		}
		final int kind = format == CompressedMips.FORMAT_RG11_EAC ? Etc2Encoder.KIND_RG
				: format == CompressedMips.FORMAT_RGBA8_ETC2_EAC ? Etc2Encoder.KIND_RGBA : Etc2Encoder.KIND_RGB;
		final byte[][] blocks = Etc2Encoder.encodeLevels(pixels, widths, heights, kind == Etc2Encoder.KIND_RGB ? 8 : 16,
				kind);
		for (int level = 0; level < count; level++) {
			out.add(widths[level], heights[level], blocks[level]);
		}
		return out;
	}

	private static boolean hasAlpha(final byte[] pixels) {
		for (int at = 3; at < pixels.length; at += 4) {
			if (pixels[at] != (byte) 0xFF) {
				return true;
			}
		}
		return false;
	}

	/** The folder the decoded textures are kept in, which also holds what other loaders learn; null where there is none. */
	public static File cacheFolder() {
		return folder();
	}

	private static synchronized File folder() {
		if (!folderTried) {
			folderTried = true;
			final String property = System.getProperty(PROPERTY_FOLDER);
			if ((property != null) && property.equalsIgnoreCase("off")) {
				return null;
			}
			try {
				final File dir = (property != null) && !property.isEmpty() ? new File(property)
						: Gdx.files.local("texture-cache").file();
				if (dir.isDirectory() || dir.mkdirs()) {
					folder = dir;
					WRITER.execute(() -> trim(dir));
				}
			}
			catch (final RuntimeException e) {
				Log.warn("No texture cache folder: " + e);
			}
		}
		return folder;
	}

	private static File cacheFile(final String path, final byte[] file, final int limit) {
		final File dir = folder();
		if (dir == null) {
			return null;
		}
		final CRC32 crc = new CRC32();
		crc.update(file, 0, file.length);
		final String key = VERSION + "|" + path.toLowerCase(Locale.ROOT).replace('/', '\\') + "|" + file.length + "|"
				+ crc.getValue() + "|" + limit;
		try {
			final byte[] hash = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
			final StringBuilder name = new StringBuilder();
			for (final byte b : hash) {
				name.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
			}
			return new File(new File(dir, name.substring(0, 2)), name.substring(2) + ".tx");
		}
		catch (final NoSuchAlgorithmException e) {
			return null;
		}
	}

	private static CompressedMips read(final File cacheFile) {
		try (DataInputStream in = new DataInputStream(
				new BufferedInputStream(new FileInputStream(cacheFile), 1 << 16))) {
			if ((in.readInt() != MAGIC) || (in.readInt() != VERSION)) {
				throw new IOException("Old cache file");
			}
			final CompressedMips levels = CompressedMips.read(in);
			cacheFile.setLastModified(System.currentTimeMillis());
			return levels;
		}
		catch (final IOException | RuntimeException e) {
			cacheFile.delete();
			return null;
		}
	}

	private static void write(final File cacheFile, final CompressedMips blocks) {
		final File temp = new File(cacheFile.getParentFile(), cacheFile.getName() + ".tmp");
		try {
			cacheFile.getParentFile().mkdirs();
			try (DataOutputStream out = new DataOutputStream(
					new BufferedOutputStream(new FileOutputStream(temp), 1 << 16))) {
				out.writeInt(MAGIC);
				out.writeInt(VERSION);
				blocks.write(out);
			}
			if (!temp.renameTo(cacheFile)) {
				temp.delete();
			}
		}
		catch (final IOException e) {
			temp.delete();
		}
	}

	/** Deletes the least recently used cache files while the folder is over its limit. */
	private static void trim(final File dir) {
		final File[] subfolders = dir.listFiles();
		if (subfolders == null) {
			return;
		}
		long total = 0;
		final List<File> files = new ArrayList<>();
		for (final File subfolder : subfolders) {
			final File[] children = subfolder.listFiles();
			if (children == null) {
				continue;
			}
			for (final File child : children) {
				total += child.length();
				files.add(child);
			}
		}
		if (total <= MAX_CACHE_BYTES) {
			return;
		}
		final File[] sorted = files.toArray(new File[0]);
		final long[] times = new long[sorted.length];
		final Integer[] order = new Integer[sorted.length];
		for (int i = 0; i < sorted.length; i++) {
			times[i] = sorted[i].lastModified();
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> Long.compare(times[a], times[b]));
		for (int i = 0; (i < order.length) && (total > ((MAX_CACHE_BYTES / 10) * 9)); i++) {
			final File file = sorted[order[i]];
			total -= file.length();
			file.delete();
		}
	}
}
