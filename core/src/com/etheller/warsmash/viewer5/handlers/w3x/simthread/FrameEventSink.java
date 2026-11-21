package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.function.IntSupplier;

/**
 * Collects the events the simulation records into the frame that the step in progress publishes. Events made outside
 * a step (while a map starts, in a command applied between steps) join the next frame to be published.
 * <p>
 * Simulation thread only; the ring has one writer.
 */
public final class FrameEventSink implements RenderEventSink {
	private final SimFrameRing ring;
	private final RenderEventPool pool;
	private final IntSupplier tick;
	private int lastTick = Integer.MIN_VALUE;
	private int order;

	public FrameEventSink(final SimFrameRing ring, final RenderEventPool pool, final IntSupplier tick) {
		this.ring = ring;
		this.pool = pool;
		this.tick = tick;
	}

	@Override
	public RenderEvent begin(final int code) {
		final int now = this.tick.getAsInt();
		if (now != this.lastTick) {
			this.lastTick = now;
			this.order = 0;
		}
		final RenderEvent event = this.pool.obtain();
		event.code = code;
		event.tick = now;
		event.order = this.order++;
		return event;
	}

	@Override
	public void emit(final RenderEvent event) {
		final SimFrame frame = this.ring.beginWrite();
		if (frame == null) {
			throw new IllegalStateException("The frame ring is full: the render side did not release frames");
		}
		frame.addEvent(event);
	}

	/** True when events wait in the frame being built, so they are not lost if no further step publishes it. */
	public boolean hasPending() {
		final SimFrame frame = this.ring.beginWrite();
		return (frame != null) && (frame.eventCount() > 0);
	}
}
