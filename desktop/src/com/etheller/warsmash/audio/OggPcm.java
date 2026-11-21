package com.etheller.warsmash.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** A decoded ogg sound, ready to be given to OpenAL. */
final class OggPcm {
	private byte[] pcm;
	private ByteBuffer direct;
	final int channels;
	final int sampleRate;

	OggPcm(final byte[] pcm, final int channels, final int sampleRate) {
		this.pcm = pcm;
		this.channels = channels;
		this.sampleRate = sampleRate;
	}

	/** The bytes the sound holds, as {@link #buffer()} gives them (whole sample frames only). */
	int size() {
		return this.direct != null ? this.direct.limit() : this.pcm.length - (this.pcm.length % frameBytes());
	}

	private int frameBytes() {
		return this.channels > 1 ? 4 : 2;
	}

	/**
	 * Makes the native buffer OpenAL reads from, which for a long ambient loop is a copy of megabytes: done by the
	 * helper that decoded the sound, the game thread is left with the upload only.
	 */
	void prepare() {
		if (this.direct == null) {
			final int bytes = size();
			final ByteBuffer buffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
			buffer.put(this.pcm, 0, bytes);
			buffer.flip();
			this.direct = buffer;
			this.pcm = null;
		}
	}

	ByteBuffer buffer() {
		prepare();
		return this.direct;
	}
}
