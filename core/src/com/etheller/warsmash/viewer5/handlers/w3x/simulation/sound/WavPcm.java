package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Turns a WAV file of any common encoding into one of 16 bit PCM with one or two channels, which is all the sound
 * backends play: 8, 24 and 32 bit PCM, floating point, A-law and mu-law, MS and IMA ADPCM, in a plain or extensible
 * header, with more than two channels folded down to a stereo pair.
 */
public final class WavPcm {
	private static final int PCM = 1;
	private static final int MS_ADPCM = 2;
	private static final int FLOAT = 3;
	private static final int ALAW = 6;
	private static final int MULAW = 7;
	private static final int IMA_ADPCM = 0x11;
	private static final int EXTENSIBLE = 0xFFFE;

	private static final int[] MS_ADAPT = { 230, 230, 230, 230, 307, 409, 512, 614, 768, 614, 512, 409, 307, 230, 230,
			230 };
	private static final int[] IMA_INDEX = { -1, -1, -1, -1, 2, 4, 6, 8 };
	private static final int[] IMA_STEP = { 7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
			50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408,
			449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499,
			2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635,
			13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29795, 32767 };

	private WavPcm() {
	}

	private static final class Format {
		int tag;
		int channels;
		int sampleRate;
		int blockAlign;
		int bits;
		int samplesPerBlock;
		byte[] coefficients;
		int coefficientCount;
	}

	/**
	 * @return the file as 16 bit PCM with one or two channels: the same array when it already is
	 * @throws IOException when the file is not a WAV or its encoding is not one of those above
	 */
	public static byte[] toPcm16(final byte[] wav) throws IOException {
		Format format = null;
		int dataStart = -1;
		long dataSize = 0;
		if ((wav.length < 12) || !is(wav, 0, "RIFF") || !is(wav, 8, "WAVE")) {
			throw new IOException("Not a WAV file");
		}
		int at = 12;
		while ((at + 8) <= wav.length) {
			final long size = intLE(wav, at + 4) & 0xFFFFFFFFL;
			final int body = at + 8;
			if (is(wav, at, "fmt ") && (size >= 16) && ((body + 16) <= wav.length)) {
				format = readFormat(wav, body, (int) Math.min(size, wav.length - body));
			}
			else if (is(wav, at, "data")) {
				dataStart = body;
				// A length past the end of the file, or the 0 of a streamed file, means what is left.
				dataSize = ((size == 0) || (size > (wav.length - body))) ? (wav.length - body) : size;
				break;
			}
			at = (int) Math.min(Integer.MAX_VALUE, body + size + (size & 1));
		}
		if ((format == null) || (dataStart < 0)) {
			throw new IOException("WAV file without format or data");
		}
		if ((format.tag == PCM) && (format.bits == 16) && (format.channels >= 1) && (format.channels <= 2)) {
			return wav;
		}
		final short[] samples = decode(format, wav, dataStart, (int) dataSize);
		return write(samples, format.channels == 1 ? 1 : 2, format.sampleRate, format.channels);
	}

	private static Format readFormat(final byte[] wav, final int at, final int size) throws IOException {
		final Format format = new Format();
		format.tag = shortLE(wav, at);
		format.channels = shortLE(wav, at + 2);
		format.sampleRate = intLE(wav, at + 4);
		format.blockAlign = shortLE(wav, at + 12);
		format.bits = shortLE(wav, at + 14);
		if (size >= 20) {
			final int extra = shortLE(wav, at + 16);
			if ((format.tag == EXTENSIBLE) && (extra >= 22) && (size >= 40)) {
				// The first two bytes of the sub format GUID are the real format tag.
				format.tag = shortLE(wav, at + 24);
			}
			else if ((format.tag == MS_ADPCM) && (extra >= 4) && (size >= 22)) {
				format.samplesPerBlock = shortLE(wav, at + 18);
				format.coefficientCount = shortLE(wav, at + 20);
				format.coefficients = new byte[format.coefficientCount * 4];
				System.arraycopy(wav, at + 22, format.coefficients, 0,
						Math.max(0, Math.min(format.coefficients.length, size - 22)));
			}
			else if ((format.tag == IMA_ADPCM) && (extra >= 2)) {
				format.samplesPerBlock = shortLE(wav, at + 18);
			}
		}
		if ((format.channels < 1) || (format.channels > 32) || (format.sampleRate <= 0)) {
			throw new IOException("Unusable WAV format");
		}
		return format;
	}

	/** @return the samples of all channels, one frame after another */
	private static short[] decode(final Format format, final byte[] wav, final int start, final int size)
			throws IOException {
		final int channels = format.channels;
		switch (format.tag) {
		case PCM: {
			if ((format.bits != 8) && (format.bits != 16) && (format.bits != 24) && (format.bits != 32)) {
				throw new IOException("Unsupported PCM bits: " + format.bits);
			}
			final int bytes = format.bits / 8;
			final int count = size / bytes;
			final short[] out = new short[count - (count % channels)];
			for (int i = 0; i < out.length; i++) {
				final int at = start + (i * bytes);
				switch (bytes) {
				case 1:
					out[i] = (short) (((wav[at] & 0xFF) - 128) << 8);
					break;
				case 2:
					out[i] = (short) shortLE(wav, at);
					break;
				case 3:
					out[i] = (short) shortLE(wav, at + 1);
					break;
				default:
					out[i] = (short) (intLE(wav, at) >> 16);
					break;
				}
			}
			return out;
		}
		case FLOAT: {
			if ((format.bits != 32) && (format.bits != 64)) {
				throw new IOException("Unsupported float bits: " + format.bits);
			}
			final int bytes = format.bits / 8;
			final int count = size / bytes;
			final short[] out = new short[count - (count % channels)];
			for (int i = 0; i < out.length; i++) {
				final int at = start + (i * bytes);
				final double value = bytes == 4 ? Float.intBitsToFloat(intLE(wav, at))
						: Double.longBitsToDouble((intLE(wav, at) & 0xFFFFFFFFL) | ((long) intLE(wav, at + 4) << 32));
				out[i] = clamp(Math.round(value * 32767.0));
			}
			return out;
		}
		case ALAW:
		case MULAW: {
			final int count = size - (size % channels);
			final short[] out = new short[count];
			for (int i = 0; i < count; i++) {
				final int code = wav[start + i] & 0xFF;
				out[i] = (short) (format.tag == ALAW ? alaw(code) : mulaw(code));
			}
			return out;
		}
		case MS_ADPCM:
			return decodeMsAdpcm(format, wav, start, size);
		case IMA_ADPCM:
			return decodeImaAdpcm(format, wav, start, size);
		default:
			throw new IOException("Unsupported WAV format tag: " + format.tag);
		}
	}

	private static int alaw(final int code) {
		final int value = code ^ 0x55;
		int magnitude = (value & 0x0F) << 4;
		final int segment = (value & 0x70) >> 4;
		if (segment == 0) {
			magnitude += 8;
		}
		else {
			magnitude += 0x108;
			magnitude <<= segment - 1;
		}
		return (value & 0x80) != 0 ? magnitude : -magnitude;
	}

	private static int mulaw(final int code) {
		final int value = ~code & 0xFF;
		final int magnitude = ((((value & 0x0F) << 3) + 0x84) << ((value & 0x70) >> 4)) - 0x84;
		return (value & 0x80) != 0 ? -magnitude : magnitude;
	}

	private static short[] decodeMsAdpcm(final Format format, final byte[] wav, final int start, final int size)
			throws IOException {
		final int channels = format.channels;
		final int block = format.blockAlign;
		if ((block < (7 * channels)) || (format.coefficients == null) || (format.coefficientCount < 1)) {
			throw new IOException("Unusable MS ADPCM format");
		}
		final int perBlock = format.samplesPerBlock > 0 ? format.samplesPerBlock
				: (((block - (7 * channels)) * 2) / channels) + 2;
		final int blocks = (size + block - 1) / block;
		final short[] out = new short[blocks * perBlock * channels];
		int written = 0;
		final int[] c1 = new int[channels];
		final int[] c2 = new int[channels];
		final int[] delta = new int[channels];
		final int[] s1 = new int[channels];
		final int[] s2 = new int[channels];
		for (int b = 0; b < blocks; b++) {
			final int at = start + (b * block);
			final int length = Math.min(block, start + size - at);
			if (length < (7 * channels)) {
				break;
			}
			int p = at;
			for (int c = 0; c < channels; c++) {
				final int index = Math.min(wav[p++] & 0xFF, format.coefficientCount - 1);
				c1[c] = (short) shortLE(format.coefficients, index * 4);
				c2[c] = (short) shortLE(format.coefficients, (index * 4) + 2);
			}
			for (int c = 0; c < channels; c++) {
				delta[c] = (short) shortLE(wav, p);
				p += 2;
			}
			for (int c = 0; c < channels; c++) {
				s1[c] = (short) shortLE(wav, p);
				p += 2;
			}
			for (int c = 0; c < channels; c++) {
				s2[c] = (short) shortLE(wav, p);
				p += 2;
			}
			final int limit = written + (perBlock * channels);
			for (int c = 0; c < channels; c++) {
				out[written++] = (short) s2[c];
			}
			for (int c = 0; c < channels; c++) {
				out[written++] = (short) s1[c];
			}
			final int blockEnd = at + length;
			int channel = 0;
			while ((p < blockEnd) && (written < limit)) {
				final int pair = wav[p++] & 0xFF;
				for (int half = 0; (half < 2) && (written < limit); half++) {
					final int nibble = half == 0 ? pair >> 4 : pair & 0x0F;
					final int signed = nibble >= 8 ? nibble - 16 : nibble;
					int predictor = (((s1[channel] * c1[channel]) + (s2[channel] * c2[channel])) >> 8)
							+ (signed * delta[channel]);
					predictor = Math.max(-32768, Math.min(32767, predictor));
					s2[channel] = s1[channel];
					s1[channel] = predictor;
					delta[channel] = Math.max(16, (MS_ADAPT[nibble] * delta[channel]) >> 8);
					out[written++] = (short) predictor;
					channel = (channel + 1) % channels;
				}
			}
		}
		return trim(out, written, channels);
	}

	private static short[] decodeImaAdpcm(final Format format, final byte[] wav, final int start, final int size)
			throws IOException {
		final int channels = format.channels;
		final int block = format.blockAlign;
		if (block < (4 * channels)) {
			throw new IOException("Unusable IMA ADPCM format");
		}
		final int perBlock = (((block - (4 * channels)) * 2) / channels) + 1;
		final int blocks = (size + block - 1) / block;
		final short[] out = new short[blocks * perBlock * channels];
		int frames = 0;
		final int[] predictor = new int[channels];
		final int[] index = new int[channels];
		for (int b = 0; b < blocks; b++) {
			final int at = start + (b * block);
			final int length = Math.min(block, start + size - at);
			if (length < (4 * channels)) {
				break;
			}
			final int base = frames * channels;
			for (int c = 0; c < channels; c++) {
				predictor[c] = (short) shortLE(wav, at + (c * 4));
				index[c] = Math.max(0, Math.min(88, wav[at + (c * 4) + 2] & 0xFF));
				out[base + c] = (short) predictor[c];
			}
			// After the headers the nibbles come in groups of four bytes (eight samples) per channel in turn.
			final int groups = (length - (4 * channels)) / (4 * channels);
			for (int g = 0; g < groups; g++) {
				for (int c = 0; c < channels; c++) {
					final int source = at + (4 * channels) + (((g * channels) + c) * 4);
					for (int i = 0; i < 8; i++) {
						final int pair = wav[source + (i >> 1)] & 0xFF;
						final int nibble = (i & 1) == 0 ? pair & 0x0F : pair >> 4;
						final int step = IMA_STEP[index[c]];
						int diff = step >> 3;
						if ((nibble & 1) != 0) {
							diff += step >> 2;
						}
						if ((nibble & 2) != 0) {
							diff += step >> 1;
						}
						if ((nibble & 4) != 0) {
							diff += step;
						}
						predictor[c] = Math.max(-32768,
								Math.min(32767, (nibble & 8) != 0 ? predictor[c] - diff : predictor[c] + diff));
						index[c] = Math.max(0, Math.min(88, index[c] + IMA_INDEX[nibble & 7]));
						out[base + ((1 + (g * 8) + i) * channels) + c] = (short) predictor[c];
					}
				}
			}
			frames += 1 + (groups * 8);
		}
		return trim(out, frames * channels, channels);
	}

	private static short[] trim(final short[] samples, final int count, final int channels) {
		final int whole = count - (count % channels);
		if (whole == samples.length) {
			return samples;
		}
		final short[] out = new short[whole];
		System.arraycopy(samples, 0, out, 0, whole);
		return out;
	}

	private static short clamp(final long value) {
		return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
	}

	/** Writes the frames as a 16 bit WAV, folding channels past the first two into a pair by side. */
	private static byte[] write(final short[] samples, final int outChannels, final int sampleRate,
			final int inChannels) {
		final int frames = samples.length / inChannels;
		final int dataBytes = frames * outChannels * 2;
		final ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataBytes);
		writeText(out, "RIFF");
		writeIntLE(out, 36 + dataBytes);
		writeText(out, "WAVE");
		writeText(out, "fmt ");
		writeIntLE(out, 16);
		writeShortLE(out, PCM);
		writeShortLE(out, outChannels);
		writeIntLE(out, sampleRate);
		writeIntLE(out, sampleRate * outChannels * 2);
		writeShortLE(out, outChannels * 2);
		writeShortLE(out, 16);
		writeText(out, "data");
		writeIntLE(out, dataBytes);
		final float sideGain = inChannels > 2 ? (float) (1 / Math.sqrt((inChannels + 1) / 2)) : 1;
		for (int frame = 0; frame < frames; frame++) {
			if (inChannels <= 2) {
				for (int c = 0; c < inChannels; c++) {
					writeShortLE(out, samples[(frame * inChannels) + c]);
				}
				continue;
			}
			float left = 0;
			float right = 0;
			for (int c = 0; c < inChannels; c++) {
				final short sample = samples[(frame * inChannels) + c];
				if ((c & 1) == 0) {
					left += sample;
				}
				else {
					right += sample;
				}
			}
			writeShortLE(out, clamp(Math.round(left * sideGain)));
			writeShortLE(out, clamp(Math.round(right * sideGain)));
		}
		return out.toByteArray();
	}

	private static void writeText(final ByteArrayOutputStream out, final String text) {
		for (int i = 0; i < text.length(); i++) {
			out.write(text.charAt(i));
		}
	}

	private static void writeShortLE(final ByteArrayOutputStream out, final int value) {
		out.write(value & 0xFF);
		out.write((value >> 8) & 0xFF);
	}

	private static void writeIntLE(final ByteArrayOutputStream out, final int value) {
		writeShortLE(out, value & 0xFFFF);
		writeShortLE(out, (value >>> 16) & 0xFFFF);
	}

	private static boolean is(final byte[] data, final int at, final String text) {
		if ((at + 4) > data.length) {
			return false;
		}
		for (int i = 0; i < 4; i++) {
			if (data[at + i] != text.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private static int shortLE(final byte[] data, final int at) {
		return (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8);
	}

	private static int intLE(final byte[] data, final int at) {
		return shortLE(data, at) | (shortLE(data, at + 2) << 16);
	}
}
