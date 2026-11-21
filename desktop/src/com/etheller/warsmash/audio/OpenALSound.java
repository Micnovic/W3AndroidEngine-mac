/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.etheller.warsmash.audio;

import static org.lwjgl.openal.AL10.AL_BUFFER;
import static org.lwjgl.openal.AL10.AL_FALSE;
import static org.lwjgl.openal.AL10.AL_FORMAT_MONO16;
import static org.lwjgl.openal.AL10.AL_FORMAT_STEREO16;
import static org.lwjgl.openal.AL10.AL_GAIN;
import static org.lwjgl.openal.AL10.AL_LOOPING;
import static org.lwjgl.openal.AL10.AL_TRUE;
import static org.lwjgl.openal.AL10.alBufferData;
import static org.lwjgl.openal.AL10.alDeleteBuffers;
import static org.lwjgl.openal.AL10.alGenBuffers;
import static org.lwjgl.openal.AL10.alSourceQueueBuffers;
import static org.lwjgl.openal.AL10.alSourcePlay;
import static org.lwjgl.openal.AL10.alSourcef;
import static org.lwjgl.openal.AL10.alSourcei;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;

import com.badlogic.gdx.audio.Sound;

/** @author Nathan Sweet */
public class OpenALSound implements Sound {
	private int bufferID = -1;
	/** The buffers of a sound filled a few at a time (see {@link #beginSpread}), played queued in this order. */
	private int[] spreadBuffers;
	private IntBuffer spreadQueue;
	private OggPcm spreadSource;
	private int spreadChunkBytes;
	private int spreadUploaded;
	private final OpenALAudio audio;
	private float duration;

	public OpenALSound(final OpenALAudio audio) {
		this.audio = audio;
	}

	void setup(final byte[] pcm, final int channels, final int sampleRate) {
		setup(new OggPcm(pcm, channels, sampleRate));
	}

	void setup(final OggPcm pcm) {
		final ByteBuffer buffer = pcm.buffer();
		final int samples = buffer.limit() / (2 * pcm.channels);
		this.duration = samples / (float) pcm.sampleRate;

		if (this.bufferID == -1) {
			this.bufferID = alGenBuffers();
			alBufferData(this.bufferID, pcm.channels > 1 ? AL_FORMAT_STEREO16 : AL_FORMAT_MONO16, buffer.asShortBuffer(),
					pcm.sampleRate);
		}
	}

	/**
	 * Starts a sound of many megabytes whose buffer is filled a piece at a time, see {@link #uploadNextChunk()}: a
	 * single upload of an ambient loop is a stall on its own. Played, the pieces are queued one after the other, which
	 * also loops seamlessly. The first buffer stands for the sound where sources are looked up by their buffer.
	 */
	void beginSpread(final OggPcm pcm, final int chunkBytes) {
		final int total = pcm.buffer().limit();
		this.spreadSource = pcm;
		this.spreadChunkBytes = chunkBytes;
		this.spreadUploaded = 0;
		this.duration = (total / (2 * pcm.channels)) / (float) pcm.sampleRate;
		this.spreadBuffers = new int[(total + chunkBytes - 1) / chunkBytes];
		for (int i = 0; i < this.spreadBuffers.length; i++) {
			this.spreadBuffers[i] = alGenBuffers();
		}
		this.bufferID = this.spreadBuffers[0];
		this.spreadQueue = BufferUtils.createIntBuffer(this.spreadBuffers.length);
		this.spreadQueue.put(this.spreadBuffers).flip();
	}

	/** @return whether the sound is complete; false while pieces are left to upload */
	boolean uploadNextChunk() {
		final ByteBuffer all = this.spreadSource.buffer();
		final int from = this.spreadUploaded * this.spreadChunkBytes;
		final int to = Math.min(all.limit(), from + this.spreadChunkBytes);
		final ByteBuffer piece = all.duplicate().order(ByteOrder.nativeOrder());
		piece.limit(to).position(from);
		alBufferData(this.spreadBuffers[this.spreadUploaded],
				this.spreadSource.channels > 1 ? AL_FORMAT_STEREO16 : AL_FORMAT_MONO16, piece.asShortBuffer(),
				this.spreadSource.sampleRate);
		this.spreadUploaded++;
		if (this.spreadUploaded < this.spreadBuffers.length) {
			return false;
		}
		this.spreadSource = null;
		return true;
	}

	private int[] bufferIds() {
		return this.spreadBuffers != null ? this.spreadBuffers : new int[] { this.bufferID };
	}

	private void attach(final int sourceID) {
		if (this.spreadBuffers == null) {
			alSourcei(sourceID, AL_BUFFER, this.bufferID);
		}
		else {
			this.spreadQueue.rewind();
			alSourceQueueBuffers(sourceID, this.spreadQueue);
		}
	}

	@Override
	public long play() {
		return play(1);
	}

	@Override
	public long play(final float volume) {
		if (this.audio.noDevice) {
			return 0;
		}
		int sourceID = this.audio.obtainSource(false);
		if (sourceID == -1) {
			// Attempt to recover by stopping the least recently played sound
			this.audio.retain(this, true);
			sourceID = this.audio.obtainSource(false);
		}
		else {
			this.audio.retain(this, false);
		}
		// In case it still didn't work
		if (sourceID == -1) {
			return -1;
		}
		final long soundId = this.audio.getSoundId(sourceID);
		attach(sourceID);
		alSourcei(sourceID, AL_LOOPING, AL_FALSE);
		alSourcef(sourceID, AL_GAIN, volume);
		alSourcePlay(sourceID);
		return soundId;
	}

	@Override
	public long loop() {
		return loop(1);
	}

	@Override
	public long loop(final float volume) {
		if (this.audio.noDevice) {
			return 0;
		}
		final int sourceID = this.audio.obtainSource(false);
		if (sourceID == -1) {
			return -1;
		}
		final long soundId = this.audio.getSoundId(sourceID);
		attach(sourceID);
		alSourcei(sourceID, AL_LOOPING, AL_TRUE);
		alSourcef(sourceID, AL_GAIN, volume);
		alSourcePlay(sourceID);
		return soundId;
	}

	@Override
	public void stop() {
		if (this.audio.noDevice) {
			return;
		}
		this.audio.stopSourcesWithBuffer(bufferIds());
	}

	@Override
	public void dispose() {
		if (this.audio.noDevice) {
			return;
		}
		if (this.bufferID == -1) {
			return;
		}
		this.audio.freeBuffer(bufferIds());
		if (this.spreadBuffers != null) {
			for (final int id : this.spreadBuffers) {
				alDeleteBuffers(id);
			}
			this.spreadBuffers = null;
			this.spreadSource = null;
		}
		else {
			alDeleteBuffers(this.bufferID);
		}
		this.bufferID = -1;
		this.audio.forget(this);
	}

	@Override
	public void stop(final long soundId) {
		if (this.audio.noDevice) {
			return;
		}
		this.audio.stopSound(soundId);
	}

	@Override
	public void pause() {
		if (this.audio.noDevice) {
			return;
		}
		this.audio.pauseSourcesWithBuffer(bufferIds());
	}

	@Override
	public void pause(final long soundId) {
		if (this.audio.noDevice) {
			return;
		}
		this.audio.pauseSound(soundId);
	}

	@Override
	public void resume() {
		if (this.audio.noDevice) {
			return;
		}
		this.audio.resumeSourcesWithBuffer(bufferIds());
	}

	@Override
	public void resume(final long soundId) {
		if (this.audio.noDevice) {
			return;
		}
		this.audio.resumeSound(soundId);
	}

	@Override
	public void setPitch(final long soundId, final float pitch) {
		if (this.audio.noDevice) {
			return;
		}
		this.audio.setSoundPitch(soundId, pitch);
	}

	@Override
	public void setVolume(final long soundId, final float volume) {
		if (this.audio.noDevice) {
			return;
		}
		this.audio.setSoundGain(soundId, volume);
	}

	@Override
	public void setLooping(final long soundId, final boolean looping) {
		if (this.audio.noDevice) {
			return;
		}
		this.audio.setSoundLooping(soundId, looping);
	}

	@Override
	public void setPan(final long soundId, final float pan, final float volume) {
		if (this.audio.noDevice) {
			return;
		}
		this.audio.setSoundPan(soundId, pan, volume);
	}

	public void setPosition(final long soundId, final float x, final float y, final float z, final boolean is3DSound,
			final float maxDistance, final float refDistance) {
		if (this.audio.noDevice) {
			return;
		}
		this.audio.setSoundPosition(soundId, x, y, z, is3DSound, maxDistance, refDistance);
	}

	public void setOffset(final long soundId, final float seconds) {
		if (this.audio.noDevice) {
			return;
		}
		this.audio.setSoundOffset(soundId, seconds);
	}

	@Override
	public long play(final float volume, final float pitch, final float pan) {
		final long id = play();
		setPitch(id, pitch);
		setPan(id, pan, volume);
		return id;
	}

	public long play(final float volume, final float pitch, final float x, final float y, final float z,
			final boolean is3DSound, final float maxDistance, final float refDistance, final boolean looping) {
		final long id = looping ? loop() : play();
		setPitch(id, pitch);
		setVolume(id, volume);
		setPosition(id, x, y, z, is3DSound, maxDistance, refDistance);
		return id;
	}

	@Override
	public long loop(final float volume, final float pitch, final float pan) {
		final long id = loop();
		setPitch(id, pitch);
		setPan(id, pan, volume);
		return id;
	}

	/** Returns the length of the sound in seconds. */
	public float duration() {
		return this.duration;
	}
}
