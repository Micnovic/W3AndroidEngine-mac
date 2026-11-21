package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import com.badlogic.gdx.audio.Sound;
import com.etheller.warsmash.viewer5.AudioBufferSource;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.AudioPanner;
import com.etheller.warsmash.viewer5.SoundLoops;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedTransient;

public class CSoundFilename extends AbstractCSound {
	/** What shares the sound of a file among the handles made from it. */
	public interface Owner {
		/** Follows a killed handle so that it is asked to give its sound back once it has played out. */
		void watchKilled(CSoundFilename handle);

		void releaseSound(Sound sound);
	}

	private Sound sound;
	private final boolean looping;
	private final boolean stopWhenOutOfRange;
	private final int fadeInRate;
	private final int fadeOutRate;
	private final AudioContext audioContext;
	private final CSoundParameters parameters = new CSoundParameters();
	private final String eaxSetting;
	private long lastSoundId = -1;
	@SavedTransient
	private Owner owner;
	private int stackedVoices;
	private String fileName;
	// The sound is being made in the background (see awaitSound): a start waits for it, and a handle killed and done
	// meanwhile gives it back as it comes.
	@SavedTransient
	private boolean soundPending;
	@SavedTransient
	private boolean playOnBind;
	@SavedTransient
	private boolean releaseOnBind;

	/**
	 * @param fileDuration the length of the file in seconds (0 when it is missing); the sound may be null when this
	 *                     computer cannot play it
	 */
	public CSoundFilename(final Sound sound, final float fileDuration, final AudioContext audioContext,
			final boolean looping, final boolean stopWhenOutOfRange, final int fadeInRate, final int fadeOutRate,
			final String eaxSetting) {
		super(fileDuration);
		this.sound = sound;
		this.audioContext = audioContext;
		this.looping = looping;
		this.stopWhenOutOfRange = stopWhenOutOfRange;
		this.fadeInRate = fadeInRate;
		this.fadeOutRate = fadeOutRate;
		this.eaxSetting = eaxSetting;
	}

	/** Makes this handle one of the holders of its sound, which it gives back when it is killed and done. */
	public void setOwner(final Owner owner) {
		this.owner = owner;
	}

	/** Makes this handle wait for its sound to be given with {@link #bindSound}. */
	public void awaitSound() {
		this.soundPending = true;
	}

	/**
	 * Gives the handle the sound it awaited (null when it could not be made); a start that came before is played now
	 * when it still sounds.
	 */
	public void bindSound(final Sound bound, final int gameTurnTick) {
		this.soundPending = false;
		if (this.releaseOnBind) {
			this.releaseOnBind = false;
			this.playOnBind = false;
			if ((bound != null) && (this.owner != null)) {
				this.owner.releaseSound(bound);
			}
			return;
		}
		this.sound = bound;
		if (this.playOnBind) {
			this.playOnBind = false;
			if ((bound != null) && isPlaying(gameTurnTick)) {
				play();
			}
		}
	}

	@Override
	public void killWhenDone() {
		super.killWhenDone();
		if (this.owner != null) {
			this.owner.watchKilled(this);
		}
	}

	/**
	 * Gives the sound back when the handle can never play again.
	 *
	 * @return whether the handle holds no sound any more
	 */
	public boolean releaseIfDone(final int gameTurnTick) {
		if (this.soundPending) {
			if (!isKilled(gameTurnTick)) {
				return false;
			}
			this.releaseOnBind = true;
			return true;
		}
		if (this.sound == null) {
			return true;
		}
		if (this.stackedVoices > 0 || !isKilled(gameTurnTick)) {
			return false;
		}
		// The file may still sound for a moment after the handle is done; its owner keeps the sound until then.
		final Sound released = this.sound;
		this.sound = null;
		if (this.owner != null) {
			this.owner.releaseSound(released);
		}
		return true;
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
	public void attachToUnit(final RenderUnit unit) {
		this.parameters.attachedLocation = unit == null ? null : unit.location;
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

	@Override
	protected boolean isLooping() {
		return this.looping;
	}

	@Override
	protected void play() {
		if (this.soundPending) {
			this.playOnBind = true;
			return;
		}
		if ((this.audioContext == null) || (this.sound == null)) {
			return;
		}
		// The platform cannot start a sound part way in, but one started past its end is silent as in the script.
		if (!this.looping && (getPlayPosition() >= getDuration())) {
			return;
		}
		final AudioPanner panner = this.audioContext.createPanner(this.stopWhenOutOfRange);
		final AudioBufferSource source = this.audioContext.createBufferSource();

		this.parameters.updatePositionFromUnit();
		if (this.parameters.attachedLocation != null) {
			panner.followLocation = this.parameters.attachedLocation;
		}

		// A 3D sound nobody placed plays at the listener rather than at the map origin.
		if (this.parameters.positioned) {
			panner.setPosition(this.parameters.x, this.parameters.y, this.parameters.z);
		}
		else {
			panner.setPosition(this.audioContext.listener.getX(), this.audioContext.listener.getY(),
					this.audioContext.listener.getZ());
			panner.followListener = true;
		}
		panner.setDistances(Math.min(this.parameters.distanceCutoff, this.parameters.maxDistance),
				this.parameters.minDistance);
		panner.connect(this.audioContext.destination);

		// Source.
		source.buffer = this.sound;
		source.connect(panner);

		// Make a sound.
		if (this.looping) {
			this.lastSoundId = source.startLoop(this.parameters.unmixedVolume(1.0f, this.audioContext),
					this.parameters.channel, this.parameters.startPitch(), false);
		}
		else {
			this.lastSoundId = source.start(0, this.parameters.scaledVolume(1.0f, this.audioContext),
					this.parameters.startPitch(), false);
		}
	}

	@Override
	protected void silence() {
		this.playOnBind = false;
		if (this.sound != null) {
			// Handles of the same file share its sound, so without an id there is nothing of this one to stop.
			if (this.lastSoundId != -1) {
				SoundLoops.stop(this.sound, this.lastSoundId);
			}
		}
	}

	/**
	 * A stacked sound plays as one loop for all its areas; CStackedSounds sets its loudness and side from the nearest
	 * area every frame, so it is started unplaced.
	 */
	long startStacked() {
		if ((this.sound == null) || (this.audioContext == null)) {
			return -1;
		}
		final long id = Extensions.audio.play(this.sound, 0f, this.parameters.startPitch(), 0, 0, 0, false, 1, 1,
				true);
		if (id != -1) {
			this.stackedVoices++;
		}
		return id;
	}

	void setStackedLevel(final long id, final float gain, final float pan) {
		final float volume = this.parameters.scaledVolume(1.0f, null) * gain;
		if (this.sound != null) {
			this.sound.setPan(id, pan, volume);
		}
	}

	void stopStacked(final long id) {
		if (this.sound != null) {
			this.sound.stop(id);
			this.stackedVoices = Math.max(0, this.stackedVoices - 1);
		}
	}

	/** The file the script created the sound from, as the script wrote it. */
	public void setFileName(final String fileName) {
		this.fileName = fileName;
	}

	public String getFileName() {
		return this.fileName;
	}

	CSoundParameters getParameters() {
		return this.parameters;
	}
}
