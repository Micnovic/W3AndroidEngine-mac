package com.etheller.warsmash.viewer5.handlers.blp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Encodes RGBA pixels into the block formats every OpenGL ES 3.0 GPU reads: ETC2 RGB8 (4 bits a pixel), ETC2 RGBA8
 * with an EAC alpha block (8 bits) and EAC RG11 (8 bits). The color blocks use the ETC1 subset of ETC2 (two
 * sub blocks, one brightness table each), which any ETC2 decoder reads as it is; the encoder picks sub block colors
 * from the averages and the best brightness table by squared error, which is fast enough for a phone's CPU.
 */
public final class Etc2Encoder {
	private static final int[][] COLOR_MODIFIERS = { { 2, 8 }, { 5, 17 }, { 9, 29 }, { 13, 42 }, { 18, 60 },
		{ 24, 80 }, { 33, 106 }, { 47, 183 } };
	private static final int[][] ALPHA_MODIFIERS = { { -3, -6, -9, -15, 2, 5, 8, 14 }, { -3, -7, -10, -13, 2, 6, 9, 12 },
		{ -2, -5, -8, -13, 1, 4, 7, 12 }, { -2, -4, -6, -13, 1, 3, 5, 12 }, { -3, -6, -8, -12, 2, 5, 7, 11 },
		{ -3, -7, -9, -11, 2, 6, 8, 10 }, { -4, -7, -8, -11, 3, 6, 7, 10 }, { -3, -5, -8, -11, 2, 4, 7, 10 },
		{ -2, -6, -8, -10, 1, 5, 7, 9 }, { -2, -5, -8, -10, 1, 4, 7, 9 }, { -2, -4, -8, -10, 1, 3, 7, 9 },
		{ -2, -5, -7, -10, 1, 4, 6, 9 }, { -3, -4, -7, -10, 2, 3, 6, 9 }, { -1, -2, -3, -10, 0, 1, 2, 9 },
		{ -4, -6, -8, -9, 3, 5, 7, 8 }, { -3, -5, -7, -9, 2, 4, 6, 8 } };
	/**
	 * The nearest of a table's eight levels for a pixel that is d = value - base away from the block base, for each
	 * table and multiplier, as its distance times eight plus the table index, which lowest index first on a tie. It is valid as long as no level
	 * is cut off by the range of a byte.
	 */
	private static final class Nearest {
		static final char[] INDEX = new char[ALPHA_MODIFIERS.length * 16 * 511];
		static final char[] SQUARED = new char[ALPHA_MODIFIERS.length * 16 * 511];

		static {
			for (int table = 0; table < ALPHA_MODIFIERS.length; table++) {
				for (int multiplier = 1; multiplier < 16; multiplier++) {
					final int at = ((table * 16) + multiplier) * 511;
					for (int d = -255; d <= 255; d++) {
						int bestIndex = 0;
						int bestDelta = Integer.MAX_VALUE;
						for (int i = 0; i < 8; i++) {
							final int delta = Math.abs((ALPHA_MODIFIERS[table][i] * multiplier) - d);
							if (delta < bestDelta) {
								bestDelta = delta;
								bestIndex = i;
							}
						}
						INDEX[at + d + 255] = (char) ((bestDelta << 3) | bestIndex);
						SQUARED[at + d + 255] = (char) (bestDelta * bestDelta);
					}
				}
			}
		}
	}

	/** The multiplier of each table for a block whose values are spread by 0..255: the least one that covers them. */
	private static final class Multipliers {
		static final byte[] OF = new byte[ALPHA_MODIFIERS.length * 256];

		static {
			for (int table = 0; table < ALPHA_MODIFIERS.length; table++) {
				final int range = ALPHA_MODIFIERS[table][7] - ALPHA_MODIFIERS[table][3];
				for (int spread = 0; spread < 256; spread++) {
					OF[(table << 8) + spread] = (byte) Math.max(1, Math.min(15, (spread + range - 1) / range));
				}
			}
		}
	}

	/** For |S| + 2 of a pixel: how many of the color tables, lowest first, give it their large modifier. */
	private static final class Classes {
		static final byte[] OF = new byte[(3 * 255) + 3];

		static {
			for (int i = 0; i < OF.length; i++) {
				final int s = i - 2;
				int c = 0;
				while ((c < 8) && ((s / 3) >= ((COLOR_MODIFIERS[c][0] + COLOR_MODIFIERS[c][1]) / 2))) {
					c++;
				}
				OF[i] = (byte) c;
			}
		}
	}

	/** The pixels of the two sub blocks of each layout, as flip * 2 + sub block, each channel indexed y * 4 + x. */
	private static final int[][] SUBS = new int[4][8];

	static {
		for (int flip = 0; flip < 2; flip++) {
			for (int s = 0; s < 2; s++) {
				int n = 0;
				for (int y = 0; y < 4; y++) {
					for (int x = 0; x < 4; x++) {
						if ((flip == 0 ? (x >> 1) : (y >> 1)) == s) {
							SUBS[(flip * 2) + s][n++] = (y * 4) + x;
						}
					}
				}
			}
		}
	}

	/** An 8 bit value as the 5 bit or the 4 bit one that expands nearest to it. */
	private static final class Quantized {
		static final byte[] FIVE = new byte[256];
		static final byte[] FOUR = new byte[256];

		static {
			for (int v = 0; v < 256; v++) {
				FIVE[v] = (byte) (((v * 31) + 127) / 255);
				FOUR[v] = (byte) (((v * 15) + 127) / 255);
			}
		}
	}

	/** The table whose index 4 is a zero modifier: a block of one value is stored exactly with it. */
	private static final int FLAT_ALPHA_TABLE = 13;
	private static final int FLAT_ALPHA_INDEX = 4;
	/** Images of at least this many blocks in all are encoded on several threads. */
	private static final int PARALLEL_BLOCKS = 512;
	/** The blocks of a run that one thread takes at a time. */
	private static final int RUN_BLOCKS = 512;

	private Etc2Encoder() {
	}

	/** @return the ETC2 RGB8 blocks of a width * height RGBA image, rows from the top; alpha is ignored */
	public static byte[] encodeRgb(final byte[] rgba, final int width, final int height) {
		return encode(rgba, width, height, 8, KIND_RGB);
	}

	/** @return the ETC2 RGBA8 blocks (EAC alpha block, then the RGB block) of a width * height RGBA image */
	public static byte[] encodeRgba(final byte[] rgba, final int width, final int height) {
		return encode(rgba, width, height, 16, KIND_RGBA);
	}

	/** @return the EAC RG11 blocks (red block, then green block) of the red and green channels of an RGBA image */
	public static byte[] encodeRg(final byte[] rgba, final int width, final int height) {
		return encode(rgba, width, height, 16, KIND_RG);
	}

	/** The bytes of the blocks of an image of this size, in any of the three formats. */
	public static int byteCount(final int width, final int height, final int blockBytes) {
		return Math.max(1, (width + 3) / 4) * Math.max(1, (height + 3) / 4) * blockBytes;
	}

	static final int KIND_RGB = 0;
	static final int KIND_RGBA = 1;
	static final int KIND_RG = 2;

	private static byte[] encode(final byte[] rgba, final int width, final int height, final int blockBytes,
			final int kind) {
		return encodeLevels(new byte[][] { rgba }, new int[] { width }, new int[] { height }, blockBytes, kind)[0];
	}

	/**
	 * Encodes every level of a mip chain in one pass: the blocks of all of them are cut into small runs that the
	 * calling thread and the helper threads take as they become free, so the cores of different speed all stay busy
	 * to the end and the small levels do not wait for their own turn.
	 */
	static byte[][] encodeLevels(final byte[][] rgba, final int[] widths, final int[] heights, final int blockBytes,
			final int kind) {
		final int levels = rgba.length;
		final int[] blocksWide = new int[levels];
		final byte[][] out = new byte[levels][];
		final List<int[]> runs = new ArrayList<>();
		int totalBlocks = 0;
		for (int level = 0; level < levels; level++) {
			blocksWide[level] = Math.max(1, (widths[level] + 3) / 4);
			final int blocksHigh = Math.max(1, (heights[level] + 3) / 4);
			out[level] = new byte[blocksWide[level] * blocksHigh * blockBytes];
			totalBlocks += blocksWide[level] * blocksHigh;
			final int rowsPerRun = Math.max(1, RUN_BLOCKS / blocksWide[level]);
			for (int row = 0; row < blocksHigh; row += rowsPerRun) {
				runs.add(new int[] { level, row, Math.min(blocksHigh, row + rowsPerRun) });
			}
		}
		final AtomicInteger next = new AtomicInteger();
		final Runnable worker = () -> {
			final Scratch scratch = new Scratch();
			for (int i = next.getAndIncrement(); i < runs.size(); i = next.getAndIncrement()) {
				final int[] run = runs.get(i);
				for (int row = run[1]; row < run[2]; row++) {
					encodeRow(scratch, rgba[run[0]], widths[run[0]], heights[run[0]], row, blocksWide[run[0]],
							out[run[0]], blockBytes, kind);
				}
			}
		};
		final int helpers = totalBlocks < PARALLEL_BLOCKS ? 0
				: Math.min(ForkJoinPool.getCommonPoolParallelism(), runs.size() - 1);
		final List<CompletableFuture<Void>> started = new ArrayList<>();
		for (int i = 0; i < helpers; i++) {
			started.add(CompletableFuture.runAsync(worker));
		}
		try {
			worker.run();
		}
		finally {
			// the helpers must be done with the arrays, also when this thread failed
			for (final CompletableFuture<Void> future : started) {
				future.join();
			}
		}
		return out;
	}

	private static void encodeRow(final Scratch scratch, final byte[] rgba, final int width, final int height,
			final int blockY, final int blocksWide, final byte[] out, final int blockBytes, final int kind) {
		for (int blockX = 0; blockX < blocksWide; blockX++) {
			scratch.load(rgba, width, height, blockX * 4, blockY * 4);
			int offset = ((blockY * blocksWide) + blockX) * blockBytes;
			if (kind == KIND_RG) {
				putLong(out, offset, scratch.encodeChannel(scratch.r, 0));
				putLong(out, offset + 8, scratch.encodeChannel(scratch.g, 1));
			}
			else {
				if (kind == KIND_RGBA) {
					putLong(out, offset, scratch.encodeChannel(scratch.a, 0));
					offset += 8;
				}
				putLong(out, offset, scratch.encodeColor());
			}
		}
	}

	private static void putLong(final byte[] out, final int offset, final long value) {
		for (int i = 0; i < 8; i++) {
			out[offset + i] = (byte) (value >>> (56 - (8 * i)));
		}
	}

	private static int clamp(final int value) {
		return value < 0 ? 0 : (value > 255 ? 255 : value);
	}

	private static int expand4(final int value) {
		return value * 17;
	}

	private static int expand5(final int value) {
		return (value << 3) | (value >> 2);
	}

	/** The 16 pixels of a block, each channel indexed y * 4 + x, and the working arrays of the encoder. */
	private static final class Scratch {
		final int[] r = new int[16];
		final int[] g = new int[16];
		final int[] b = new int[16];
		final int[] a = new int[16];
		/** The sum of the three channels and the sum of their squares of each pixel. */
		private final int[] sums = new int[16];
		private final int[] squares = new int[16];
		private final int[] diffSum = new int[8];
		/** Per class the pixels' number in the low four bits and their summed |S| above. */
		private final int[] classPacked = new int[9];
		private final int[][] layoutIndices = new int[2][16];
		private int[] flipIndices = this.layoutIndices[0];
		/** The sums of the red, green and blue channels and of all squares in each quarter of the block. */
		private final int[] quarterR = new int[4];
		private final int[] quarterG = new int[4];
		private final int[] quarterB = new int[4];
		private final int[] quarterSquares = new int[4];
		private final int[] base = new int[6];
		private final int[] quant5 = new int[6];
		private final int[] quant4 = new int[6];
		private final int[] tables = new int[2];
		private final int[] result = new int[1];
		private long layoutError;
		private final int[] channelOrder = new int[16];
		private final int[] lastTables = new int[2];
		private final int[] sumsR = new int[2];
		private final int[] sumsG = new int[2];
		private final int[] sumsB = new int[2];
		private final int[] sumsSq = new int[2];
		private int subR;
		private int subG;
		private int subB;
		private int subSquares;

		void load(final byte[] rgba, final int width, final int height, final int x0, final int y0) {
			final int[] quarterR = this.quarterR;
			final int[] quarterG = this.quarterG;
			final int[] quarterB = this.quarterB;
			final int[] quarterSquares = this.quarterSquares;
			for (int q = 0; q < 4; q++) {
				quarterR[q] = 0;
				quarterG[q] = 0;
				quarterB[q] = 0;
				quarterSquares[q] = 0;
			}
			for (int y = 0; y < 4; y++) {
				final int py = Math.min(y0 + y, height - 1);
				for (int x = 0; x < 4; x++) {
					final int px = Math.min(x0 + x, width - 1);
					final int at = ((py * width) + px) * 4;
					final int i = (y * 4) + x;
					final int red = rgba[at] & 0xFF;
					final int green = rgba[at + 1] & 0xFF;
					final int blue = rgba[at + 2] & 0xFF;
					final int square = (red * red) + (green * green) + (blue * blue);
					this.r[i] = red;
					this.g[i] = green;
					this.b[i] = blue;
					this.a[i] = rgba[at + 3] & 0xFF;
					this.sums[i] = red + green + blue;
					this.squares[i] = square;
					final int q = ((y >> 1) << 1) | (x >> 1);
					quarterR[q] += red;
					quarterG[q] += green;
					quarterB[q] += blue;
					quarterSquares[q] += square;
				}
			}
		}

		/** One 8 bit channel as an EAC block, which is also the layout of an RG11 half. */
		long encodeChannel(final int[] v, final int slot) {
			int min = 255;
			int max = 0;
			for (int i = 0; i < 16; i++) {
				min = Math.min(min, v[i]);
				max = Math.max(max, v[i]);
			}
			if (min == max) {
				long block = ((long) min << 56) | (1L << 52) | ((long) FLAT_ALPHA_TABLE << 48);
				for (int p = 0; p < 16; p++) {
					block |= (long) FLAT_ALPHA_INDEX << (45 - (3 * p));
				}
				return block;
			}
			// The pixels in the order the indices are stored, so that the loop below reads them in sequence.
			final int[] t = this.channelOrder;
			for (int p = 0; p < 16; p++) {
				t[p] = v[((p & 3) * 4) + (p >> 2)];
			}
			int bestError = Integer.MAX_VALUE;
			int bestTable = 16;
			int bestBase = 0;
			int bestMultiplier = 0;
			final int spread = max - min;
			final char[] squares = Nearest.SQUARED;
			// The table that won in the block before is tried first, which makes the cut off of the others sooner; a
			// tie still goes to the lower table, as it did when they were tried in order.
			final int guess = this.lastTables[slot];
			for (int step = 0; step < 16; step++) {
				final int table = step == 0 ? guess : (step - 1 < guess ? step - 1 : step);
				final int[] mods = ALPHA_MODIFIERS[table];
				final int multiplier = Multipliers.OF[(table << 8) + spread];
				// (min + max) / 2 - multiplier * (tableMin + tableMax) / 2, rounded half up
				final int base = clamp((min + max - (multiplier * (mods[3] + mods[7])) + 1) >> 1);
				int error = 0;
				if ((base + (mods[3] * multiplier) >= 0) && (base + (mods[7] * multiplier) <= 255)) {
					final int lutAt = (((table * 16) + multiplier) * 511) + 255 - base;
					for (int p = 0; p < 16; p += 4) {
						error += squares[lutAt + t[p]] + squares[lutAt + t[p + 1]] + squares[lutAt + t[p + 2]]
								+ squares[lutAt + t[p + 3]];
						if (error > bestError) {
							break;
						}
					}
				}
				else {
					for (int p = 0; (p < 16) && (error <= bestError); p++) {
						int bestDelta = Integer.MAX_VALUE;
						for (int i = 0; i < 8; i++) {
							final int delta = Math.abs(clamp(base + (mods[i] * multiplier)) - t[p]);
							if (delta < bestDelta) {
								bestDelta = delta;
							}
						}
						error += bestDelta * bestDelta;
					}
				}
				if ((error < bestError) || ((error == bestError) && (table < bestTable))) {
					bestError = error;
					bestTable = table;
					bestBase = base;
					bestMultiplier = multiplier;
				}
			}
			this.lastTables[slot] = bestTable;
			final int[] mods = ALPHA_MODIFIERS[bestTable];
			final boolean uncut = (bestBase + (mods[3] * bestMultiplier) >= 0)
					&& (bestBase + (mods[7] * bestMultiplier) <= 255);
			final int lutAt = (((bestTable * 16) + bestMultiplier) * 511) + 255 - bestBase;
			long indices = 0;
			for (int p = 0; p < 16; p++) {
				int bestIndex = 0;
				if (uncut) {
					bestIndex = Nearest.INDEX[lutAt + t[p]] & 7;
				}
				else {
					int bestDelta = Integer.MAX_VALUE;
					for (int i = 0; i < 8; i++) {
						final int delta = Math.abs(clamp(bestBase + (mods[i] * bestMultiplier)) - t[p]);
						if (delta < bestDelta) {
							bestDelta = delta;
							bestIndex = i;
						}
					}
				}
				indices |= (long) bestIndex << (45 - (3 * p));
			}
			return ((long) bestBase << 56) | ((long) bestMultiplier << 52) | ((long) bestTable << 48) | indices;
		}

		/**
		 * The pixels as an ETC1 style block: the better of the two sub block layouts, the side by side one on a tie.
		 * The layout whose halves are more alike inside is tried first, as it is usually the better, which lets the
		 * other stop early.
		 */
		long encodeColor() {
			final boolean stackedFirst = flipPreferred();
			final long first = encodeLayout(stackedFirst ? 1 : 0, Long.MAX_VALUE);
			final long firstError = this.layoutError;
			final long second;
			final boolean secondWins;
			if (stackedFirst) {
				second = encodeLayout(0, firstError + 1);
				secondWins = this.layoutError <= firstError;
			}
			else {
				second = encodeLayout(1, firstError);
				secondWins = this.layoutError < firstError;
			}
			final int flip = (stackedFirst ^ secondWins) ? 1 : 0;
			long block = secondWins ? second : first;
			final int[] indices = this.layoutIndices[flip];
			for (int y = 0; y < 4; y++) {
				for (int x = 0; x < 4; x++) {
					final int index = indices[(y * 4) + x];
					final int p = (x * 4) + y;
					block |= ((long) (index >> 1) << (16 + p)) | ((long) (index & 1) << p);
				}
			}
			return block;
		}

		/** Whether the halves one above the other hold less variation than the halves side by side. */
		private boolean flipPreferred() {
			final int[] quarterR = this.quarterR;
			final int[] quarterG = this.quarterG;
			final int[] quarterB = this.quarterB;
			// the squared deviation from the half means is the squares of all pixels less these sums over 8
			final long sideBySide = halves(quarterR[0] + quarterR[2], quarterR[1] + quarterR[3])
					+ halves(quarterG[0] + quarterG[2], quarterG[1] + quarterG[3])
					+ halves(quarterB[0] + quarterB[2], quarterB[1] + quarterB[3]);
			final long stacked = halves(quarterR[0] + quarterR[1], quarterR[2] + quarterR[3])
					+ halves(quarterG[0] + quarterG[1], quarterG[2] + quarterG[3])
					+ halves(quarterB[0] + quarterB[1], quarterB[2] + quarterB[3]);
			return stacked > sideBySide;
		}

		private static long halves(final int first, final int second) {
			return ((long) first * first) + ((long) second * second);
		}

		/**
		 * Encodes the block with the two sub blocks side by side (flip 0) or one above the other (flip 1). A layout
		 * that cannot come out under the error limit stops early with the layout error at Long.MAX_VALUE.
		 */
		private long encodeLayout(final int flip, final long limit) {
			long totalError = 0;
			final int[] base = this.base;
			final int[] quant5 = this.quant5;
			final int[] quant4 = this.quant4;
			for (int s = 0; s < 2; s++) {
				// the two quarters of the half
				final int first = flip == 0 ? s : (2 * s);
				final int second = flip == 0 ? (s + 2) : ((2 * s) + 1);
				final int sr = this.quarterR[first] + this.quarterR[second];
				final int sg = this.quarterG[first] + this.quarterG[second];
				final int sb = this.quarterB[first] + this.quarterB[second];
				this.sumsR[s] = sr;
				this.sumsG[s] = sg;
				this.sumsB[s] = sb;
				this.sumsSq[s] = this.quarterSquares[first] + this.quarterSquares[second];
				quant5[(s * 3)] = Quantized.FIVE[(sr + 4) >> 3];
				quant5[(s * 3) + 1] = Quantized.FIVE[(sg + 4) >> 3];
				quant5[(s * 3) + 2] = Quantized.FIVE[(sb + 4) >> 3];
				quant4[(s * 3)] = Quantized.FOUR[(sr + 4) >> 3];
				quant4[(s * 3) + 1] = Quantized.FOUR[(sg + 4) >> 3];
				quant4[(s * 3) + 2] = Quantized.FOUR[(sb + 4) >> 3];
			}
			boolean differential = true;
			for (int c = 0; c < 3; c++) {
				final int delta = quant5[3 + c] - quant5[c];
				if ((delta < -4) || (delta > 3)) {
					differential = false;
				}
			}
			for (int i = 0; i < 6; i++) {
				base[i] = differential ? expand5(quant5[i]) : expand4(quant4[i]);
			}
			final int[] tables = this.tables;
			this.flipIndices = this.layoutIndices[flip];
			for (int s = 0; s < 2; s++) {
				this.subR = this.sumsR[s];
				this.subG = this.sumsG[s];
				this.subB = this.sumsB[s];
				this.subSquares = this.sumsSq[s];
				final long error = bestTable(SUBS[(flip * 2) + s], base[s * 3], base[(s * 3) + 1], base[(s * 3) + 2],
						limit - totalError);
				if (error == Long.MAX_VALUE) {
					this.layoutError = Long.MAX_VALUE;
					return 0;
				}
				totalError += error;
				tables[s] = this.result[0];
			}
			this.layoutError = totalError;
			long block = 0;
			if (differential) {
				block |= ((long) quant5[0] << 59) | ((long) ((quant5[3] - quant5[0]) & 7) << 56)
						| ((long) quant5[1] << 51) | ((long) ((quant5[4] - quant5[1]) & 7) << 48)
						| ((long) quant5[2] << 43) | ((long) ((quant5[5] - quant5[2]) & 7) << 40) | (1L << 33);
			}
			else {
				block |= ((long) quant4[0] << 60) | ((long) quant4[3] << 56) | ((long) quant4[1] << 52)
						| ((long) quant4[4] << 48) | ((long) quant4[2] << 44) | ((long) quant4[5] << 40);
			}
			block |= ((long) tables[0] << 37) | ((long) tables[1] << 34) | ((long) flip << 32);
			return block;
		}

		/**
		 * Picks the brightness table of the current sub block for this base color; the table in result[0], its pixel
		 * indices in flipIndices, and the squared error as the return value, or Long.MAX_VALUE when no table comes
		 * out under the limit.
		 */
		private long bestTable(final int[] sub, final int baseR, final int baseG, final int baseB,
				final long limit) {
			long bestError = limit;
			int bestTable = -1;
			// While no channel is cut off by the range of a byte, the squared error of a pixel is 3m^2 - 2mS + Q (m
			// the modifier, S the sum and Q the sum of squares of the channel differences to the base). The pixel
			// takes the large modifier of a table when its S is far enough from zero, which depends on |S| only, so
			// the pixels are counted by the first table that takes their small modifier and a table costs a few
			// operations, not a pass over the pixels.
			final int[] packed = this.classPacked;
			for (int c = 0; c <= 8; c++) {
				packed[c] = 0;
			}
			int total = 0;
			final int baseSum = baseR + baseG + baseB;
			final int[] sums = this.sums;
			for (int n = 0; n < 8; n++) {
				final int s = sums[sub[n]] - baseSum;
				this.diffSum[n] = s;
				final int toward = s >= -2 ? s : -s;
				final int c = Classes.OF[toward + 2];
				packed[c] += (toward << 4) + 1;
				total += toward;
			}
			final int sumQ = this.subSquares - (2 * ((baseR * this.subR) + (baseG * this.subG) + (baseB * this.subB)))
					+ (8 * ((baseR * baseR) + (baseG * baseG) + (baseB * baseB)));
			final int lowest = Math.min(baseR, Math.min(baseG, baseB));
			final int highest = Math.max(baseR, Math.max(baseG, baseB));
			int smallCount = 0;
			int smallSum = 0;
			for (int table = 0; table < 8; table++) {
				final int small = COLOR_MODIFIERS[table][0];
				final int large = COLOR_MODIFIERS[table][1];
				smallCount += packed[table] & 15;
				smallSum += packed[table] >> 4;
				long error = 0;
				if ((lowest >= large) && ((highest + large) <= 255)) {
					error = sumQ + (3 * ((smallCount * small * small) + ((8 - smallCount) * large * large)))
							- (2 * ((small * smallSum) + (large * (total - smallSum))));
				}
				else {
					final int threshold = (small + large) / 2;
					for (int n = 0; (n < 8) && (error < bestError); n++) {
						final int i = sub[n];
						final int delta = this.diffSum[n] / 3;
						final int modifier;
						if (delta >= 0) {
							modifier = delta >= threshold ? large : small;
						}
						else {
							modifier = -delta >= threshold ? -large : -small;
						}
						final int er = clamp(baseR + modifier) - this.r[i];
						final int eg = clamp(baseG + modifier) - this.g[i];
						final int eb = clamp(baseB + modifier) - this.b[i];
						error += (er * er) + (eg * eg) + (eb * eb);
					}
				}
				if (error < bestError) {
					bestError = error;
					bestTable = table;
				}
			}
			if (bestTable < 0) {
				return Long.MAX_VALUE;
			}
			final int small = COLOR_MODIFIERS[bestTable][0];
			final int threshold = (small + COLOR_MODIFIERS[bestTable][1]) / 2;
			for (int n = 0; n < 8; n++) {
				final int delta = this.diffSum[n] / 3;
				this.flipIndices[sub[n]] = delta >= 0 ? (delta >= threshold ? 1 : 0) : (-delta >= threshold ? 3 : 2);
			}
			this.result[0] = bestTable;
			return bestError;
		}
	}
}
