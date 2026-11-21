package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

/**
 * The files of a sound label and the length of each, as scripts wait by them. Read once on the render side; the
 * simulation picks a file of the label by the step and the count of starts and needs only these numbers.
 */
public final class SoundLabelInfo {
	private final float[] durations;

	public SoundLabelInfo(final float[] durations) {
		this.durations = durations;
	}

	public int getFileCount() {
		return this.durations.length;
	}

	public float getDuration(final int index) {
		return this.durations[index];
	}

	/** The length of the first file, which a sound that has not been started yet waits by; 0 when there is none. */
	public float getFirstDuration() {
		return this.durations.length == 0 ? 0 : this.durations[0];
	}
}
