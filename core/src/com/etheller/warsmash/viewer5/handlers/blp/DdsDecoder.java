package com.etheller.warsmash.viewer5.handlers.blp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import com.etheller.warsmash.util.OffHeapBlock;

/**
 * Decodes a block compressed DDS image (DXT1, DXT3, DXT5, ATI2) into RGBA bytes, without depending on java.awt or
 * javax.imageio: the largest stored mip level within a size limit, and the smaller stored levels below it when asked.
 */
public final class DdsDecoder {
	private static final int MAGIC = 0x20534444; // "DDS "
	private static final int HEADER_SIZE = 128;
	private static final int DDSD_MIPMAPCOUNT = 0x20000;
	private static final int DDPF_FOURCC = 0x4;
	static final int FOURCC_DXT1 = fourCC("DXT1");
	static final int FOURCC_DXT3 = fourCC("DXT3");
	static final int FOURCC_DXT5 = fourCC("DXT5");
	private static final int FOURCC_ATI2 = fourCC("ATI2");

	private final MipLevels levels;

	private DdsDecoder(final MipLevels levels) {
		this.levels = levels;
	}

	public int getWidth() {
		return this.levels.getWidth(0);
	}

	public int getHeight() {
		return this.levels.getHeight(0);
	}

	/**
	 * @return direct buffer of width * height RGBA pixels, rows from the top
	 */
	public ByteBuffer getRgba() {
		return this.levels.getRgba(0);
	}

	/** The decoded levels, the largest first. */
	public MipLevels getLevels() {
		return this.levels;
	}

	public static boolean isDds(final ByteBuffer file) {
		final ByteBuffer data = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		return (data.remaining() >= HEADER_SIZE) && (data.getInt(0) == MAGIC);
	}

	/** Whether the file stores two channel (ATI2) normal map data. */
	public static boolean isNormalMap(final ByteBuffer file) {
		final ByteBuffer data = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		return (data.remaining() >= HEADER_SIZE) && (data.getInt(0) == MAGIC) && (data.getInt(84) == FOURCC_ATI2);
	}

	/** The top level only, at its full size. */
	public static DdsDecoder decode(final ByteBuffer file) throws IOException {
		return decode(file, 0, false);
	}

	/**
	 * @param maxSize the largest width and height to decode, 0 for no limit: the first stored level within it becomes
	 *                the top level; a file without such a level keeps its smallest one
	 * @param mips    whether the smaller stored levels are decoded too
	 */
	public static DdsDecoder decode(final ByteBuffer file, final int maxSize, final boolean mips) throws IOException {
		return decode(file, maxSize, mips, false);
	}

	/**
	 * As above; with offHeap the levels are decoded into blocks outside the Java heap, which the caller gives back with
	 * {@link MipLevels#free()}, and the file is read a row of blocks at a time, so it may be a direct buffer too.
	 */
	public static DdsDecoder decode(final ByteBuffer file, final int maxSize, final boolean mips,
			final boolean offHeap) throws IOException {
		final ByteBuffer data = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		final Layout layout = Layout.read(data, maxSize);
		final int width = layout.width;
		final int height = layout.height;
		final int fourCC = layout.fourCC;
		final int maxLevels = layout.levels;
		final long[] offsets = layout.offsets;
		final int first = layout.first;
		final int blockSize = layout.blockSize;
		long offset = offsets[first];
		final MipLevels levels = new MipLevels();
		for (int level = first; level < maxLevels; level++) {
			final int levelWidth = MipLevels.size(width, level);
			final int levelHeight = MipLevels.size(height, level);
			final long size = levelBytes(levelWidth, levelHeight, blockSize);
			if ((level > first) && ((offset + size) > data.limit())) {
				break;
			}
			final int available = (int) Math.max(0, Math.min(size, data.limit() - offset));
			if (offHeap) {
				final OffHeapBlock pixels = OffHeapBlock.allocate(levelWidth * levelHeight * 4);
				levels.add(levelWidth, levelHeight, pixels);
				try {
					decodeBlocks(data, (int) offset, available, levelWidth, levelHeight, fourCC, pixels.buffer());
				}
				catch (final IOException | RuntimeException e) {
					levels.free();
					throw e;
				}
			}
			else if (file.hasArray()) {
				levels.add(levelWidth, levelHeight, decodeBlocks(file.array(), file.arrayOffset() + (int) offset,
						available, levelWidth, levelHeight, fourCC));
			}
			else {
				final byte[] src = new byte[available];
				if (src.length > 0) {
					data.position((int) offset);
					data.get(src);
				}
				levels.add(levelWidth, levelHeight, decodeBlocks(src, levelWidth, levelHeight, fourCC));
			}
			offset += size;
			if (!mips) {
				break;
			}
		}
		return new DdsDecoder(levels);
	}

	/** The header of a block compressed file and where its stored levels lie. */
	private static final class Layout {
		int width;
		int height;
		int fourCC;
		int blockSize;
		/** How many levels the file holds, whole or not. */
		int levels;
		/** Where level n starts; the last entry is the end of the last level. */
		long[] offsets;
		/** The first level within the size limit that the file is long enough to hold. */
		int first;

		static Layout read(final ByteBuffer data, final int maxSize) throws IOException {
			if ((data.remaining() < HEADER_SIZE) || (data.getInt(0) != MAGIC)) {
				throw new IOException("Not a DDS file");
			}
			final Layout layout = new Layout();
			final int flags = data.getInt(8);
			layout.height = data.getInt(12);
			layout.width = data.getInt(16);
			final int storedLevels = (flags & DDSD_MIPMAPCOUNT) != 0 ? Math.max(1, data.getInt(28)) : 1;
			final int pixelFormatFlags = data.getInt(80);
			layout.fourCC = data.getInt(84);
			if ((pixelFormatFlags & DDPF_FOURCC) == 0) {
				throw new IOException("Unsupported uncompressed DDS");
			}
			if ((layout.width <= 0) || (layout.height <= 0) || (((long) layout.width * layout.height) > (1 << 26))) {
				throw new IOException("Bad DDS dimensions " + layout.width + "x" + layout.height);
			}
			final int fourCC = layout.fourCC;
			if ((fourCC != FOURCC_DXT1) && (fourCC != FOURCC_DXT3) && (fourCC != FOURCC_DXT5)
					&& (fourCC != FOURCC_ATI2)) {
				throw new IOException("Unsupported DDS format: " + Integer.toHexString(fourCC));
			}
			layout.blockSize = fourCC == FOURCC_DXT1 ? 8 : 16;
			// A stored chain may stop early, or claim levels the file is too short to hold; only whole levels count.
			layout.levels = Math.min(storedLevels, MipLevels.levelCount(layout.width, layout.height));
			layout.offsets = new long[layout.levels + 1];
			layout.offsets[0] = HEADER_SIZE;
			for (int level = 0; level < layout.levels; level++) {
				layout.offsets[level + 1] = layout.offsets[level] + levelBytes(MipLevels.size(layout.width, level),
						MipLevels.size(layout.height, level), layout.blockSize);
			}
			int first = MipLevels.firstWithin(layout.width, layout.height, maxSize, layout.levels);
			while ((first > 0) && (layout.offsets[first + 1] > data.limit())) {
				first--;
			}
			layout.first = first;
			return layout;
		}
	}

	/**
	 * The levels as the GPU reads them, without decoding: views of the file's bytes, which the result keeps (and frees
	 * with {@link CompressedMips#free()}) once it is returned; on null the caller still owns the file. The file must be
	 * in a block of its own, since the two channel format has its halves of a block swapped in place.
	 *
	 * @param maxSize as for {@link #decode(ByteBuffer, int, boolean)}
	 * @return null when the file is not one of these formats or its top level is cut short, which a decoder pads
	 */
	public static CompressedMips blocks(final OffHeapBlock file, final int maxSize) {
		final ByteBuffer data = file.buffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);
		final Layout layout;
		try {
			layout = Layout.read(data, maxSize);
		}
		catch (final IOException e) {
			return null;
		}
		if (layout.offsets[layout.first + 1] > data.limit()) {
			return null;
		}
		final int fourCC = layout.fourCC;
		final CompressedMips levels = new CompressedMips(fourCC == FOURCC_DXT1 ? CompressedMips.FORMAT_RGBA_S3TC_DXT1
				: fourCC == FOURCC_DXT3 ? CompressedMips.FORMAT_RGBA_S3TC_DXT3
						: fourCC == FOURCC_DXT5 ? CompressedMips.FORMAT_RGBA_S3TC_DXT5 : CompressedMips.FORMAT_RG_RGTC2);
		for (int level = layout.first; level < layout.levels; level++) {
			final int start = (int) layout.offsets[level];
			final int end = (int) layout.offsets[level + 1];
			if (end > data.limit()) {
				break;
			}
			final ByteBuffer view = data.duplicate().order(ByteOrder.nativeOrder());
			view.limit(end).position(start);
			final ByteBuffer slice = view.slice().order(ByteOrder.nativeOrder());
			if (fourCC == FOURCC_ATI2) {
				// Unlike BC5, the legacy ATI2 code stores the green block first.
				for (int at = 0; at < slice.capacity(); at += 16) {
					final long green = slice.getLong(at);
					slice.putLong(at, slice.getLong(at + 8));
					slice.putLong(at + 8, green);
				}
			}
			levels.add(MipLevels.size(layout.width, level), MipLevels.size(layout.height, level), slice);
		}
		levels.keep(file);
		return levels;
	}

	private static long levelBytes(final int width, final int height, final int blockSize) {
		return (long) Math.max(1, (width + 3) / 4) * Math.max(1, (height + 3) / 4) * blockSize;
	}

	/**
	 * @param data the blocks of one image, left to right and top to bottom; missing
	 *             trailing blocks decode as zeros
	 * @return width * height RGBA pixels, rows from the top
	 */
	static byte[] decodeBlocks(final byte[] data, final int width, final int height, final int fourCC)
			throws IOException {
		return decodeBlocks(data, 0, data.length, width, height, fourCC);
	}

	/** As above for the length bytes of data from offset, which spares a copy of them. */
	static byte[] decodeBlocks(final byte[] data, final int start, final int length, final int width,
			final int height, final int fourCC) throws IOException {
		final int blocksWide = Math.max(1, (width + 3) / 4);
		final int blocksHigh = Math.max(1, (height + 3) / 4);
		final int blockSize = fourCC == FOURCC_DXT1 ? 8 : 16;
		final int needed = blocksWide * blocksHigh * blockSize;
		final byte[] src;
		final int base;
		if (length >= needed) {
			src = data;
			base = start;
		}
		else {
			src = new byte[needed];
			System.arraycopy(data, start, src, 0, length);
			base = 0;
		}
		final byte[] pixels = new byte[width * height * 4];
		final BlockScratch scratch = new BlockScratch();
		int offset = base;
		for (int blockY = 0; blockY < blocksHigh; blockY++) {
			for (int blockX = 0; blockX < blocksWide; blockX++) {
				decodeBlock(src, offset, fourCC, scratch);
				offset += blockSize;
				writeBlock(pixels, scratch.block, blockX * 4, blockY * 4, width, height);
			}
		}
		return pixels;
	}

	/**
	 * As above into out, width * height RGBA pixels from position 0. The blocks are read and the pixels written a row
	 * of blocks at a time, through arrays of that size.
	 */
	static void decodeBlocks(final ByteBuffer data, final int start, final int length, final int width,
			final int height, final int fourCC, final ByteBuffer out) throws IOException {
		final int blocksWide = Math.max(1, (width + 3) / 4);
		final int blocksHigh = Math.max(1, (height + 3) / 4);
		final int blockSize = fourCC == FOURCC_DXT1 ? 8 : 16;
		final int rowBytes = blocksWide * blockSize;
		final byte[] row = new byte[rowBytes];
		final byte[] strip = new byte[width * 4 * 4];
		final BlockScratch scratch = new BlockScratch();
		final ByteBuffer source = data.duplicate();
		final ByteBuffer target = out.duplicate();
		target.clear();
		for (int blockY = 0; blockY < blocksHigh; blockY++) {
			final int rowStart = blockY * rowBytes;
			final int stored = Math.max(0, Math.min(rowBytes, length - rowStart));
			if (stored > 0) {
				source.position(start + rowStart);
				source.get(row, 0, stored);
			}
			if (stored < rowBytes) {
				Arrays.fill(row, stored, rowBytes, (byte) 0);
			}
			final int stripRows = Math.min(4, height - (blockY * 4));
			for (int blockX = 0; blockX < blocksWide; blockX++) {
				decodeBlock(row, blockX * blockSize, fourCC, scratch);
				writeBlock(strip, scratch.block, blockX * 4, 0, width, stripRows);
			}
			target.position(blockY * 4 * width * 4);
			target.put(strip, 0, stripRows * width * 4);
		}
		out.clear();
	}

	/** The arrays one block is decoded through. */
	private static final class BlockScratch {
		final byte[] block = new byte[16 * 4];
		final byte[] channelA = new byte[16];
		final byte[] channelB = new byte[16];
		final int[] palette = new int[16];
		final int[] values = new int[8];
	}

	/** Decodes the block at offset into scratch.block, 16 RGBA pixels in rows of four. */
	private static void decodeBlock(final byte[] src, final int offset, final int fourCC, final BlockScratch scratch)
			throws IOException {
		final byte[] block = scratch.block;
		final byte[] channelA = scratch.channelA;
		final byte[] channelB = scratch.channelB;
		final int[] palette = scratch.palette;
		final int[] values = scratch.values;
		if (fourCC == FOURCC_DXT1) {
			decodeColorBlock(src, offset, block, palette, true);
		}
		else if (fourCC == FOURCC_DXT3) {
			decodeColorBlock(src, offset + 8, block, palette, false);
			for (int i = 0; i < 16; i++) {
				final int alpha = ((src[offset + (i >> 1)] & 0xFF) >>> ((i & 1) * 4)) & 0xF;
				block[(i * 4) + 3] = (byte) ((alpha << 4) | alpha);
			}
		}
		else if (fourCC == FOURCC_DXT5) {
			decodeInterpolatedBlock(src, offset, channelA, values);
			decodeColorBlock(src, offset + 8, block, palette, false);
			for (int i = 0; i < 16; i++) {
				block[(i * 4) + 3] = channelA[i];
			}
		}
		else if (fourCC == FOURCC_ATI2) {
			// Unlike BC5, the legacy ATI2 code stores the green block first.
			decodeInterpolatedBlock(src, offset, channelB, values);
			decodeInterpolatedBlock(src, offset + 8, channelA, values);
			for (int i = 0; i < 16; i++) {
				block[i * 4] = channelA[i];
				block[(i * 4) + 1] = channelB[i];
				block[(i * 4) + 2] = 0;
				block[(i * 4) + 3] = (byte) 0xFF;
			}
		}
		else {
			throw new IOException("Unsupported DDS format: " + Integer.toHexString(fourCC));
		}
	}

	private static void decodeColorBlock(final byte[] src, final int offset, final byte[] out, final int[] palette,
			final boolean dxt1) {
		final int color0 = (src[offset] & 0xFF) | ((src[offset + 1] & 0xFF) << 8);
		final int color1 = (src[offset + 2] & 0xFF) | ((src[offset + 3] & 0xFF) << 8);
		final int indices = (src[offset + 4] & 0xFF) | ((src[offset + 5] & 0xFF) << 8)
				| ((src[offset + 6] & 0xFF) << 16) | ((src[offset + 7] & 0xFF) << 24);
		expand565(color0, palette, 0);
		expand565(color1, palette, 4);
		final boolean fourColors = !dxt1 || (color0 > color1);
		for (int channel = 0; channel < 3; channel++) {
			final int c0 = palette[channel];
			final int c1 = palette[4 + channel];
			if (fourColors) {
				palette[8 + channel] = ((2 * c0) + c1) / 3;
				palette[12 + channel] = (c0 + (2 * c1)) / 3;
			}
			else {
				palette[8 + channel] = (c0 + c1) / 2;
				palette[12 + channel] = 0;
			}
		}
		palette[3] = 255;
		palette[7] = 255;
		palette[11] = 255;
		palette[15] = fourColors ? 255 : 0;
		for (int i = 0; i < 16; i++) {
			final int index = ((indices >>> (2 * i)) & 0x3) * 4;
			out[i * 4] = (byte) palette[index];
			out[(i * 4) + 1] = (byte) palette[index + 1];
			out[(i * 4) + 2] = (byte) palette[index + 2];
			out[(i * 4) + 3] = (byte) palette[index + 3];
		}
	}

	private static void decodeInterpolatedBlock(final byte[] src, final int offset, final byte[] out,
			final int[] values) {
		final int value0 = src[offset] & 0xFF;
		final int value1 = src[offset + 1] & 0xFF;
		long indices = 0;
		for (int i = 0; i < 6; i++) {
			indices |= (long) (src[offset + 2 + i] & 0xFF) << (8 * i);
		}
		values[0] = value0;
		values[1] = value1;
		if (value0 > value1) {
			for (int i = 1; i < 7; i++) {
				values[i + 1] = (((7 - i) * value0) + (i * value1)) / 7;
			}
		}
		else {
			for (int i = 1; i < 5; i++) {
				values[i + 1] = (((5 - i) * value0) + (i * value1)) / 5;
			}
			values[6] = 0;
			values[7] = 255;
		}
		for (int i = 0; i < 16; i++) {
			out[i] = (byte) values[(int) ((indices >>> (3 * i)) & 0x7)];
		}
	}

	private static void expand565(final int color, final int[] out, final int offset) {
		final int red = (color >>> 11) & 0x1F;
		final int green = (color >>> 5) & 0x3F;
		final int blue = color & 0x1F;
		out[offset] = (red << 3) | (red >>> 2);
		out[offset + 1] = (green << 2) | (green >>> 4);
		out[offset + 2] = (blue << 3) | (blue >>> 2);
	}

	private static void writeBlock(final byte[] pixels, final byte[] block, final int x0, final int y0,
			final int width, final int height) {
		final int columns = Math.min(4, width - x0) * 4;
		for (int y = 0; y < 4; y++) {
			final int pixelY = y0 + y;
			if (pixelY >= height) {
				break;
			}
			System.arraycopy(block, y * 16, pixels, ((pixelY * width) + x0) * 4, columns);
		}
	}

	private static int fourCC(final String code) {
		return code.charAt(0) | (code.charAt(1) << 8) | (code.charAt(2) << 16) | (code.charAt(3) << 24);
	}
}
