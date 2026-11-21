package com.etheller.warsmash.viewer5.handlers.blp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

/**
 * A texture size limit reads the first stored mip level within it, and the levels below it come along for the GPU.
 * Every level of these test images has a color of its own, so the color tells which stored level was read.
 */
class MipSelectionTest {
	/** RGB565 of red, green, blue, white and black: the colors of the levels of a 16 pixel image. */
	private static final int[] LEVEL_565 = { 0xF800, 0x07E0, 0x001F, 0xFFFF, 0x0000 };
	/** The same colors as BGRA. */
	private static final int[][] LEVEL_BGRA = { { 0, 0, 255 }, { 0, 255, 0 }, { 255, 0, 0 }, { 255, 255, 255 },
		{ 0, 0, 0 } };

	@Test
	void ddsWithoutLimitReadsTheTopLevel() throws IOException {
		final DdsDecoder image = DdsDecoder.decode(dds(16, 5, true, Integer.MAX_VALUE));
		assertEquals(16, image.getWidth());
		assertEquals(1, image.getLevels().count());
		assertColor(image.getLevels(), 0, 255, 0, 0);
	}

	@Test
	void ddsLimitReadsTheFirstLevelWithinIt() throws IOException {
		final MipLevels levels = DdsDecoder.decode(dds(16, 5, true, Integer.MAX_VALUE), 4, true).getLevels();
		assertEquals(3, levels.count());
		assertEquals(4, levels.getWidth(0));
		assertEquals(1, levels.getWidth(2));
		assertColor(levels, 0, 0, 0, 255);
		assertColor(levels, 1, 255, 255, 255);
		assertColor(levels, 2, 0, 0, 0);
	}

	@Test
	void ddsLevelsPastTheEndOfTheFileAreNotRead() throws IOException {
		// Only the 16 and 8 pixel levels are in the file.
		final ByteBuffer file = dds(16, 5, true, 128 + (16 * 8) + (4 * 8));
		final MipLevels levels = DdsDecoder.decode(file, 4, true).getLevels();
		assertEquals(8, levels.getWidth(0));
		assertEquals(1, levels.count());
		assertColor(levels, 0, 0, 255, 0);
	}

	@Test
	void ddsWithoutStoredLevelsKeepsItsOnlyLevel() throws IOException {
		final MipLevels levels = DdsDecoder.decode(dds(16, 5, false, Integer.MAX_VALUE), 4, true).getLevels();
		assertEquals(1, levels.count());
		assertEquals(16, levels.getWidth(0));
	}

	@Test
	void blpLimitReadsTheFirstLevelWithinIt() throws IOException {
		final MipLevels levels = BlpDecoder.decode(blp(16, 5), 8, true).getLevels();
		assertEquals(4, levels.count());
		assertEquals(8, levels.getWidth(0));
		assertColor(levels, 0, 0, 255, 0);
		assertColor(levels, 3, 0, 0, 0);
		assertEquals(16, BlpDecoder.decode(blp(16, 5), 0, false).getWidth());
	}

	@Test
	void blpChainThatStopsEarlyEndsThere() throws IOException {
		// Two stored levels: a 1 pixel limit falls back to the smallest one there is.
		final MipLevels levels = BlpDecoder.decode(blp(16, 2), 1, true).getLevels();
		assertEquals(1, levels.count());
		assertEquals(8, levels.getWidth(0));
	}

	@Test
	void skippedLevelsDropTheLargestOfTexturesTheLimitLeavesWhole() {
		// 512 within a 1024 limit: one level skipped.
		assertEquals(1, MipLevels.firstWithin(512, 512, MipLevels.sizeRule(1024, 1), 10));
		assertEquals(1, MipLevels.firstWithin(512, 256, MipLevels.sizeRule(0, 1), 10));
		// 2048 lowered to 512 by the limit is not lowered again.
		assertEquals(2, MipLevels.firstWithin(2048, 2048, MipLevels.sizeRule(512, 1), 12));
		// Never below 128 pixels: a 256 texture goes to 128, a 128 one stays.
		assertEquals(1, MipLevels.firstWithin(256, 64, MipLevels.sizeRule(0, 1), 9));
		assertEquals(0, MipLevels.firstWithin(128, 128, MipLevels.sizeRule(0, 1), 8));
		assertEquals(3, MipLevels.firstWithin(1024, 1024, MipLevels.sizeRule(0, 3), 11));
		assertEquals(2, MipLevels.firstWithin(512, 512, MipLevels.sizeRule(0, 3), 10));
		// A file without smaller levels keeps its only one.
		assertEquals(0, MipLevels.firstWithin(512, 512, MipLevels.sizeRule(0, 1), 1));
		// No skipping: the plain limit.
		assertEquals(MipLevels.firstWithin(2048, 1024, 512, 12), MipLevels.firstWithin(2048, 1024,
				MipLevels.sizeRule(512, 0), 12));
		assertEquals(512, MipLevels.sizeRule(512, 0));
	}

	@Test
	void smallTexturesKeepTheirLevelsWhateverIsSkipped() throws IOException {
		assertEquals(16, BlpDecoder.decode(blp(16, 5), MipLevels.sizeRule(0, 1), true).getLevels().getWidth(0));
		assertEquals(16, DdsDecoder.decode(dds(16, 5, true, Integer.MAX_VALUE), MipLevels.sizeRule(0, 2), true)
				.getLevels().getWidth(0));
	}

	@Test
	void firstLevelWithinALimit() {
		assertEquals(0, MipLevels.firstWithin(2048, 1024, 0, 12));
		assertEquals(1, MipLevels.firstWithin(2048, 1024, 1024, 12));
		assertEquals(2, MipLevels.firstWithin(2048, 1024, 512, 12));
		assertEquals(0, MipLevels.firstWithin(512, 512, 1024, 10));
		assertEquals(2, MipLevels.firstWithin(2048, 2048, 64, 3));
		assertEquals(12, MipLevels.levelCount(2048, 1024));
	}

	@Test
	void arrayBackedAndOtherBuffersDecodeAlike() throws IOException {
		for (final int length : new int[] { Integer.MAX_VALUE, 128 + 128 + 17, 128 + 50 }) {
			final ByteBuffer file = dds(16, 5, true, length);
			final byte[] shifted = new byte[file.limit() + 7];
			file.duplicate().get(shifted, 3, file.limit());
			// a buffer that starts inside its array
			final ByteBuffer slice = ByteBuffer.wrap(shifted, 3, file.limit()).slice();
			final MipLevels expected = DdsDecoder.decode(file.asReadOnlyBuffer(), 0, true).getLevels();
			for (final ByteBuffer other : new ByteBuffer[] { file, slice }) {
				final MipLevels levels = DdsDecoder.decode(other, 0, true).getLevels();
				assertEquals(expected.count(), levels.count(), "levels at " + length);
				for (int level = 0; level < levels.count(); level++) {
					assertArrayEquals(expected.getArray(level), levels.getArray(level), "level " + level);
				}
			}
		}
	}

	@Test
	void levelsAddedAsArraysBecomeBuffersOnlyWhenAsked() {
		final MipLevels levels = new MipLevels();
		final byte[] rgba = { 1, 2, 3, 4, 5, 6, 7, 8 };
		levels.add(2, 1, rgba);
		assertSame(rgba, levels.getArray(0));
		final ByteBuffer buffer = levels.getRgba(0);
		assertEquals(true, buffer.isDirect());
		assertEquals(0, buffer.position());
		assertEquals(8, buffer.remaining());
		assertEquals(6, buffer.get(5));
		assertSame(buffer, levels.getRgba(0));
		assertArrayEquals(rgba, levels.getArray(0));
		assertEquals(1, levels.count());
		assertEquals(8, levels.byteCount());
	}

	private static void assertColor(final MipLevels levels, final int level, final int red, final int green,
			final int blue) {
		final ByteBuffer rgba = levels.getRgba(level);
		assertEquals(red, rgba.get(0) & 0xFF, "red of level " + level);
		assertEquals(green, rgba.get(1) & 0xFF, "green of level " + level);
		assertEquals(blue, rgba.get(2) & 0xFF, "blue of level " + level);
	}

	/** A square DXT1 image whose levels are solid colors, cut off after the given number of bytes. */
	private static ByteBuffer dds(final int size, final int levelCount, final boolean mipCountFlag,
			final int length) {
		final ByteBuffer file = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
		file.putInt(0, 0x20534444);
		file.putInt(4, 124);
		file.putInt(8, 0x1007 | (mipCountFlag ? 0x20000 : 0));
		file.putInt(12, size);
		file.putInt(16, size);
		file.putInt(28, levelCount);
		file.putInt(76, 32);
		file.putInt(80, 0x4);
		file.putInt(84, 0x31545844);
		file.position(128);
		for (int level = 0; level < levelCount; level++) {
			final int blocks = Math.max(1, (size >> level) / 4);
			for (int i = 0; i < (blocks * blocks); i++) {
				file.putShort((short) LEVEL_565[level]);
				file.putShort((short) LEVEL_565[level]);
				file.putInt(0);
			}
		}
		file.limit(Math.min(length, file.position()));
		file.position(0);
		return file;
	}

	/** A square BLP2 of plain BGRA pixels with this many stored levels, each a solid color. */
	private static ByteBuffer blp(final int size, final int levelCount) {
		final ByteBuffer file = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
		file.putInt(0, 0x32504C42);
		file.putInt(4, 1);
		file.put(8, (byte) 3);
		file.put(9, (byte) 8);
		file.put(11, (byte) 1);
		file.putInt(12, size);
		file.putInt(16, size);
		int offset = 148;
		for (int level = 0; level < levelCount; level++) {
			final int side = Math.max(1, size >> level);
			file.putInt(20 + (level * 4), offset);
			file.putInt(84 + (level * 4), side * side * 4);
			for (int i = 0; i < (side * side); i++) {
				final int at = offset + (i * 4);
				file.put(at, (byte) LEVEL_BGRA[level][0]);
				file.put(at + 1, (byte) LEVEL_BGRA[level][1]);
				file.put(at + 2, (byte) LEVEL_BGRA[level][2]);
				file.put(at + 3, (byte) 255);
			}
			offset += side * side * 4;
		}
		file.limit(offset);
		return file;
	}
}
