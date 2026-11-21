package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

/**
 * How the simulation is scheduled. Chosen once at start-up and read-only afterwards, so any thread may read it.
 */
public enum SimThreadMode {
	/** Simulation steps run inside the render frame as before; no frames are published (also headless and LAN headless). */
	OFF,
	/** Frames, events, inbox and presentation clock are all in use, but the steps still run on the render thread. */
	SERIAL,
	/** The simulation runs on its own thread. */
	ON;

	public static final String PROPERTY = "warsmash.simthread";

	private static SimThreadMode current;

	/** Parses off / serial / on, ignoring case; anything else, including null, is {@link #OFF}. */
	public static SimThreadMode parse(final String value) {
		if (value != null) {
			for (final SimThreadMode mode : values()) {
				if (mode.name().equalsIgnoreCase(value.trim())) {
					return mode;
				}
			}
		}
		return OFF;
	}

	public static synchronized SimThreadMode current() {
		if (current == null) {
			current = parse(System.getProperty(PROPERTY));
		}
		return current;
	}

	/** Start-up only, before any simulation exists: lets a launcher pass the mode where there are no properties. */
	public static synchronized void set(final SimThreadMode mode) {
		current = mode == null ? OFF : mode;
	}

	/** True when frames are produced and consumed, that is, in every mode except {@link #OFF}. */
	public boolean usesFrames() {
		return this != OFF;
	}

	public boolean isThreaded() {
		return this == ON;
	}
}
