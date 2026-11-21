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

import java.io.Closeable;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.StreamUtils;
import com.etheller.warsmash.util.Log;

import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.DataFormatException;
import io.nayuki.flac.decode.FlacDecoder;

public class Flac {
	static public class Music extends OpenALMusic {
		private PcmStream input;

		public Music(final OpenALAudio audio, final FileHandle file) {
			super(audio, file);
			this.input = open(file);
			if (audio.noDevice) {
				return;
			}
			setup(this.input.channels, this.input.sampleRate);
		}

		@Override
		public int read(final byte[] buffer) {
			if (this.input == null) {
				this.input = open(this.file);
				setup(this.input.channels, this.input.sampleRate);
			}
			try {
				return this.input.read(buffer, 0, buffer.length);
			}
			catch (final IOException ex) {
				throw new GdxRuntimeException("Error reading FLAC file: " + this.file, ex);
			}
		}

		@Override
		public void reset() {
			StreamUtils.closeQuietly(this.input);
			this.input = null;
		}

		private static PcmStream open(final FileHandle file) {
			try {
				return new PcmStream(file.readBytes());
			}
			catch (final IOException ex) {
				throw new GdxRuntimeException("Error reading FLAC file: " + file, ex);
			}
		}
	}

	static public class Sound extends OpenALSound {
		public Sound(final OpenALAudio audio, final FileHandle file) {
			super(audio);
			if (audio.noDevice) {
				return;
			}

			OggPcm pcm = audio.takePrefetched(file);
			if (pcm == null) {
				try {
					pcm = decode(file.readBytes());
				}
				catch (final IOException ex) {
					throw new GdxRuntimeException("Error reading FLAC file: " + file, ex);
				}
			}
			setup(pcm);
		}
	}

	static OggPcm decode(final byte[] fileBytes) throws IOException {
		try (PcmStream input = new PcmStream(fileBytes)) {
			final byte[] pcm = new byte[(int) input.dataLength];
			int total = 0;
			while (total < pcm.length) {
				final int count = input.read(pcm, total, pcm.length - total);
				if (count < 0) {
					break;
				}
				total += count;
			}
			if (total == pcm.length) {
				input.read(new byte[1], 0, 1);
			}
			return new OggPcm(total == pcm.length ? pcm : Arrays.copyOf(pcm, total), input.channels,
					input.sampleRate);
		}
	}

	/**
	 * Decodes a FLAC track block by block into 16-bit (or the source depth, up to 16) little endian PCM, as it is
	 * read. Only one decoded block lives in memory, so long tracks and ambient loops need no sample arrays. Layouts
	 * wider than stereo are folded to stereo, even channels to the left and odd ones to the right, since libgdx plays
	 * only mono and stereo.
	 */
	static final class PcmStream implements Closeable {
		private final FlacDecoder decoder;
		private final StreamInfo streamInfo;
		private final int sourceChannels;
		private final int[][] block;
		private final int[] counts = new int[2];
		private final byte[] frame;
		private final int bytesPerSample;
		private final int downsampleBytes;
		private final MessageDigest md5;
		private final byte[] md5Buffer;
		private int blockLength;
		private int blockPosition;
		private int frameLength;
		private int framePosition;
		private boolean finished;
		private boolean md5Verified;

		final int channels;
		final int sampleRate;
		final long dataLength;

		PcmStream(final byte[] fileBytes) throws IOException {
			this.decoder = new FlacDecoder(fileBytes);
			while (this.decoder.readAndHandleMetadataBlock() != null) {
				;
			}
			this.streamInfo = this.decoder.streamInfo;
			if ((this.streamInfo.sampleDepth % 8) != 0) {
				throw new UnsupportedOperationException("Only whole-byte sample depth supported");
			}
			this.sourceChannels = this.streamInfo.numChannels;
			this.channels = Math.min(this.sourceChannels, 2);
			for (int j = 0; j < this.sourceChannels; j++) {
				this.counts[j % 2]++;
			}
			this.sampleRate = this.streamInfo.sampleRate;
			final int sourceBytes = this.streamInfo.sampleDepth / 8;
			if (sourceBytes >= 3) {
				this.downsampleBytes = sourceBytes - 2;
				this.bytesPerSample = 2;
			}
			else {
				this.downsampleBytes = 0;
				this.bytesPerSample = sourceBytes;
			}
			this.dataLength = this.streamInfo.numSamples * this.channels * this.bytesPerSample;
			this.block = new int[this.sourceChannels][65536];
			this.frame = new byte[this.channels * this.bytesPerSample];
			this.md5 = newMd5();
			this.md5Buffer = new byte[this.sourceChannels * (this.streamInfo.sampleDepth / 8)];
		}

		private static MessageDigest newMd5() {
			try {
				return MessageDigest.getInstance("MD5");
			}
			catch (final NoSuchAlgorithmException e) {
				throw new AssertionError(e);
			}
		}

		/** Fills up to {@code length} bytes; returns the count, or -1 at the end of the track. */
		int read(final byte[] buffer, final int offset, final int length) throws IOException {
			int written = 0;
			while (written < length) {
				if (this.framePosition == this.frameLength) {
					if (!nextFrame()) {
						break;
					}
				}
				final int count = Math.min(length - written, this.frameLength - this.framePosition);
				System.arraycopy(this.frame, this.framePosition, buffer, offset + written, count);
				this.framePosition += count;
				written += count;
			}
			return (written == 0) && (length > 0) ? -1 : written;
		}

		/** True when the stored audio hash matched, or was blank; false before the end of the track. */
		boolean isVerified() {
			return this.md5Verified;
		}

		private boolean nextFrame() throws IOException {
			if (this.blockPosition == this.blockLength) {
				if (this.finished) {
					return false;
				}
				this.blockLength = this.decoder.readAudioBlock(this.block, 0);
				this.blockPosition = 0;
				if (this.blockLength == 0) {
					this.finished = true;
					verifyMd5();
					return false;
				}
				hashBlock();
			}
			final int i = this.blockPosition++;
			int p = 0;
			for (int c = 0; c < this.channels; c++) {
				int val;
				if (this.sourceChannels > 2) {
					long sum = 0;
					for (int j = c; j < this.sourceChannels; j += 2) {
						sum += this.block[j][i];
					}
					val = (int) (sum / this.counts[c]);
				}
				else {
					val = this.block[c][i];
				}
				for (int k = 0; k < this.downsampleBytes; k++) {
					val = val >>> 8;
				}
				if (this.bytesPerSample == 1) {
					this.frame[p++] = (byte) (val + 128);
				}
				else {
					for (int k = 0; k < this.bytesPerSample; k++) {
						this.frame[p++] = (byte) (val >>> (k * 8));
					}
				}
			}
			this.frameLength = p;
			this.framePosition = 0;
			return true;
		}

		private void hashBlock() {
			final int numBytes = this.streamInfo.sampleDepth / 8;
			for (int i = 0; i < this.blockLength; i++) {
				int l = 0;
				for (int j = 0; j < this.sourceChannels; j++) {
					final int val = this.block[j][i];
					for (int k = 0; k < numBytes; k++) {
						this.md5Buffer[l++] = (byte) (val >>> (k << 3));
					}
				}
				this.md5.update(this.md5Buffer, 0, l);
			}
		}

		private void verifyMd5() throws IOException {
			final byte[] expectHash = this.streamInfo.md5Hash;
			if (Arrays.equals(expectHash, new byte[16])) {
				Log.warn("Warning: MD5 hash field was blank");
			}
			else if (!Arrays.equals(this.md5.digest(), expectHash)) {
				throw new DataFormatException("MD5 hash check failed");
			}
			this.md5Verified = true;
		}

		@Override
		public void close() throws IOException {
			this.decoder.close();
		}
	}
}
