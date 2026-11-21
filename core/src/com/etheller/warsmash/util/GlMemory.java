package com.etheller.warsmash.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntMap;

/**
 * Counts the memory the engine's GL objects (textures, buffers, renderbuffers) hold, as the sizes the GL calls give,
 * and who made them. The driver's own total (dumpsys meminfo, GL mtrack) also holds what these sizes do not show, so
 * the difference is the driver's padding and copies. The GL of the platform calls the hooks while the accounting is
 * on: "glmem" in the warsmash.perf file, or the warsmash.glmem system property.
 */
public final class GlMemory {
	public static boolean ON;

	private static final int ELEMENT_ARRAY_BUFFER = 0x8893;
	private static final int MAX_LEVELS = 16;
	private static final int OWNER_LINES = 14;

	private static final IntMap<Tex> TEXTURES = new IntMap<>();
	private static final IntMap<Buf> BUFFERS = new IntMap<>();
	private static final IntMap<Buf> RENDERBUFFERS = new IntMap<>();
	private static final IntIntMap TEXTURE_BOUND = new IntIntMap();
	private static final IntIntMap BUFFER_BOUND = new IntIntMap();
	private static final IntIntMap ELEMENT_OF_VAO = new IntIntMap();
	private static final Map<String, long[]> OWNERS = new HashMap<>();
	private static int unit;
	private static int vao;
	private static int renderbuffer;
	private static long textureBytes;
	private static long bufferBytes;
	private static long renderbufferBytes;
	private static int unknownFormats;

	private GlMemory() {
	}

	/** Turns the accounting on when the perf file or the property asks for it; needs the files of the application. */
	public static void configure() {
		boolean on = System.getProperty("warsmash.glmem") != null;
		try {
			if (!on && (Gdx.files != null) && Gdx.files.local("warsmash.perf").exists()) {
				on = Gdx.files.local("warsmash.perf").readString().contains("glmem");
			}
		}
		catch (final RuntimeException e) {
			// the file is only a switch; unreadable means off
		}
		ON = on;
	}

	/** A new context starts with no objects; what libGDX reloads into it is counted again. */
	public static void reset() {
		TEXTURES.clear();
		BUFFERS.clear();
		RENDERBUFFERS.clear();
		TEXTURE_BOUND.clear();
		BUFFER_BOUND.clear();
		ELEMENT_OF_VAO.clear();
		OWNERS.clear();
		unit = 0;
		vao = 0;
		renderbuffer = 0;
		textureBytes = 0;
		bufferBytes = 0;
		renderbufferBytes = 0;
	}

	private static final class Tex {
		final String owner;
		final long[] levels = new long[MAX_LEVELS];
		int width;
		int height;
		int depth = 1;
		int bytesPerPixel;
		int format;
		long total;

		Tex(final String owner) {
			this.owner = owner;
		}
	}

	private static final class Buf {
		final String owner;
		long bytes;

		Buf(final String owner) {
			this.owner = owner;
		}
	}

	public static void activeTexture(final int texture) {
		unit = texture - 0x84C0;
	}

	public static void bindTexture(final int target, final int texture) {
		TEXTURE_BOUND.put((unit << 16) | target, texture);
	}

	private static Tex boundTexture(final int target) {
		final int id = TEXTURE_BOUND.get((unit << 16) | target, 0);
		if (id == 0) {
			return null;
		}
		Tex tex = TEXTURES.get(id);
		if (tex == null) {
			tex = new Tex(owner('T', -1));
			TEXTURES.put(id, tex);
			ownerAdd(tex.owner, 0, 1);
		}
		return tex;
	}

	/** An uncompressed level of a texture; the depth is 1 for a 2D one. */
	public static void texImage(final int target, final int level, final int internalFormat, final int width,
			final int height, final int depth, final int format, final int type) {
		final Tex tex = boundTexture(target);
		if ((tex != null) && (level >= 0) && (level < MAX_LEVELS)) {
			final int bpp = bytesPerPixel(internalFormat, format, type);
			if (level == 0) {
				tex.width = width;
				tex.height = height;
				tex.depth = depth;
				tex.bytesPerPixel = bpp;
				tex.format = internalFormat;
			}
			setLevel(tex, level, (long) width * height * depth * bpp);
		}
	}

	public static void compressedTexImage(final int target, final int level, final int internalFormat, final int width,
			final int height, final int imageSize) {
		final Tex tex = boundTexture(target);
		if ((tex != null) && (level >= 0) && (level < MAX_LEVELS)) {
			if (level == 0) {
				tex.width = width;
				tex.height = height;
				tex.depth = 1;
				tex.bytesPerPixel = 0;
				tex.format = internalFormat;
			}
			setLevel(tex, level, imageSize);
		}
	}

	public static void generateMipmap(final int target) {
		final Tex tex = boundTexture(target);
		if ((tex == null) || (tex.width == 0) || (tex.bytesPerPixel == 0)) {
			return;
		}
		int w = tex.width;
		int h = tex.height;
		for (int level = 1; (level < MAX_LEVELS) && ((w > 1) || (h > 1)); level++) {
			w = Math.max(1, w >> 1);
			h = Math.max(1, h >> 1);
			setLevel(tex, level, (long) w * h * tex.depth * tex.bytesPerPixel);
		}
	}

	private static void setLevel(final Tex tex, final int level, final long bytes) {
		final long delta = bytes - tex.levels[level];
		tex.levels[level] = bytes;
		tex.total += delta;
		textureBytes += delta;
		ownerAdd(tex.owner, delta, 0);
	}

	public static void deleteTexture(final int texture) {
		final Tex tex = TEXTURES.remove(texture);
		if (tex != null) {
			textureBytes -= tex.total;
			ownerAdd(tex.owner, -tex.total, -1);
		}
	}

	public static void bindBuffer(final int target, final int buffer) {
		if (target == ELEMENT_ARRAY_BUFFER) {
			ELEMENT_OF_VAO.put(vao, buffer);
		}
		else {
			BUFFER_BOUND.put(target, buffer);
		}
	}

	public static void bindVertexArray(final int array) {
		vao = array;
	}

	public static void bufferData(final int target, final int size) {
		final int id = target == ELEMENT_ARRAY_BUFFER ? ELEMENT_OF_VAO.get(vao, 0) : BUFFER_BOUND.get(target, 0);
		if (id == 0) {
			return;
		}
		Buf buf = BUFFERS.get(id);
		if (buf == null) {
			buf = new Buf(owner('B', target));
			BUFFERS.put(id, buf);
			ownerAdd(buf.owner, 0, 1);
		}
		bufferBytes += size - buf.bytes;
		ownerAdd(buf.owner, size - buf.bytes, 0);
		buf.bytes = size;
	}

	public static void deleteBuffer(final int buffer) {
		final Buf buf = BUFFERS.remove(buffer);
		if (buf != null) {
			bufferBytes -= buf.bytes;
			ownerAdd(buf.owner, -buf.bytes, -1);
		}
	}

	public static void bindRenderbuffer(final int buffer) {
		renderbuffer = buffer;
	}

	public static void renderbufferStorage(final int format, final int width, final int height, final int samples) {
		if (renderbuffer == 0) {
			return;
		}
		Buf buf = RENDERBUFFERS.get(renderbuffer);
		if (buf == null) {
			buf = new Buf(owner('R', -1));
			RENDERBUFFERS.put(renderbuffer, buf);
			ownerAdd(buf.owner, 0, 1);
		}
		final long bytes = (long) width * height * Math.max(1, samples) * bytesPerPixel(format, format, 0);
		renderbufferBytes += bytes - buf.bytes;
		ownerAdd(buf.owner, bytes - buf.bytes, 0);
		buf.bytes = bytes;
	}

	public static void deleteRenderbuffer(final int buffer) {
		final Buf buf = RENDERBUFFERS.remove(buffer);
		if (buf != null) {
			renderbufferBytes -= buf.bytes;
			ownerAdd(buf.owner, -buf.bytes, -1);
		}
	}

	private static void ownerAdd(final String owner, final long delta, final int objects) {
		long[] sum = OWNERS.get(owner);
		if (sum == null) {
			sum = new long[2];
			OWNERS.put(owner, sum);
		}
		sum[0] += delta;
		sum[1] += objects;
	}

	/** Who asked for the object: the first two engine frames of the stack, with the libGDX class before them when there is one. */
	private static String owner(final char kind, final int target) {
		final StackTraceElement[] stack = new Throwable().getStackTrace();
		String gdx = null;
		String engine = "?";
		int found = 0;
		for (final StackTraceElement frame : stack) {
			final String name = frame.getClassName();
			if (name.startsWith("com.etheller.warsmash.util.GlMemory") || name.endsWith("ContextGL30")) {
				continue;
			}
			if (name.startsWith("com.etheller.warsmash.")) {
				final String at = name.substring(name.lastIndexOf('.') + 1) + "." + frame.getMethodName();
				engine = found == 0 ? at : engine + "<" + at;
				if (++found == 2) {
					break;
				}
			}
			else if ((gdx == null) && (found == 0) && name.startsWith("com.badlogic.gdx.")) {
				gdx = name.substring(name.lastIndexOf('.') + 1);
			}
		}
		return kind + ":" + (gdx != null ? gdx + "<" : "") + engine + (target >= 0 ? "@" + Integer.toHexString(target) : "");
	}

	private static int bytesPerPixel(final int internal, final int format, final int type) {
		switch (internal) {
		case 0x8058: case 0x8C43: case 0x8D7C: case 0x81A6: case 0x81A7: case 0x8CAC: case 0x88F0: case 0x822F:
		case 0x822E: case 0x8059: case 0x8D82:
			return 4;
		case 0x8051: case 0x8C41:
			return 3;
		case 0x8229: case 0x8D48: case 0x8D8F:
			return 1;
		case 0x822B: case 0x822D: case 0x8056: case 0x8057: case 0x8D62: case 0x81A5:
			return 2;
		case 0x881A: case 0x8D76: case 0x8230:
			return 8;
		case 0x8814:
			return 16;
		case 0x8815:
			return 12;
		default:
			break;
		}
		switch (format) {
		case 0x1908:
			return type == 0x1406 ? 16 : ((type == 0x8033) || (type == 0x8034)) ? 2 : 4;
		case 0x1907:
			return type == 0x8363 ? 2 : 3;
		case 0x1909:
		case 0x1906:
		case 0x1903:
			return 1;
		case 0x190A:
			return 2;
		case 0x1902:
			return type == 0x1405 ? 4 : 2;
		default:
			unknownFormats++;
			return 4;
		}
	}

	/** The totals for a perf line. */
	public static String summary() {
		return String.format(Locale.ROOT, "gl_acct_mb=%.0f gl_tex_mb=%.0f gl_buf_mb=%.0f gl_rb_mb=%.0f gl_objs=%d",
				(textureBytes + bufferBytes + renderbufferBytes) / 1048576.0, textureBytes / 1048576.0,
				bufferBytes / 1048576.0, renderbufferBytes / 1048576.0,
				TEXTURES.size + BUFFERS.size + RENDERBUFFERS.size);
	}

	/** How the live textures are laid out: levels per texture, formats and sizes, to compare with what the driver holds. */
	public static String shape() {
		final long[] byLevels = new long[MAX_LEVELS + 1];
		final int[] countByLevels = new int[MAX_LEVELS + 1];
		final long[] bySize = new long[6];
		final int[] countBySize = new int[6];
		final Map<Integer, long[]> byFormat = new HashMap<>();
		for (final IntMap.Entry<Tex> entry : TEXTURES) {
			final Tex tex = entry.value;
			int levels = 0;
			for (final long bytes : tex.levels) {
				if (bytes > 0) {
					levels++;
				}
			}
			byLevels[levels] += tex.total;
			countByLevels[levels]++;
			final int bucket = tex.total <= 16384 ? 0 : tex.total <= 65536 ? 1 : tex.total <= 262144 ? 2 : tex.total <= 1048576 ? 3 : tex.total <= 4194304 ? 4 : 5;
			bySize[bucket] += tex.total;
			countBySize[bucket]++;
			long[] sum = byFormat.get(tex.format);
			if (sum == null) {
				sum = new long[2];
				byFormat.put(tex.format, sum);
			}
			sum[0] += tex.total;
			sum[1]++;
		}
		final StringBuilder out = new StringBuilder("shape levels");
		for (int i = 0; i <= MAX_LEVELS; i++) {
			if (countByLevels[i] > 0) {
				out.append(String.format(Locale.ROOT, " %d=%.1fMB/%d", i, byLevels[i] / 1048576.0, countByLevels[i]));
			}
		}
		out.append(" size<=16K,64K,256K,1M,4M,more");
		for (int i = 0; i < 6; i++) {
			out.append(String.format(Locale.ROOT, " %.1fMB/%d", bySize[i] / 1048576.0, countBySize[i]));
		}
		out.append(" formats");
		for (final Map.Entry<Integer, long[]> entry : byFormat.entrySet()) {
			out.append(String.format(Locale.ROOT, " %x=%.1fMB/%d", entry.getKey(), entry.getValue()[0] / 1048576.0,
					entry.getValue()[1]));
		}
		return out.toString();
	}

	/** The biggest owners, largest first, for the log. */
	public static String owners() {
		final List<Map.Entry<String, long[]>> entries = new ArrayList<>(OWNERS.entrySet());
		entries.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
		final StringBuilder out = new StringBuilder("top");
		for (int i = 0; (i < entries.size()) && (i < OWNER_LINES); i++) {
			final long[] sum = entries.get(i).getValue();
			if (sum[0] <= 0) {
				break;
			}
			out.append(String.format(Locale.ROOT, " %s=%.1fMB/%d", entries.get(i).getKey(), sum[0] / 1048576.0, sum[1]));
		}
		if (unknownFormats > 0) {
			out.append(" unknown_formats=").append(unknownFormats);
		}
		return out.toString();
	}
}
