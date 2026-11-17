package com.etheller.warsmash.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Reads a whole stream into one array. The streams of the data sources know how many bytes are left, so the array
 * is allocated at that size and filled in place; collecting chunks and copying them into a result of the right
 * size needs twice the file on the Java heap, which on Android is the limit that big models and maps run into.
 */
public final class StreamBytes {
	private static final int MIN_GROWTH = 1 << 16;

	private StreamBytes() {
	}

	public static byte[] readAll(final InputStream stream) throws IOException {
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
