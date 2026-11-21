package com.etheller.warsmash.viewer5.handlers.blp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Decoding into blocks outside the Java heap reads the file a row of blocks at a time; the pixels are the same as
 * those decoded into arrays, for every block format, sizes that are not whole blocks, a file cut short and a file in
 * a direct buffer.
 */
class DdsOffHeapDecodeTest {
	private static final String[] FORMATS = { "DXT1", "DXT3", "DXT5", "ATI2" };

	@Test
	void offHeapLevelsMatchArrayLevels() throws IOException {
		for (final String format : FORMATS) {
			for (final int[] size : new int[][] { { 64, 32 }, { 13, 7 }, { 1, 1 }, { 6, 20 } }) {
				final ByteBuffer file = dds(format, size[0], size[1], Integer.MAX_VALUE);
				assertSame(DdsDecoder.decode(file, 0, true).getLevels(), DdsDecoder.decode(file, 0, true, true)
						.getLevels());
				assertSame(DdsDecoder.decode(file, 8, true).getLevels(), DdsDecoder.decode(file, 8, true, true)
						.getLevels());
				assertSame(DdsDecoder.decode(file, 0, true).getLevels(), DdsDecoder.decode(direct(file), 0, true, true)
						.getLevels());
			}
		}
	}

	@Test
	void aTopLevelCutShortDecodesItsMissingBlocksAsZeros() throws IOException {
		final ByteBuffer file = dds("DXT5", 32, 32, 128 + 100);
		assertSame(DdsDecoder.decode(file, 0, true).getLevels(), DdsDecoder.decode(file, 0, true, true).getLevels());
	}

	private static void assertSame(final MipLevels expected, final MipLevels actual) {
		assertEquals(expected.count(), actual.count());
		for (int level = 0; level < expected.count(); level++) {
			assertEquals(expected.getWidth(level), actual.getWidth(level));
			assertEquals(expected.getHeight(level), actual.getHeight(level));
			assertArrayEquals(expected.getArray(level), actual.getArray(level), "level " + level);
		}
		actual.free();
	}

	private static ByteBuffer direct(final ByteBuffer file) {
		final ByteBuffer copy = ByteBuffer.allocateDirect(file.remaining());
		copy.put(file.duplicate()).flip();
		return copy;
	}

	/** A DDS file with a full chain of random blocks, cut at length bytes. */
	private static ByteBuffer dds(final String format, final int width, final int height, final int length) {
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
		final ByteBuffer file = ByteBuffer.allocate(Math.min(bytes, length)).order(ByteOrder.LITTLE_ENDIAN);
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
		file.put(content, 0, file.capacity()).flip();
		return file;
	}
}
