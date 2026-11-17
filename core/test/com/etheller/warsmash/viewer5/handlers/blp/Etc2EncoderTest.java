package com.etheller.warsmash.viewer5.handlers.blp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * The ETC2 blocks the encoder writes are read back with a decoder written from the format description, so a wrong
 * bit layout shows as large errors; the cache hands the same blocks back without decoding the source again.
 */
class Etc2EncoderTest {
	private static final int[][] COLOR_MODIFIERS = { { 2, 8 }, { 5, 17 }, { 9, 29 }, { 13, 42 }, { 18, 60 },
		{ 24, 80 }, { 33, 106 }, { 47, 183 } };
	private static final int[][] ALPHA_MODIFIERS = { { -3, -6, -9, -15, 2, 5, 8, 14 }, { -3, -7, -10, -13, 2, 6, 9, 12 },
		{ -2, -5, -8, -13, 1, 4, 7, 12 }, { -2, -4, -6, -13, 1, 3, 5, 12 }, { -3, -6, -8, -12, 2, 5, 7, 11 },
		{ -3, -7, -9, -11, 2, 6, 8, 10 }, { -4, -7, -8, -11, 3, 6, 7, 10 }, { -3, -5, -8, -11, 2, 4, 7, 10 },
		{ -2, -6, -8, -10, 1, 5, 7, 9 }, { -2, -5, -8, -10, 1, 4, 7, 9 }, { -2, -4, -8, -10, 1, 3, 7, 9 },
		{ -2, -5, -7, -10, 1, 4, 6, 9 }, { -3, -4, -7, -10, 2, 3, 6, 9 }, { -1, -2, -3, -10, 0, 1, 2, 9 },
		{ -4, -6, -8, -9, 3, 5, 7, 8 }, { -3, -5, -7, -9, 2, 4, 6, 8 } };

	@Test
	void flatColorsStayClose() {
		final byte[] rgba = new byte[16 * 16 * 4];
		for (int i = 0; i < (16 * 16); i++) {
			rgba[i * 4] = (byte) 200;
			rgba[(i * 4) + 1] = (byte) 90;
			rgba[(i * 4) + 2] = (byte) 33;
			rgba[(i * 4) + 3] = (byte) 255;
		}
		final byte[] decoded = decodeRgb(Etc2Encoder.encodeRgb(rgba, 16, 16), 16, 16);
		assertTrue(maxDifference(rgba, decoded, 3) <= 4);
	}

	@Test
	void gradientHasHighPsnr() {
		final byte[] rgba = new byte[64 * 64 * 4];
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				final int at = ((y * 64) + x) * 4;
				rgba[at] = (byte) (x * 4);
				rgba[at + 1] = (byte) (y * 4);
				rgba[at + 2] = (byte) ((x + y) * 2);
				rgba[at + 3] = (byte) 255;
			}
		}
		assertTrue(psnr(rgba, decodeRgb(Etc2Encoder.encodeRgb(rgba, 64, 64), 64, 64), 3) > 34);
	}

	@Test
	void noisyImageHasUsablePsnr() {
		final Random random = new Random(7);
		final byte[] rgba = new byte[64 * 64 * 4];
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				final int at = ((y * 64) + x) * 4;
				final int base = 80 + ((x / 8) * 10) + ((y / 8) * 6);
				for (int c = 0; c < 3; c++) {
					rgba[at + c] = (byte) Math.min(255, base + random.nextInt(24) + (c * 12));
				}
				rgba[at + 3] = (byte) 255;
			}
		}
		assertTrue(psnr(rgba, decodeRgb(Etc2Encoder.encodeRgb(rgba, 64, 64), 64, 64), 3) > 28);
	}

	@Test
	void alphaMaskIsExactAndGradientIsClose() {
		final byte[] rgba = new byte[32 * 32 * 4];
		final byte[] gradient = new byte[32 * 32 * 4];
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				final int at = ((y * 32) + x) * 4;
				rgba[at + 3] = (byte) ((((x / 3) + (y / 5)) & 1) == 0 ? 0 : 255);
				gradient[at + 3] = (byte) ((x * 6) + y);
			}
		}
		assertEquals(0, maxDifference(rgba, decodeAlpha(Etc2Encoder.encodeRgba(rgba, 32, 32), 32, 32), 1, 3));
		assertTrue(maxDifference(gradient, decodeAlpha(Etc2Encoder.encodeRgba(gradient, 32, 32), 32, 32), 1,
				3) <= 14);
	}

	@Test
	void twoChannelBlocksKeepRedAndGreen() {
		final byte[] rgba = new byte[32 * 32 * 4];
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				final int at = ((y * 32) + x) * 4;
				rgba[at] = (byte) (128 + (x * 2));
				rgba[at + 1] = (byte) (200 - y);
				rgba[at + 3] = (byte) 255;
			}
		}
		final byte[] blocks = Etc2Encoder.encodeRg(rgba, 32, 32);
		final byte[] red = decodeChannel(blocks, 32, 32, 16, 0);
		final byte[] green = decodeChannel(blocks, 32, 32, 16, 8);
		for (int i = 0; i < (32 * 32); i++) {
			assertTrue(Math.abs((rgba[i * 4] & 0xFF) - (red[i] & 0xFF)) <= 8);
			assertTrue(Math.abs((rgba[(i * 4) + 1] & 0xFF) - (green[i] & 0xFF)) <= 8);
		}
	}

	@Test
	void encodedBlocksStayTheSameAsInTheCacheFiles() {
		final int width = 70;
		final int height = 45;
		final Random random = new Random(5);
		final byte[] rgba = new byte[width * height * 4];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				final int i = ((y * width) + x) * 4;
				rgba[i] = (byte) ((x * 4) + random.nextInt(24));
				rgba[i + 1] = (byte) ((y * 5) + random.nextInt(40));
				rgba[i + 2] = (byte) (((x ^ y) & 1) == 0 ? random.nextInt(256) : ((x + y) * 3));
				rgba[i + 3] = (byte) (x < (width / 2) ? 255 : random.nextInt(256));
			}
		}
		assertEquals(1429083629L, crc(Etc2Encoder.encodeRgb(rgba, width, height)));
		assertEquals(1732319283L, crc(Etc2Encoder.encodeRgba(rgba, width, height)));
		assertEquals(3626054375L, crc(Etc2Encoder.encodeRg(rgba, width, height)));
	}

	@Test
	void levelsEncodedTogetherMatchTheSameLevelsEncodedAlone() {
		final Random random = new Random(11);
		final int[] sizes = { 96, 48, 24, 12, 6, 3, 1 };
		final byte[][] pixels = new byte[sizes.length][];
		final int[] widths = new int[sizes.length];
		for (int level = 0; level < sizes.length; level++) {
			pixels[level] = new byte[sizes[level] * sizes[level] * 4];
			random.nextBytes(pixels[level]);
			widths[level] = sizes[level];
		}
		final byte[][] together = Etc2Encoder.encodeLevels(pixels, widths, widths, 16, Etc2Encoder.KIND_RGBA);
		for (int level = 0; level < sizes.length; level++) {
			assertTrue(java.util.Arrays.equals(Etc2Encoder.encodeRgba(pixels[level], sizes[level], sizes[level]),
					together[level]));
		}
	}

	private static long crc(final byte[] bytes) {
		final java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(bytes, 0, bytes.length);
		return crc.getValue();
	}

	@Test
	void sizesThatAreNotWholeBlocksAreCovered() {
		final byte[] rgba = new byte[5 * 3 * 4];
		java.util.Arrays.fill(rgba, (byte) 120);
		assertEquals(2 * 1 * 8, Etc2Encoder.encodeRgb(rgba, 5, 3).length);
		assertEquals(1 * 1 * 16, Etc2Encoder.encodeRgba(new byte[4], 1, 1).length);
	}

	@Test
	void levelsSurviveTheCacheFormat() throws IOException {
		final CompressedMips levels = new CompressedMips(CompressedMips.FORMAT_RGB8_ETC2);
		levels.add(8, 8, new byte[4 * 8]);
		levels.add(4, 4, new byte[8]);
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		levels.write(new DataOutputStream(bytes));
		final CompressedMips back = CompressedMips.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
		assertEquals(2, back.count());
		assertEquals(levels.byteCount(), back.byteCount());
		assertEquals(CompressedMips.FORMAT_RGB8_ETC2, back.getFormat());
	}

	@Test
	void secondRunReadsTheCache() throws Exception {
		final File folder = java.nio.file.Files.createTempDirectory("texcache").toFile();
		System.setProperty("warsmash.textureCache", folder.getAbsolutePath());
		try {
			final byte[] file = new byte[200];
			new Random(3).nextBytes(file);
			final AtomicInteger decodes = new AtomicInteger();
			final TextureTranscoder.Decoder decoder = () -> {
				decodes.incrementAndGet();
				final MipLevels levels = new MipLevels();
				levels.add(128, 128, new byte[128 * 128 * 4]);
				levels.add(64, 64, new byte[64 * 64 * 4]);
				return levels;
			};
			final CompressedMips first = TextureTranscoder.transcode("Textures\\Test.dds", file, 0, decoder, false);
			assertNotNull(first);
			assertEquals(CompressedMips.FORMAT_RGBA8_ETC2_EAC, first.getFormat());
			assertEquals(1, decodes.get());
			// the cache file is written in the background
			for (int i = 0; (i < 100) && !hasCacheFile(folder); i++) {
				Thread.sleep(50);
			}
			final CompressedMips second = TextureTranscoder.transcode("Textures\\Test.dds", file, 0, decoder, false);
			assertNotNull(second);
			assertEquals(1, decodes.get());
			assertEquals(first.byteCount(), second.byteCount());
			// another size limit is another entry
			TextureTranscoder.transcode("Textures\\Test.dds", file, 512, decoder, false);
			assertEquals(2, decodes.get());
			// small images stay plain
			final TextureTranscoder.Decoder small = () -> {
				final MipLevels levels = new MipLevels();
				levels.add(64, 64, new byte[64 * 64 * 4]);
				return levels;
			};
			assertNull(TextureTranscoder.transcode("Textures\\Small.dds", file, 0, small, false));
		}
		finally {
			System.clearProperty("warsmash.textureCache");
		}
	}

	private static boolean hasCacheFile(final File folder) {
		final File[] subfolders = folder.listFiles();
		if (subfolders != null) {
			for (final File subfolder : subfolders) {
				final String[] names = subfolder.list();
				if (names != null) {
					for (final String name : names) {
						if (name.endsWith(".tx")) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private static long blockBits(final byte[] data, final int offset) {
		long bits = 0;
		for (int i = 0; i < 8; i++) {
			bits = (bits << 8) | (data[offset + i] & 0xFF);
		}
		return bits;
	}

	private static int clamp(final int value) {
		return Math.max(0, Math.min(255, value));
	}

	/** Decodes ETC2 RGB8 blocks of the ETC1 kind (individual and differential) to RGBA. */
	private static byte[] decodeRgb(final byte[] blocks, final int width, final int height) {
		return decodeColor(blocks, width, height, 8, 0);
	}

	private static byte[] decodeColor(final byte[] blocks, final int width, final int height, final int blockBytes,
			final int colorOffset) {
		final int blocksWide = (width + 3) / 4;
		final byte[] out = new byte[width * height * 4];
		for (int by = 0; by < ((height + 3) / 4); by++) {
			for (int bx = 0; bx < blocksWide; bx++) {
				final long bits = blockBits(blocks, (((by * blocksWide) + bx) * blockBytes) + colorOffset);
				final boolean differential = ((bits >> 33) & 1) != 0;
				final boolean flip = ((bits >> 32) & 1) != 0;
				final int[][] base = new int[2][3];
				if (differential) {
					for (int c = 0; c < 3; c++) {
						final int shift = 59 - (8 * c);
						final int first = (int) ((bits >> shift) & 0x1F);
						int delta = (int) ((bits >> (shift - 3)) & 7);
						if (delta >= 4) {
							delta -= 8;
						}
						final int second = first + delta;
						assertTrue((second >= 0) && (second < 32));
						base[0][c] = (first << 3) | (first >> 2);
						base[1][c] = (second << 3) | (second >> 2);
					}
				}
				else {
					for (int c = 0; c < 3; c++) {
						final int shift = 60 - (8 * c);
						base[0][c] = (int) ((bits >> shift) & 0xF) * 17;
						base[1][c] = (int) ((bits >> (shift - 4)) & 0xF) * 17;
					}
				}
				final int[] tables = { (int) ((bits >> 37) & 7), (int) ((bits >> 34) & 7) };
				for (int y = 0; y < 4; y++) {
					for (int x = 0; x < 4; x++) {
						final int px = (bx * 4) + x;
						final int py = (by * 4) + y;
						if ((px >= width) || (py >= height)) {
							continue;
						}
						final int sub = flip ? (y >> 1) : (x >> 1);
						final int p = (x * 4) + y;
						final int index = (int) ((((bits >> (16 + p)) & 1) << 1) | ((bits >> p) & 1));
						final int[] mods = COLOR_MODIFIERS[tables[sub]];
						final int modifier = index == 0 ? mods[0] : index == 1 ? mods[1] : index == 2 ? -mods[0] : -mods[1];
						final int at = ((py * width) + px) * 4;
						for (int c = 0; c < 3; c++) {
							out[at + c] = (byte) clamp(base[sub][c] + modifier);
						}
						out[at + 3] = (byte) 255;
					}
				}
			}
		}
		return out;
	}

	/** Decodes the EAC alpha of ETC2 RGBA8 blocks into the alpha channel of RGBA. */
	private static byte[] decodeAlpha(final byte[] blocks, final int width, final int height) {
		final byte[] alpha = decodeChannel(blocks, width, height, 16, 0);
		final byte[] out = new byte[width * height * 4];
		for (int i = 0; i < (width * height); i++) {
			out[(i * 4) + 3] = alpha[i];
		}
		return out;
	}

	/** Decodes one EAC block of each 4x4 block, at this offset in the block, to one byte a pixel. */
	private static byte[] decodeChannel(final byte[] blocks, final int width, final int height, final int blockBytes,
			final int offset) {
		final int blocksWide = (width + 3) / 4;
		final byte[] out = new byte[width * height];
		for (int by = 0; by < ((height + 3) / 4); by++) {
			for (int bx = 0; bx < blocksWide; bx++) {
				final long bits = blockBits(blocks, (((by * blocksWide) + bx) * blockBytes) + offset);
				final int base = (int) (bits >>> 56);
				final int multiplier = (int) ((bits >>> 52) & 0xF);
				final int[] mods = ALPHA_MODIFIERS[(int) ((bits >>> 48) & 0xF)];
				for (int y = 0; y < 4; y++) {
					for (int x = 0; x < 4; x++) {
						final int px = (bx * 4) + x;
						final int py = (by * 4) + y;
						if ((px >= width) || (py >= height)) {
							continue;
						}
						final int index = (int) ((bits >>> (45 - (3 * ((x * 4) + y)))) & 7);
						out[(py * width) + px] = (byte) clamp(base + (mods[index] * multiplier));
					}
				}
			}
		}
		return out;
	}

	private static int maxDifference(final byte[] a, final byte[] b, final int channels) {
		return maxDifference(a, b, 0, channels);
	}

	private static int maxDifference(final byte[] a, final byte[] b, final int first, final int count) {
		int max = 0;
		for (int i = 0; i < a.length; i += 4) {
			for (int c = first; c < (first + count); c++) {
				max = Math.max(max, Math.abs((a[i + c] & 0xFF) - (b[i + c] & 0xFF)));
			}
		}
		return max;
	}

	private static double psnr(final byte[] a, final byte[] b, final int channels) {
		double sum = 0;
		long count = 0;
		for (int i = 0; i < a.length; i += 4) {
			for (int c = 0; c < channels; c++) {
				final int d = (a[i + c] & 0xFF) - (b[i + c] & 0xFF);
				sum += d * d;
				count++;
			}
		}
		final double mse = sum / count;
		return mse == 0 ? 99 : 10 * Math.log10((255.0 * 255.0) / mse);
	}
}
