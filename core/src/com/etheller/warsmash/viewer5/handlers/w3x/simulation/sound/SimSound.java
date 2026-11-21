package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedTransient;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/**
 * The sound handle a script holds: everything a script can read of a sound (length, whether it plays, its dialogue
 * keys) and everything it set on it (kept for a loaded game) is owned by the simulation and answered without the
 * render side. What the script does to the sound is also sent to the render side as events that make the
 * {@link #getVoice() voice}, the object that holds the audio, do the same in the same order; the voice is only ever
 * touched there.
 */
public final class SimSound extends AbstractCSound {
	public enum Kind {
		/** CreateSound: one file. */
		FILE,
		/** CreateSoundFromLabel: a file of the label's files each start. */
		LABEL,
		/** CreateMIDISound of a label with no files, which has nothing to play. */
		MIDI
	}

	private final Kind kind;
	/** The file name or the label the script made the sound of. */
	private final String source;
	private final boolean looping;
	private final boolean is3D;
	private final boolean stopWhenOutOfRange;
	private final int fadeInRate;
	private final int fadeOutRate;
	private final String eaxSetting;
	/** The label whose values the sound took when it was made of a file, or null. */
	private final String paramsLabel;
	private final SoundLabelInfo labelInfo;
	private int starts;
	private int variant;
	private final CSoundParameters parameters = new CSoundParameters();
	private CUnit attachedUnit;
	@SavedTransient
	private CSound voice;

	public SimSound(final Kind kind, final String source, final boolean looping, final boolean is3D,
			final boolean stopWhenOutOfRange, final int fadeInRate, final int fadeOutRate, final String eaxSetting,
			final String paramsLabel, final SoundLabelInfo labelInfo, final float fileDuration) {
		super(fileDuration);
		this.kind = kind;
		this.source = source;
		this.looping = looping;
		this.is3D = is3D;
		this.stopWhenOutOfRange = stopWhenOutOfRange;
		this.fadeInRate = fadeInRate;
		this.fadeOutRate = fadeOutRate;
		this.eaxSetting = eaxSetting;
		this.paramsLabel = paramsLabel;
		this.labelInfo = labelInfo;
	}

	public Kind getKind() {
		return this.kind;
	}

	public String getSource() {
		return this.source;
	}

	public boolean getLooping() {
		return this.looping;
	}

	public boolean is3D() {
		return this.is3D;
	}

	public boolean isStopWhenOutOfRange() {
		return this.stopWhenOutOfRange;
	}

	public int getFadeInRate() {
		return this.fadeInRate;
	}

	public int getFadeOutRate() {
		return this.fadeOutRate;
	}

	public String getEaxSetting() {
		return this.eaxSetting;
	}

	public String getParamsLabel() {
		return this.paramsLabel;
	}

	@Override
	protected boolean isLooping() {
		return this.looping;
	}

	/** Render thread only: the object that plays this sound, null until the render side has made it. */
	public CSound getVoice() {
		return this.voice;
	}

	public void setVoice(final CSound voice) {
		this.voice = voice;
	}

	/** The same choice as the voice of a label makes, from the same numbers. */
	@Override
	protected void starting(final int gameTurnTick) {
		if ((this.kind != Kind.LABEL) || (this.labelInfo.getFileCount() == 0)) {
			return;
		}
		this.variant = CSoundFromLabel.variant(gameTurnTick, this.starts++, this.labelInfo.getFileCount());
		setFileDuration(this.labelInfo.getDuration(this.variant));
	}

	@Override
	protected void play() {
	}

	@Override
	protected void silence() {
	}

	@Override
	public void setPosition(final float x, final float y, final float z) {
		this.parameters.positioned = true;
		this.parameters.x = x;
		this.parameters.y = y;
		this.parameters.z = z;
	}

	@Override
	public void setVolume(final int volume) {
		this.parameters.volume = volume;
	}

	@Override
	public void setPitch(final float pitch) {
		this.parameters.pitch = pitch;
	}

	@Override
	public void setPitchVariance(final float variance) {
		this.parameters.pitchVariance = variance;
	}

	@Override
	public void setChannel(final int channel) {
		this.parameters.channel = channel;
	}

	@Override
	public void setDistances(final float minDistance, final float maxDistance) {
		this.parameters.minDistance = minDistance;
		this.parameters.maxDistance = maxDistance;
	}

	@Override
	public void setDistanceCutoff(final float cutoff) {
		this.parameters.distanceCutoff = cutoff;
	}

	@Override
	public void setConeAngles(final float inside, final float outside, final int outsideVolume) {
		this.parameters.coneInside = inside;
		this.parameters.coneOutside = outside;
		this.parameters.coneOutsideVolume = outsideVolume;
	}

	@Override
	public void setConeOrientation(final float x, final float y, final float z) {
		this.parameters.coneX = x;
		this.parameters.coneY = y;
		this.parameters.coneZ = z;
	}

	@Override
	public void setVelocity(final float x, final float y, final float z) {
		this.parameters.velocityX = x;
		this.parameters.velocityY = y;
		this.parameters.velocityZ = z;
	}

	@Override
	public void setDialogueSpeakerNameKey(final String key) {
		this.parameters.dialogueSpeakerNameKey = key;
	}

	@Override
	public String getDialogueSpeakerNameKey() {
		return this.parameters.dialogueSpeakerNameKey;
	}

	@Override
	public void setDialogueTextKey(final String key) {
		this.parameters.dialogueTextKey = key;
	}

	@Override
	public String getDialogueTextKey() {
		return this.parameters.dialogueTextKey;
	}

	public void setAttachedUnit(final CUnit unit) {
		this.attachedUnit = unit;
	}

	public CUnit getAttachedUnit() {
		return this.attachedUnit;
	}

	/**
	 * A loaded game has no voice: the one made for it takes over what the script had done to the sound by the save.
	 * Render thread, before any event of the loaded game reaches the voice.
	 */
	public void restoreVoice(final CSound restored) {
		if (restored instanceof AbstractCSound) {
			((AbstractCSound) restored).restoreScriptState(this);
		}
		if (restored instanceof CSoundFilename) {
			((CSoundFilename) restored).getParameters().copyFrom(this.parameters);
		}
		else if (restored instanceof CSoundFromLabel) {
			((CSoundFromLabel) restored).getParameters().copyFrom(this.parameters);
			((CSoundFromLabel) restored).restoreStarts(this.starts, this.variant);
		}
		this.voice = restored;
	}
}
