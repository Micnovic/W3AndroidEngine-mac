package com.etheller.warsmash.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.datasources.IndependentStream;

class StreamBytesTest {
	@Test
	void anOwnedStreamHandsOverItsArray() throws IOException {
		final byte[] file = { 1, 2, 3, 4 };
		assertSame(file, StreamBytes.readAll(new StreamBytes.Owned(file)));
	}

	@Test
	void anOwnedStreamPartlyReadIsCopiedFromWhereItStands() throws IOException {
		final byte[] file = { 1, 2, 3, 4 };
		final StreamBytes.Owned stream = new StreamBytes.Owned(file);
		stream.read();
		final byte[] rest = StreamBytes.readAll(stream);
		assertNotSame(file, rest);
		assertArrayEquals(new byte[] { 2, 3, 4 }, rest);
	}

	@Test
	void aFileOpenedWhenReadHandsOverTheArrayOfWhatItOpened() throws IOException {
		final byte[] file = { 1, 2, 3, 4 };
		assertSame(file, StreamBytes.readAll(new IndependentStream.Opening(() -> new StreamBytes.Owned(file))));
	}

	private static byte[] bytes(final ByteBuffer buffer) {
		assertEquals(0, buffer.position());
		final byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		return bytes;
	}

	/** A stream that tells less than it has left, as some do. */
	private static InputStream underCounting(final byte[] file, final int told) {
		return new ByteArrayInputStream(file) {
			@Override
			public synchronized int available() {
				return Math.min(told, super.available());
			}
		};
	}

	@Test
	void filesParsedOneAfterAnotherEachGetTheirOwnBytes() throws IOException {
		final Random random = new Random(3);
		for (final int size : new int[] { 70000, 5, 1 << 20, 300, 0, 70000, 12 }) {
			final byte[] file = new byte[size];
			random.nextBytes(file);
			assertArrayEquals(file, StreamBytes.parse(new ByteArrayInputStream(file), StreamBytesTest::bytes));
			assertArrayEquals(file, StreamBytes.parse(new StreamBytes.Owned(file.clone()), StreamBytesTest::bytes));
			assertArrayEquals(file, StreamBytes.parse(underCounting(file, size / 2), StreamBytesTest::bytes));
			assertArrayEquals(file,
					StreamBytes.parse(new IndependentStream.Opening(() -> new ByteArrayInputStream(file)),
							StreamBytesTest::bytes));
		}
	}

	@Test
	void threadsParsingAtOnceNeverSeeEachOthersBytes() throws Exception {
		final int threads = 6;
		final byte[][] files = new byte[threads][];
		final Random random = new Random(11);
		for (int i = 0; i < threads; i++) {
			files[i] = new byte[(i % 2 == 0) ? 90000 + (i * 1000) : 1500 + i];
			random.nextBytes(files[i]);
		}
		final ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			final List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				final byte[] file = files[i];
				results.add(pool.submit(() -> {
					int wrong = 0;
					for (int round = 0; round < 300; round++) {
						// every parse stays on its bytes for a while, so the other threads parse meanwhile
						final byte[] seen = StreamBytes.parse(
								new IndependentStream.Opening(() -> underCounting(file, file.length / 3)), buffer -> {
									final byte[] copy = bytes(buffer);
									Thread.yield();
									return copy;
								});
						if (!Arrays.equals(file, seen)) {
							wrong++;
						}
					}
					return wrong;
				}));
			}
			for (final Future<Integer> result : results) {
				assertEquals(0, result.get().intValue());
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void aParseWithinAParseDoesNotTakeItsBytes() throws IOException {
		final byte[] outer = { 1, 2, 3, 4, 5 };
		final byte[] inner = { 9, 8, 7 };
		final byte[][] seen = new byte[2][];
		StreamBytes.parse(new ByteArrayInputStream(outer), buffer -> {
			seen[1] = StreamBytes.parse(new ByteArrayInputStream(inner), StreamBytesTest::bytes);
			seen[0] = bytes(buffer);
			return null;
		});
		assertArrayEquals(outer, seen[0]);
		assertArrayEquals(inner, seen[1]);
	}

	@Test
	void otherStreamsAreCopied() throws IOException {
		final byte[] file = { 1, 2, 3, 4 };
		final byte[] read = StreamBytes.readAll(new ByteArrayInputStream(file));
		assertNotSame(file, read);
		assertArrayEquals(file, read);
	}
}
