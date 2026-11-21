package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import com.etheller.warsmash.units.Element;

/**
 * What a sound label sets on a sound that is made from it (SetSoundParamsFromLabel, a sound made with a label),
 * copied out of the label's row once so that the simulation and the render side apply the same values.
 */
public final class SoundLabelParams {
	public static final SoundLabelParams NONE = new SoundLabelParams();

	private boolean hasVolume;
	private int volume;
	private boolean hasPitch;
	private float pitch;
	private float pitchVariance;
	private boolean hasChannel;
	private int channel;
	private boolean hasDistances;
	private float minDistance;
	private float maxDistance;
	private boolean hasCutoff;
	private float cutoff;

	private SoundLabelParams() {
	}

	/** @return the values of the row, {@link #NONE} when the label is unknown */
	public static SoundLabelParams of(final Element row) {
		if (row == null) {
			return NONE;
		}
		final SoundLabelParams params = new SoundLabelParams();
		// Dialogue and MIDI labels only carry a volume.
		if (!row.getField("Volume").isEmpty()) {
			params.hasVolume = true;
			params.volume = row.getFieldValue("Volume");
		}
		if (!row.getField("Pitch").isEmpty()) {
			params.hasPitch = true;
			params.pitch = row.getFieldFloatValue("Pitch");
			params.pitchVariance = hasFlag(row, "RANDOMPITCH") ? row.getFieldFloatValue("PitchVariance") : 0f;
		}
		if (!row.getField("Channel").isEmpty()) {
			params.hasChannel = true;
			params.channel = row.getFieldValue("Channel");
		}
		if (!row.getField("MinDistance").isEmpty()) {
			params.hasDistances = true;
			params.minDistance = row.getFieldFloatValue("MinDistance");
			params.maxDistance = row.getFieldFloatValue("MaxDistance");
			if (row.hasFieldValue("DistanceCutoff")) {
				params.hasCutoff = true;
				params.cutoff = row.getFieldFloatValue("DistanceCutoff");
			}
		}
		return params;
	}

	public void applyTo(final CSound sound) {
		if (this.hasVolume) {
			sound.setVolume(this.volume);
		}
		if (this.hasPitch) {
			sound.setPitch(this.pitch);
			sound.setPitchVariance(this.pitchVariance);
		}
		if (this.hasChannel) {
			sound.setChannel(this.channel);
		}
		if (this.hasDistances) {
			sound.setDistances(this.minDistance, this.maxDistance);
			if (this.hasCutoff) {
				sound.setDistanceCutoff(this.cutoff);
			}
		}
	}

	private static boolean hasFlag(final Element row, final String flag) {
		for (final String value : row.getField("Flags").split("[,|]")) {
			if (flag.equals(value.trim())) {
				return true;
			}
		}
		return false;
	}
}
