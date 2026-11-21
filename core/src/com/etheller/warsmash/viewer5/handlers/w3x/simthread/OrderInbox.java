package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.ArrayDeque;
import java.util.function.Consumer;

/**
 * The only way the interface (and the network thread) tell the simulation to do something once it runs on its own
 * thread.
 * <p>
 * Producers: the render thread (input), and in a LAN game the network thread; any number of threads may submit.
 * Consumer: the simulation thread, which drains at the start of a step and also while paused. Messages come out in
 * the order they were submitted (for a single producer exactly; between producers in the order they got the lock).
 * Submitting never blocks beyond a short lock and never allocates once the pool has warmed up.
 */
public final class OrderInbox {
	private final Object lock = new Object();
	private final ArrayDeque<OrderMessage> pending = new ArrayDeque<>();
	private final ArrayDeque<OrderMessage> free = new ArrayDeque<>();
	private final ArrayDeque<OrderMessage> draining = new ArrayDeque<>();
	private long nextSequence;
	private int created;

	/** Producer. A cleared message to fill; pass it to {@link #submit}. */
	public OrderMessage obtain() {
		synchronized (lock) {
			final OrderMessage message = free.pollLast();
			if (message != null) {
				return message;
			}
			created++;
			return new OrderMessage();
		}
	}

	/** Producer. Queues a message from {@link #obtain()}; it belongs to the inbox from here on. */
	public void submit(final OrderMessage message) {
		message.submitNanos = System.nanoTime();
		synchronized (lock) {
			message.submitSequence = nextSequence++;
			pending.addLast(message);
		}
	}

	/** Producer: convenience for an encoded command. */
	public void submitCommand(final int playerIndex, final byte[] encoded, final int offset, final int length) {
		final OrderMessage message = obtain();
		message.kind = OrderMessage.COMMAND;
		message.playerIndex = playerIndex;
		message.setPayload(encoded, offset, length);
		submit(message);
	}

	/** Messages waiting; any thread, only a hint. */
	public int pendingCount() {
		synchronized (lock) {
			return pending.size();
		}
	}

	/**
	 * Consumer. Hands every waiting message, oldest first, to the consumer with {@code appliedTick} set to the tick
	 * given, then recycles them. Messages submitted while draining wait for the next drain. Returns how many were
	 * handled. Must be called by one thread at a time (the simulation thread).
	 */
	public int drain(final int tick, final Consumer<OrderMessage> consumer) {
		synchronized (lock) {
			if (pending.isEmpty() && draining.isEmpty()) {
				return 0;
			}
			draining.addAll(pending);
			pending.clear();
		}
		final int count = draining.size();
		OrderMessage message;
		while ((message = draining.pollFirst()) != null) {
			message.appliedTick = tick;
			try {
				consumer.accept(message);
			}
			finally {
				message.clear();
				synchronized (lock) {
					free.addLast(message);
				}
			}
		}
		return count;
	}

	/** Neither thread running (load, restart): drops everything waiting. */
	public void clear() {
		synchronized (lock) {
			for (final OrderMessage message : pending) {
				message.clear();
				free.addLast(message);
			}
			pending.clear();
		}
	}

	/** How many messages were ever allocated; stays flat in steady state. */
	public int createdCount() {
		synchronized (lock) {
			return created;
		}
	}
}
