package com.etheller.warsmash.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Reads a whole stream into one array. The streams of the data sources know how many bytes are left, so the array
 * is allocated at that size and filled in place; collecting chunks and copying them into a result of the right
 * size needs twice the file on the Java heap, which on Android is the limit that big models and maps run into.
 */
public final class StreamBytes {
	private static final int MIN_GROWTH = 1 << 16;
	/** The largest file {@link #parse} reads into the array it keeps for the thread. */
	private static final int PARSE_SCRATCH_MAX = (int) Math.min(32 << 20, Runtime.getRuntime().maxMemory() / 32);
	private static final ThreadLocal<SoftReference<byte[]>> PARSE_SCRATCH = new ThreadLocal<>();
	private static final ThreadLocal<boolean[]> PARSING = ThreadLocal.withInitial(() -> new boolean[1]);

	private StreamBytes() {
	}

	/**
	 * A stream over an array that only the stream holds, such as a file a data source has just read whole: reading
	 * all of it hands over the array instead of a copy.
	 */
	public static final class Owned extends ByteArrayInputStream {
		public Owned(final byte[] data) {
			super(data);
		}

		private synchronized byte[] takeWhole() {
			if ((this.pos != 0) || (this.count != this.buf.length)) {
				return null;
			}
			this.pos = this.count;
			return this.buf;
		}
	}

	/**
	 * A stream that only passes reads on to the one it opens, such as a file opened when it is first read: a reader of
	 * the whole stream reads the inner one, so that an {@link Owned} array is still handed over.
	 */
	public interface Passing {
		InputStream inner() throws IOException;
	}

	/** What is read from the bytes of a file, see {@link #parse}. */
	public interface Parser<T> {
		T parse(ByteBuffer bytes) throws IOException;
	}

	/**
	 * Parses the rest of a stream with a parser that keeps nothing of the bytes it is given, which are then in an array
	 * this thread uses for every such parse: a model file is read whole only to be parsed, and the big ones were the
	 * largest arrays made while a map loaded. The buffer starts at position 0 and ends with the stream.
	 */
	public static <T> T parse(InputStream stream, final Parser<T> parser) throws IOException {
		while (stream instanceof Passing) {
			stream = ((Passing) stream).inner();
		}
		final boolean[] parsing = PARSING.get();
		int expected;
		try {
			expected = stream.available();
		}
		catch (final IOException e) {
			expected = 0;
		}
		if ((stream instanceof Owned) || parsing[0] || (expected <= 0) || (expected > PARSE_SCRATCH_MAX)) {
			return parser.parse(ByteBuffer.wrap(readAll(stream)));
		}
		final SoftReference<byte[]> kept = PARSE_SCRATCH.get();
		byte[] scratch = kept == null ? null : kept.get();
		if ((scratch == null) || (scratch.length < expected)) {
			scratch = new byte[Math.max(expected, Math.min(PARSE_SCRATCH_MAX, Integer.highestOneBit(expected) << 1))];
			PARSE_SCRATCH.set(new SoftReference<>(scratch));
		}
		int length = 0;
		while (length < expected) {
			final int read = stream.read(scratch, length, expected - length);
			if (read < 0) {
				break;
			}
			length += read;
		}
		byte[] bytes = scratch;
		if (length == expected) {
			final int next = stream.read();
			if (next >= 0) {
				// the stream knew less than it has
				final byte[] rest = readAll(stream);
				bytes = Arrays.copyOf(scratch, length + 1 + rest.length);
				bytes[length] = (byte) next;
				System.arraycopy(rest, 0, bytes, length + 1, rest.length);
				length = bytes.length;
			}
		}
		parsing[0] = true;
		try {
			return parser.parse(ByteBuffer.wrap(bytes, 0, length).slice());
		}
		finally {
			parsing[0] = false;
		}
	}

	public static byte[] readAll(InputStream stream) throws IOException {
		while (stream instanceof Passing) {
			stream = ((Passing) stream).inner();
		}
		if (stream instanceof Owned) {
			final byte[] whole = ((Owned) stream).takeWhole();
			if (whole != null) {
				return whole;
			}
		}
		int expected;
		try {
			expected = Math.max(0, stream.available());
		}
		catch (final IOException e) {
			expected = 0;
		}
		byte[] data = new byte[expected];
		int length = 0;
		while (true) {
			while (length < data.length) {
				final int read = stream.read(data, length, data.length - length);
				if (read < 0) {
					return Arrays.copyOf(data, length);
				}
				length += read;
			}
			// The array is full: it holds everything unless the stream knew less than it has.
			final int next = stream.read();
			if (next < 0) {
				return data;
			}
			data = Arrays.copyOf(data, (int) Math.min(Integer.MAX_VALUE - 8,
					Math.max((long) data.length + MIN_GROWTH, (long) data.length * 3 / 2)));
			data[length++] = (byte) next;
		}
	}
}
