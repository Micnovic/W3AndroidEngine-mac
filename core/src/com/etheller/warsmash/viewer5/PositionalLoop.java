package com.etheller.warsmash.viewer5;

import java.util.function.LongSupplier;

import com.badlogic.gdx.audio.Sound;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;

/**
 * A looping sound heard from a place in the world. Its loudness and side depend on where the camera is, so they are
 * set again every frame; it takes a voice of the sound device only while it can be heard.
 */
final class PositionalLoop {
	private static final float LEVEL_STEP = 0.002f;
	private static final float AUDIBLE = 0.001f;
	private static final int RETRY_FRAMES = 30;
	/** A voice that starts this far into its sound or less is not moved. */
	private static final float MIN_SEEK_SECONDS = 0.05f;
	/** The time loops count their age by, in nanoseconds. */
	static LongSupplier clock = System::nanoTime;

	private final Sound sound;
	private final AudioPanner panner;
	private final float volume;
	private final int channel;
	private final float pitch;
	private final boolean heardOnlyWhereSeen;
	/** When the loop began: a loop heard again carries on from where it would be had it sounded all along. */
	private long startedAt;
	/** The device's voice, -1 while the loop is not audible. */
	private long id = -1;
	private long virtualId = -1;
	private boolean stopped;
	private int retryDelay;
	private float lastVolume = -1;
	private float lastPan = Float.NaN;
	private float lastX = Float.NaN;
	private float lastY;
	private float lastZ;

	PositionalLoop(final Sound sound, final AudioPanner panner, final float volume, final int channel,
			final float pitch, final boolean heardOnlyWhereSeen) {
		this.sound = sound;
		this.panner = panner;
		this.volume = volume;
		this.channel = channel;
		this.pitch = pitch;
		this.heardOnlyWhereSeen = heardOnlyWhereSeen;
	}

	/**
	 * @return the id the caller keeps, valid until the loop is stopped
	 */
	long begin() {
		this.startedAt = clock.getAsLong();
		this.virtualId = SoundLoops.register(this);
		update();
		return this.virtualId;
	}

	/**
	 * Where a loop of this length is, this long after it began.
	 *
	 * @param elapsedSeconds the time since the loop began
	 * @param pitch          how much faster than its own speed the loop plays
	 * @param length         the length of the sound in seconds, 0 when unknown
	 * @return seconds into the sound
	 */
	static float resumeOffset(final float elapsedSeconds, final float pitch, final float length) {
		if ((length <= 0) || (elapsedSeconds <= 0)) {
			return 0;
		}
		final float played = elapsedSeconds * (pitch > 0 ? pitch : 1);
		return played % length;
	}

	boolean isOf(final Sound other) {
		return this.sound == other;
	}

	/** Ends the loop: its voice, if it has one, and every list that follows it. */
	void stop() {
		if (this.stopped) {
			return;
		}
		this.stopped = true;
		releaseVoice();
		SoundLoops.forget(this.virtualId);
		if (this.panner.context != null) {
			this.panner.context.removeLoop(this);
		}
	}

	/** Whether the device voice with this id is this loop's, which asks for another when it is lost. */
	boolean holds(final long voiceId) {
		return !this.stopped && (this.id != -1) && (this.id == voiceId);
	}

	void voiceLost(final long lostId) {
		if (!this.stopped && (this.id != -1) && (this.id == lostId)) {
			this.id = -1;
			this.lastVolume = -1;
			this.lastPan = Float.NaN;
			this.retryDelay = RETRY_FRAMES;
		}
	}

	private void releaseVoice() {
		if (this.id != -1) {
			this.sound.stop(this.id);
			this.id = -1;
		}
		this.lastVolume = -1;
		this.lastPan = Float.NaN;
		this.lastX = Float.NaN;
	}

	void update() {
		if (this.stopped) {
			return;
		}
		this.panner.refreshPosition();
		final boolean heard = isHeard();
		final float mixed = heard ? this.volume * CSoundMixer.audible().channelScale(this.channel) : 0;
		final boolean threeD = this.panner.listener.is3DSupported();
		final float level = threeD ? mixed : mixed * this.panner.getDistanceGain();
		// A voice is scarce: a loop out of range, unseen or muted holds none, and gets one when it is heard again.
		if (level <= AUDIBLE) {
			if (this.id != -1) {
				releaseVoice();
			}
			return;
		}
		if (this.id == -1) {
			if (this.retryDelay > 0) {
				this.retryDelay--;
				return;
			}
			startVoice(threeD, level);
			return;
		}
		if (threeD) {
			if (Math.abs(level - this.lastVolume) > LEVEL_STEP) {
				this.lastVolume = level;
				this.sound.setVolume(this.id, level);
			}
			followPlace();
			return;
		}
		final float pan = pan();
		if ((Math.abs(level - this.lastVolume) > LEVEL_STEP) || (Math.abs(pan - this.lastPan) > LEVEL_STEP)) {
			this.lastVolume = level;
			this.lastPan = pan;
			this.sound.setPan(this.id, pan, level);
		}
	}

	/** A voice placed in the world stays where its source is when the source moves. */
	private void followPlace() {
		if (((this.panner.followLocation == null) && !this.panner.followListener)
				|| ((this.panner.x == this.lastX) && (this.panner.y == this.lastY) && (this.panner.z == this.lastZ))) {
			return;
		}
		this.lastX = this.panner.x;
		this.lastY = this.panner.y;
		this.lastZ = this.panner.z;
		Extensions.audio.setPosition(this.sound, this.id, this.lastX, this.lastY, this.lastZ,
				this.panner.maxDistance, this.panner.refDistance);
	}

	private void startVoice(final boolean threeD, final float level) {
		this.lastVolume = level;
		this.lastX = this.panner.x;
		this.lastY = this.panner.y;
		this.lastZ = this.panner.z;
		if (threeD) {
			this.id = Extensions.audio.play(this.sound, level, this.pitch, this.panner.x, this.panner.y,
					this.panner.z, true, this.panner.maxDistance, this.panner.refDistance, true);
		}
		else {
			this.lastPan = pan();
			this.id = Extensions.audio.playPanned(this.sound, level, this.pitch, this.lastPan, true);
		}
		if (this.id == -1) {
			this.retryDelay = RETRY_FRAMES;
			return;
		}
		final float offset = resumeOffset((clock.getAsLong() - this.startedAt) / 1e9f, this.pitch,
				Extensions.audio.duration(this.sound));
		if (offset >= MIN_SEEK_SECONDS) {
			Extensions.audio.seek(this.sound, this.id, offset);
		}
	}

	private boolean isHeard() {
		if (!this.panner.isWithinListenerDistance()) {
			return false;
		}
		final AudioContext.SoundVisibility visibility = this.panner.context == null ? null
				: this.panner.context.visibility;
		return !this.heardOnlyWhereSeen || (visibility == null) || visibility.isHeardAt(this.panner.x, this.panner.y);
	}

	private float pan() {
		return this.panner.listener instanceof AudioContext.Listener.Placed
				? ((AudioContext.Listener.Placed) this.panner.listener).pan(this.panner.x, this.panner.y,
						this.panner.z)
				: 0;
	}
}
