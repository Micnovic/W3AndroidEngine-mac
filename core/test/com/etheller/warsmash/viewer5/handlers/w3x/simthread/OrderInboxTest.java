package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class OrderInboxTest {
	@Test
	void emptyInboxDrainsNothing() {
		final OrderInbox inbox = new OrderInbox();
		assertEquals(0, inbox.drain(5, message -> {
			throw new AssertionError("nothing was submitted");
		}));
	}

	@Test
	void messagesComeOutInSubmissionOrderWithTheDrainTick() {
		final OrderInbox inbox = new OrderInbox();
		for (int i = 0; i < 5; i++) {
			inbox.submitCommand(i % 2, new byte[] { (byte) i, 9 }, 0, 2);
		}
		assertEquals(5, inbox.pendingCount());
		final List<Integer> seen = new ArrayList<>();
		final int count = inbox.drain(42, message -> {
			assertEquals(42, message.appliedTick);
			assertEquals(OrderMessage.COMMAND, message.kind);
			assertArrayEquals(new byte[] { (byte) seen.size(), 9 }, message.payloadCopy());
			assertEquals(seen.size() % 2, message.playerIndex);
			assertEquals(seen.size(), message.submitSequence);
			seen.add((int) message.payload[0]);
		});
		assertEquals(5, count);
		assertEquals(List.of(0, 1, 2, 3, 4), seen);
		assertEquals(0, inbox.pendingCount());
	}

	@Test
	void messagesAreRecycledAndStayCleared() {
		final OrderInbox inbox = new OrderInbox();
		for (int round = 0; round < 100; round++) {
			for (int i = 0; i < 8; i++) {
				final OrderMessage message = inbox.obtain();
				assertEquals(0, message.payloadLength);
				assertEquals(0, message.appliedTick);
				message.kind = OrderMessage.PAUSE;
				message.setPayload(new byte[] { 1 }, 0, 1);
				inbox.submit(message);
			}
			assertEquals(8, inbox.drain(round, message -> assertEquals(OrderMessage.PAUSE, message.kind)));
		}
		assertEquals(8, inbox.createdCount());
	}

	@Test
	void payloadGrowsBeyondTheInitialSize() {
		final OrderInbox inbox = new OrderInbox();
		final byte[] big = new byte[1000];
		for (int i = 0; i < big.length; i++) {
			big[i] = (byte) i;
		}
		inbox.submitCommand(0, big, 0, big.length);
		inbox.drain(1, message -> {
			assertEquals(1000, message.payloadLength);
			assertArrayEquals(big, message.payloadCopy());
		});
	}

	@Test
	void messageSubmittedWhileDrainingWaitsForTheNextDrain() {
		final OrderInbox inbox = new OrderInbox();
		inbox.submitCommand(0, new byte[] { 1 }, 0, 1);
		final AtomicBoolean again = new AtomicBoolean();
		assertEquals(1, inbox.drain(1, message -> {
			if (!again.getAndSet(true)) {
				inbox.submitCommand(0, new byte[] { 2 }, 0, 1);
			}
		}));
		assertEquals(1, inbox.pendingCount());
		assertEquals(1, inbox.drain(2, message -> assertEquals(2, message.payload[0])));
	}

	@Test
	void clearDropsWaitingMessages() {
		final OrderInbox inbox = new OrderInbox();
		inbox.submitCommand(0, new byte[] { 1 }, 0, 1);
		inbox.clear();
		assertEquals(0, inbox.pendingCount());
		assertEquals(0, inbox.drain(1, message -> {
			throw new AssertionError("cleared");
		}));
	}

	/** Several producers against one consumer: nothing lost or duplicated, and each producer's own order kept. */
	@Test
	void concurrentProducersLoseNothingAndKeepTheirOwnOrder() throws Exception {
		final OrderInbox inbox = new OrderInbox();
		final int producers = 4;
		final int perProducer = 50_000;
		final int[] next = new int[producers];
		final Thread[] threads = new Thread[producers];
		for (int p = 0; p < producers; p++) {
			final int id = p;
			threads[p] = new Thread(() -> {
				final byte[] data = new byte[8];
				for (int i = 0; i < perProducer; i++) {
					data[0] = (byte) i;
					data[1] = (byte) (i >> 8);
					data[2] = (byte) (i >> 16);
					inbox.submitCommand(id, data, 0, 3);
				}
			});
			threads[p].start();
		}
		int received = 0;
		final long deadline = System.nanoTime() + 60_000_000_000L;
		while (received < (producers * perProducer)) {
			assertTrue(System.nanoTime() < deadline, "timed out after " + received);
			received += inbox.drain(received, message -> {
				final int index = (message.payload[0] & 0xFF) | ((message.payload[1] & 0xFF) << 8)
						| ((message.payload[2] & 0xFF) << 16);
				assertEquals(next[message.playerIndex], index);
				next[message.playerIndex]++;
			});
			Thread.onSpinWait();
		}
		for (final Thread thread : threads) {
			thread.join();
		}
		assertEquals(0, inbox.pendingCount());
		for (int p = 0; p < producers; p++) {
			assertEquals(perProducer, next[p]);
		}
	}

	@Test
	void sequencesIncreaseAcrossDrains() {
		final OrderInbox inbox = new OrderInbox();
		final long[] last = { -1 };
		for (int round = 0; round < 3; round++) {
			inbox.submitCommand(0, new byte[] { 1 }, 0, 1);
			inbox.submitCommand(0, new byte[] { 1 }, 0, 1);
			inbox.drain(round, message -> {
				assertTrue(message.submitSequence > last[0]);
				last[0] = message.submitSequence;
			});
		}
		assertEquals(5, last[0]);
	}
}
