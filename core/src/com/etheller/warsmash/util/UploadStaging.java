package com.etheller.warsmash.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The buffer that texture uploads copy pixels or blocks held in arrays into, since the GL calls take direct buffers.
 * One buffer is kept and reused; a buffer for each upload was garbage of the texture's size, and on Android direct
 * buffers are arrays in the Java heap, so loading a map collected its textures' sizes several times over. Only the GL
 * thread uses it.
 */
public final class UploadStaging {
	/** Uploads up to this size reuse the kept buffer; a bigger one gets a block of its own, freed after the upload. */
	private static final int KEPT_BYTES = 4 << 20;

	private static OffHeapBlock kept;
	private static OffHeapBlock oversized;

	private UploadStaging() {
	}

	/**
	 * The bytes in a direct buffer from position 0, valid until {@link #done()}; keep calls of it and the upload
	 * together.
	 */
	public static ByteBuffer of(final byte[] data) {
		final OffHeapBlock block;
		if (data.length > KEPT_BYTES) {
			done();
			oversized = OffHeapBlock.allocate(data.length);
			block = oversized;
		}
		else {
			if (kept == null) {
				kept = OffHeapBlock.allocate(KEPT_BYTES);
			}
			block = kept;
		}
		final ByteBuffer buffer = block.buffer();
		buffer.clear();
		buffer.put(data);
		buffer.flip();
		return buffer.order(ByteOrder.nativeOrder());
	}

	/** Lets an upload bigger than the kept buffer give its memory back. */
	public static void done() {
		if (oversized != null) {
			oversized.free();
			oversized = null;
		}
	}
}
