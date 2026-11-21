package com.etheller.warsmash.viewer5.handlers.blp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.OffHeapBlock;
import com.etheller.warsmash.util.UploadStaging;

/**
 * The decoded mip levels of an image as RGBA bytes, the largest first, each level half the size of the one above
 * (at least one pixel).
 */
public final class MipLevels {
	private final List<int[]> sizes = new ArrayList<>();
	private final List<ByteBuffer> pixels = new ArrayList<>();
	/** A level added as an array stays one until something asks for its buffer, which is only the upload. */
	private final List<byte[]> arrays = new ArrayList<>();
	/** The levels decoded outside the Java heap, which {@link #free()} gives back. */
	private final List<OffHeapBlock> blocks = new ArrayList<>();

	void add(final int width, final int height, final byte[] rgba) {
		this.sizes.add(new int[] { width, height });
		this.pixels.add(null);
		this.arrays.add(rgba);
		this.blocks.add(null);
	}

	void add(final int width, final int height, final ByteBuffer rgba) {
		this.sizes.add(new int[] { width, height });
		this.pixels.add(rgba);
		this.arrays.add(null);
		this.blocks.add(null);
	}

	/** A level in a block of its own, which this keeps until {@link #free()}. */
	void add(final int width, final int height, final OffHeapBlock rgba) {
		this.sizes.add(new int[] { width, height });
		this.pixels.add(rgba.buffer());
		this.arrays.add(null);
		this.blocks.add(rgba);
	}

	public int count() {
		return this.sizes.size();
	}

	public int getWidth(final int level) {
		return this.sizes.get(level)[0];
	}

	public int getHeight(final int level) {
		return this.sizes.get(level)[1];
	}

	/**
	 * @return direct buffer of width * height RGBA pixels, rows from the top
	 */
	public ByteBuffer getRgba(final int level) {
		ByteBuffer buffer = this.pixels.get(level);
		if (buffer == null) {
			final byte[] rgba = this.arrays.get(level);
			buffer = ByteBuffer.allocateDirect(rgba.length).order(ByteOrder.nativeOrder());
			buffer.put(rgba);
			buffer.position(0);
			this.pixels.set(level, buffer);
			this.arrays.set(level, null);
		}
		return buffer;
	}

	/**
	 * The pixels of a level for one upload: a level held as an array is copied into the shared upload buffer rather
	 * than into a buffer of its own, so the result is valid only until the next upload.
	 */
	public ByteBuffer getRgbaForUpload(final int level) {
		final byte[] rgba = this.arrays.get(level);
		if (rgba != null) {
			return UploadStaging.of(rgba);
		}
		final ByteBuffer buffer = this.pixels.get(level);
		buffer.position(0);
		return buffer;
	}

	/** Gives back the levels decoded outside the Java heap; their buffers may not be used afterwards. */
	public void free() {
		for (int level = 0; level < this.blocks.size(); level++) {
			final OffHeapBlock block = this.blocks.get(level);
			if (block != null) {
				this.blocks.set(level, null);
				this.pixels.set(level, null);
				block.free();
			}
		}
	}

	/**
	 * The pixels of a level as an array a reader may take as it is and not change: the one the level was added as,
	 * or a copy of its buffer.
	 */
	byte[] getArray(final int level) {
		final byte[] rgba = this.arrays.get(level);
		if (rgba != null) {
			return rgba;
		}
		final ByteBuffer buffer = this.pixels.get(level).duplicate();
		final int[] size = this.sizes.get(level);
		final byte[] copy = new byte[size[0] * size[1] * 4];
		buffer.position(0);
		buffer.get(copy);
		return copy;
	}

	/** The bytes of every level together. */
	public long byteCount() {
		long bytes = 0;
		for (final int[] size : this.sizes) {
			bytes += (long) size[0] * size[1] * 4;
		}
		return bytes;
	}

	/** The width or height of a level of an image with this full size. */
	static int size(final int full, final int level) {
		return Math.max(1, full >> level);
	}

	/** How many levels a full chain has, down to one pixel. */
	static int levelCount(final int width, final int height) {
		int count = 1;
		for (int size = Math.max(width, height); size > 1; size >>= 1) {
			count++;
		}
		return count;
	}

	/** A texture left smaller than this by skipped levels would lose more than its largest level's detail. */
	private static final int SMALLEST_SKIPPED_TO = 128;
	private static final int SIZE_BITS = 16;

	/**
	 * A size limit (0 for none) together with how many more of the largest levels to leave out of a texture the limit
	 * leaves whole, as the decoders take it for their size limit.
	 */
	public static int sizeRule(final int maxSize, final int skippedLevels) {
		return Math.max(0, Math.min(maxSize, (1 << SIZE_BITS) - 1)) | (Math.max(0, skippedLevels) << SIZE_BITS);
	}

	/**
	 * The first level of a chain within the size limit (0 for no limit), never past the last level there is. A texture
	 * the limit leaves whole then drops the levels the {@link #sizeRule} skips, as long as it stays at least
	 * {@value #SMALLEST_SKIPPED_TO} pixels: one the limit lowered is smaller than it was meant to be already.
	 */
	static int firstWithin(final int width, final int height, final int sizeRule, final int levels) {
		final int maxSize = sizeRule & ((1 << SIZE_BITS) - 1);
		int skip = sizeRule >>> SIZE_BITS;
		int first = 0;
		while ((maxSize > 0) && (first < (levels - 1)) && (Math.max(size(width, first), size(height, first)) > maxSize)) {
			first++;
		}
		if (first > 0) {
			return first;
		}
		while ((skip > 0) && (first < (levels - 1))
				&& (Math.max(size(width, first + 1), size(height, first + 1)) >= SMALLEST_SKIPPED_TO)) {
			first++;
			skip--;
		}
		return first;
	}
}
