package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.Arrays;

/**
 * Everything the render side may know about the simulation after one step: the single source for both the world and
 * the interface, so they always show the same step. Frames are preallocated by a {@link SimFrameRing} and reused.
 * <p>
 * Writer: the simulation thread, between {@link SimFrameRing#beginWrite()} and {@link SimFrameRing#publish()}. Reader:
 * the render thread, only after publication and until it releases the frame; it never writes anything except
 * {@link #eventsApplied}. Nothing in a frame refers to a live simulation object.
 */
public final class SimFrame {
	/** Position in publication order, set by the ring; consecutive from 0. */
	public long sequence;
	/** Simulation step number. */
	public int tick;
	/** Game time at the end of the step, in milliseconds. */
	public long gameTimeMillis;
	public float timeOfDay;
	/** The day and night cycle is forced by a script (the clock shows the second sequence). */
	public boolean falseTimeOfDay;
	public boolean paused;
	/** Speed factor in force for this step (game speed times replay speed). */
	public float timeScale = 1f;

	public final UnitFrameState units = new UnitFrameState();
	public final WorldObjectFrameState projectiles = new WorldObjectFrameState();
	public final WorldObjectFrameState items = new WorldObjectFrameState();
	public final WorldObjectFrameState trees = new WorldObjectFrameState();
	public final UiFrameState ui = new UiFrameState();

	/**
	 * Version of the local player's fog data as of this step (set on every frame). The array below holds data only when
	 * {@link #fogChanged}; otherwise it is stale (slots are reused) and the reader keeps the last data it took.
	 */
	public int fogVersion;
	public boolean fogChanged;
	public byte[] fog = new byte[0];
	public int fogLength;

	private RenderEvent[] events = new RenderEvent[64];
	private int eventCount;
	/** Render thread only: how many of the events it has applied, so a frame seen twice is not applied twice. */
	public int eventsApplied;

	public int eventCount() {
		return eventCount;
	}

	public RenderEvent event(final int index) {
		return events[index];
	}

	/** Simulation thread, while building. */
	public void addEvent(final RenderEvent event) {
		if (eventCount == events.length) {
			events = Arrays.copyOf(events, eventCount * 2);
		}
		events[eventCount++] = event;
	}

	/** Simulation thread, while building: stores new fog data and marks it changed. */
	public void setFog(final byte[] source, final int length, final int version) {
		if (fog.length < length) {
			fog = new byte[length];
		}
		System.arraycopy(source, 0, fog, 0, length);
		fogLength = length;
		fogVersion = version;
		fogChanged = true;
	}

	/**
	 * Prepares the slot for a new step: recycles its events into the pool and empties the state, keeping the arrays.
	 * The fog array is kept for reuse but its content is meaningless until {@link #setFog} runs.
	 */
	void reset(final RenderEventPool pool) {
		for (int i = 0; i < eventCount; i++) {
			pool.recycle(events[i]);
			events[i] = null;
		}
		eventCount = 0;
		eventsApplied = 0;
		units.clear();
		projectiles.clear();
		items.clear();
		trees.clear();
		ui.clear();
		fogChanged = false;
	}
}
