package com.etheller.warsmash.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.BufferUtils;

/**
 * Bytes that wait for a later step without counting against the Java heap. On Android a direct buffer is an array in
 * the Java heap, so there the bytes are allocated outside the runtime; on the desktop a direct buffer is native memory
 * but is given back only when the collector runs, which lets the textures of a loading map pile up. Both are freed by
 * {@link #free()}; a block that is let go without it is freed once the collector finds it unreachable. Without a
 * running application (tests) a plain direct buffer is used.
 */
public final class OffHeapBlock {
	private static final ReferenceQueue<OffHeapBlock> UNREACHABLE = new ReferenceQueue<>();
	private static final Set<Release> LIVE = ConcurrentHashMap.newKeySet();

	private static final class Release extends PhantomReference<OffHeapBlock> {
		private ByteBuffer unsafe;

		private Release(final OffHeapBlock block, final ByteBuffer unsafe) {
			super(block, UNREACHABLE);
			this.unsafe = unsafe;
		}

		private synchronized void free() {
			if (this.unsafe != null) {
				BufferUtils.disposeUnsafeByteBuffer(this.unsafe);
				this.unsafe = null;
			}
			LIVE.remove(this);
		}
	}

	/** Small enough to stay out of the runtime's space for large objects. */
	private static final int READ_CHUNK = 8 << 10;

	private final ByteBuffer buffer;
	private Release release;

	private OffHeapBlock(final ByteBuffer buffer) {
		this.buffer = buffer;
	}

	/** Whether this platform keeps direct buffers in the Java heap. */
	public static boolean directBuffersInHeap() {
		return (Gdx.app != null) && (Gdx.app.getType() == ApplicationType.Android);
	}

	public static OffHeapBlock allocate(final int bytes) {
		freeUnreachable();
		if (Gdx.app == null) {
			return new OffHeapBlock(ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder()));
		}
		final ByteBuffer unsafe = BufferUtils.newUnsafeByteBuffer(bytes).order(ByteOrder.nativeOrder());
		final OffHeapBlock block = new OffHeapBlock(unsafe);
		block.release = new Release(block, unsafe);
		LIVE.add(block.release);
		return block;
	}

	/** The bytes of the array in a block of their own. */
	public static OffHeapBlock copyOf(final byte[] bytes) {
		final OffHeapBlock block = allocate(bytes.length);
		block.buffer.put(bytes).clear();
		return block;
	}

	/**
	 * The rest of a stream in a block of its own, from position 0 to the limit, read through a small array: no
	 * array of the stream's size is made.
	 */
	public static OffHeapBlock readAll(final InputStream stream) throws IOException {
		int expected;
		try {
			expected = Math.max(0, stream.available());
		}
		catch (final IOException e) {
			expected = 0;
		}
		OffHeapBlock block = allocate(expected > 0 ? expected : READ_CHUNK * 8);
		try {
			final byte[] chunk = new byte[READ_CHUNK];
			int read;
			while ((read = stream.read(chunk)) >= 0) {
				if (block.buffer.remaining() < read) {
					final OffHeapBlock bigger = allocate((int) Math.min(Integer.MAX_VALUE - 8,
							Math.max((long) block.buffer.position() + read, (long) block.buffer.capacity() * 3 / 2)));
					block.buffer.flip();
					bigger.buffer.put(block.buffer);
					block.free();
					block = bigger;
				}
				block.buffer.put(chunk, 0, read);
			}
			block.buffer.flip();
			return block;
		}
		catch (final IOException | RuntimeException | Error e) {
			block.free();
			throw e;
		}
	}

	/** The content, valid until {@link #free()}; keep the block reachable while it is used. */
	public ByteBuffer buffer() {
		return this.buffer;
	}

	/** Lets the bytes go; the buffer may not be used afterwards. */
	public void free() {
		if (this.release != null) {
			this.release.free();
		}
	}

	private static void freeUnreachable() {
		Object released;
		while ((released = UNREACHABLE.poll()) != null) {
			((Release) released).free();
		}
	}
}
