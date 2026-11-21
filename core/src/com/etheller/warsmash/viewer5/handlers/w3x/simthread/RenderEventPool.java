package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.ArrayDeque;

/**
 * Recycles {@link RenderEvent}s. Events are taken by the simulation thread when it records them and returned when the
 * frame holding them is reused (also the simulation thread), or by {@link SimFrameRing#clear()}. The lock is there for
 * the restart and load paths and costs a few uncontended operations per tick.
 */
public final class RenderEventPool {
	private final ArrayDeque<RenderEvent> free = new ArrayDeque<>();
	private int created;

	/** Simulation thread. */
	public synchronized RenderEvent obtain() {
		final RenderEvent event = free.pollLast();
		if (event != null) {
			return event;
		}
		created++;
		return new RenderEvent();
	}

	/** Whoever drops a frame that still holds events; normally the simulation thread. */
	public synchronized void recycle(final RenderEvent event) {
		event.clear();
		free.addLast(event);
	}

	/** How many instances were ever allocated; stays flat in steady state. */
	public synchronized int createdCount() {
		return created;
	}
}
