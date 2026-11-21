package com.etheller.warsmash.viewer5.handlers.blp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.OffHeapBlock;

/**
 * Uploading a DDS file as it is stored keeps the levels a decoder would have made, with the bytes of the file, except
 * that the two channel format has the halves of its blocks swapped.
 */
class DdsBlocksTest {
	private static final String[] FORMATS = { "DXT1", "DXT3", "DXT5", "ATI2" };
	private static final int[] CODES = { CompressedMips.FORMAT_RGBA_S3TC_DXT1, CompressedMips.FORMAT_RGBA_S3TC_DXT3,
			CompressedMips.FORMAT_RGBA_S3TC_DXT5, CompressedMips.FORMAT_RG_RGTC2 };

	@Test
	void levelsMatchTheDecodedOnes() throws IOException {
		for (int f = 0; f < FORMATS.length; f++) {
			for (final int[] size : new int[][] { { 64, 32 }, { 13, 7 }, { 1, 1 }, { 6, 20 } }) {
				for (final int limit : new int[] { 0, 8, 16 }) {
					final byte[] file = dds(FORMATS[f], size[0], size[1], Integer.MAX_VALUE);
					final MipLevels decoded = DdsDecoder.decode(ByteBuffer.wrap(file), limit, true).getLevels();
					final CompressedMips blocks = DdsDecoder.blocks(OffHeapBlock.copyOf(file), limit);
					assertNotNull(blocks);
					assertEquals(CODES[f], blocks.getFormat());
					assertEquals(decoded.count(), blocks.count());
					for (int level = 0; level < decoded.count(); level++) {
						assertEquals(decoded.getWidth(level), blocks.getWidth(level));
						assertEquals(decoded.getHeight(level), blocks.getHeight(level));
						final int expected = Math.max(1, (decoded.getWidth(level) + 3) / 4)
								* Math.max(1, (decoded.getHeight(level) + 3) / 4) * CompressedMips.blockBytes(CODES[f]);
						assertEquals(expected, blocks.getView(level).remaining());
					}
					blocks.free();
				}
			}
		}
	}

	@Test
	void theTwoChannelFormatHasItsHalvesSwapped() {
		final byte[] file = dds("ATI2", 4, 4, Integer.MAX_VALUE);
		final CompressedMips blocks = DdsDecoder.blocks(OffHeapBlock.copyOf(file), 0);
		final ByteBuffer top = blocks.getView(0);
		for (int i = 0; i < 8; i++) {
			assertEquals(file[128 + 8 + i], top.get(i));
			assertEquals(file[128 + i], top.get(8 + i));
		}
		blocks.free();
	}

	@Test
	void theOtherFormatsKeepTheFilesBytes() {
		final byte[] file = dds("DXT5", 8, 8, Integer.MAX_VALUE);
		final CompressedMips blocks = DdsDecoder.blocks(OffHeapBlock.copyOf(file), 0);
		final ByteBuffer top = blocks.getView(0);
		for (int i = 0; i < top.remaining(); i++) {
			assertEquals(file[128 + i], top.get(i));
		}
		blocks.free();
	}

	@Test
	void aTopLevelCutShortIsLeftToTheDecoder() {
		assertNull(DdsDecoder.blocks(OffHeapBlock.copyOf(dds("DXT5", 32, 32, 128 + 100)), 0));
	}

	/** A DDS file with a full chain of random blocks, cut at length bytes. */
	private static byte[] dds(final String format, final int width, final int height, final int length) {
		final int blockSize = format.equals("DXT1") ? 8 : 16;
		int levels = 1;
		for (int size = Math.max(width, height); size > 1; size >>= 1) {
			levels++;
		}
		int bytes = 128;
		for (int level = 0; level < levels; level++) {
			bytes += Math.max(1, (Math.max(1, width >> level) + 3) / 4) * Math.max(1, (Math.max(1, height >> level) + 3) / 4)
					* blockSize;
		}
		final byte[] content = new byte[bytes];
		new Random(width * 31 + height + format.hashCode()).nextBytes(content);
		final ByteBuffer header = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
		header.putInt(0, 0x20534444);
		header.putInt(8, 0x20000);
		header.putInt(12, height);
		header.putInt(16, width);
		header.putInt(28, levels);
		header.putInt(80, 0x4);
		header.putInt(84, format.charAt(0) | (format.charAt(1) << 8) | (format.charAt(2) << 16) | (format.charAt(3) << 24));
		return java.util.Arrays.copyOf(content, Math.min(bytes, length));
	}
}
