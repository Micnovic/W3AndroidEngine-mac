package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

/** Every common WAV encoding plays as 16 bit PCM, so that no sound of the game data is silent for its encoding. */
class WavPcmTest {
	private static void le(final ByteArrayOutputStream out, final long value, final int bytes) {
		for (int i = 0; i < bytes; i++) {
			out.write((int) ((value >> (8 * i)) & 0xFF));
		}
	}

	private static byte[] fmt(final int tag, final int channels, final int rate, final int blockAlign, final int bits,
			final byte[] extra) {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		le(out, tag, 2);
		le(out, channels, 2);
		le(out, rate, 4);
		le(out, (long) rate * blockAlign, 4);
		le(out, blockAlign, 2);
		le(out, bits, 2);
		if (extra != null) {
			le(out, extra.length, 2);
			out.writeBytes(extra);
		}
		return out.toByteArray();
	}

	private static byte[] wav(final byte[] fmt, final byte[] data, final int declaredDataLength) {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes("RIFF".getBytes());
		le(out, 0, 4);
		out.writeBytes("WAVE".getBytes());
		out.writeBytes("fmt ".getBytes());
		le(out, fmt.length, 4);
		out.writeBytes(fmt);
		if ((fmt.length & 1) != 0) {
			out.write(0);
		}
		out.writeBytes("data".getBytes());
		le(out, declaredDataLength, 4);
		out.writeBytes(data);
		return out.toByteArray();
	}

	private static byte[] wav(final byte[] fmt, final byte[] data) {
		return wav(fmt, data, data.length);
	}

	/** @return the 16 bit samples of a converted file, which has the 44 byte header this writes */
	private static short[] samples(final byte[] wav) {
		final short[] samples = new short[(wav.length - 44) / 2];
		for (int i = 0; i < samples.length; i++) {
			samples[i] = (short) ((wav[44 + (i * 2)] & 0xFF) | (wav[45 + (i * 2)] << 8));
		}
		return samples;
	}

	private static int channelsOf(final byte[] wav) {
		return (wav[22] & 0xFF) | ((wav[23] & 0xFF) << 8);
	}

	private static int rateOf(final byte[] wav) {
		return (wav[24] & 0xFF) | ((wav[25] & 0xFF) << 8) | ((wav[26] & 0xFF) << 16);
	}

	@Test
	void sixteenBitPcmIsLeftAlone() throws IOException {
		final byte[] file = wav(fmt(1, 2, 22050, 4, 16, null), new byte[8]);
		assertSame(file, WavPcm.toPcm16(file));
	}

	@Test
	void eightBitIsCentredAndWidened() throws IOException {
		final byte[] file = wav(fmt(1, 1, 11025, 1, 8, null), new byte[] { (byte) 128, (byte) 255, 0 });
		final byte[] out = WavPcm.toPcm16(file);
		assertEquals(1, channelsOf(out));
		assertEquals(11025, rateOf(out));
		assertArrayEquals(new short[] { 0, 32512, -32768 }, samples(out));
	}

	@Test
	void wideAndFloatSamplesAreNarrowed() throws IOException {
		final byte[] pcm24 = wav(fmt(1, 1, 8000, 3, 24, null), new byte[] { 1, 2, 3, 0, 0, (byte) 0x80 });
		assertArrayEquals(new short[] { 0x0302, -32768 }, samples(WavPcm.toPcm16(pcm24)));
		final ByteArrayOutputStream floats = new ByteArrayOutputStream();
		le(floats, Float.floatToIntBits(0.5f), 4);
		le(floats, Float.floatToIntBits(-2f), 4);
		assertArrayEquals(new short[] { 16384, -32768 },
				samples(WavPcm.toPcm16(wav(fmt(3, 1, 8000, 4, 32, null), floats.toByteArray()))));
	}

	@Test
	void lawEncodingsAreExpanded() throws IOException {
		assertArrayEquals(new short[] { 8, -8 },
				samples(WavPcm.toPcm16(wav(fmt(6, 1, 8000, 1, 8, null), new byte[] { (byte) 0xD5, 0x55 }))));
		assertArrayEquals(new short[] { 0, -32124, 32124 }, samples(
				WavPcm.toPcm16(wav(fmt(7, 1, 8000, 1, 8, null), new byte[] { (byte) 0xFF, 0, (byte) 0x80 }))));
	}

	@Test
	void imaAdpcmBlockIsDecoded() throws IOException {
		final ByteArrayOutputStream block = new ByteArrayOutputStream();
		le(block, 1000, 2);
		block.write(0);
		block.write(0);
		block.write(0x07);
		block.writeBytes(new byte[3]);
		final ByteArrayOutputStream extra = new ByteArrayOutputStream();
		le(extra, 9, 2);
		final short[] out = samples(
				WavPcm.toPcm16(wav(fmt(0x11, 1, 8000, 8, 4, extra.toByteArray()), block.toByteArray())));
		assertArrayEquals(new short[] { 1000, 1011, 1013, 1014, 1015, 1016, 1017, 1018, 1019 }, out);
	}

	@Test
	void msAdpcmBlockIsDecoded() throws IOException {
		final ByteArrayOutputStream extra = new ByteArrayOutputStream();
		le(extra, 4, 2);
		le(extra, 2, 2);
		le(extra, 256, 2);
		le(extra, 0, 2);
		le(extra, 512, 2);
		le(extra, -256, 2);
		final ByteArrayOutputStream block = new ByteArrayOutputStream();
		block.write(0);
		le(block, 16, 2);
		le(block, 100, 2);
		le(block, 50, 2);
		block.write(0x01);
		final byte[] out = WavPcm.toPcm16(wav(fmt(2, 1, 8000, 8, 4, extra.toByteArray()), block.toByteArray()));
		assertArrayEquals(new short[] { 50, 100, 100, 116 }, samples(out));
	}

	@Test
	void stereoImaAdpcmKeepsTheChannelsApart() throws IOException {
		final ByteArrayOutputStream block = new ByteArrayOutputStream();
		le(block, 1000, 2);
		block.write(0);
		block.write(0);
		le(block, -1000, 2);
		block.write(0);
		block.write(0);
		block.writeBytes(new byte[8]);
		final ByteArrayOutputStream extra = new ByteArrayOutputStream();
		le(extra, 9, 2);
		final byte[] out = WavPcm.toPcm16(wav(fmt(0x11, 2, 8000, 16, 4, extra.toByteArray()), block.toByteArray()));
		assertEquals(2, channelsOf(out));
		final short[] samples = samples(out);
		assertEquals(18, samples.length);
		assertEquals(1000, samples[0]);
		assertEquals(-1000, samples[1]);
		assertEquals(1000, samples[2]);
		assertEquals(-1000, samples[3]);
	}

	@Test
	void extensibleHeaderNamesTheRealFormat() throws IOException {
		final ByteArrayOutputStream extra = new ByteArrayOutputStream();
		le(extra, 8, 2);
		le(extra, 3, 4);
		le(extra, 1, 2);
		extra.writeBytes(new byte[14]);
		final byte[] fmt = fmt(0xFFFE, 1, 8000, 1, 8, extra.toByteArray());
		assertArrayEquals(new short[] { 0 }, samples(WavPcm.toPcm16(wav(fmt, new byte[] { (byte) 128 }))));
	}

	@Test
	void moreThanTwoChannelsAreFoldedIntoAPair() throws IOException {
		final ByteArrayOutputStream data = new ByteArrayOutputStream();
		for (int i = 0; i < 4; i++) {
			le(data, 1000, 2);
		}
		final byte[] out = WavPcm.toPcm16(wav(fmt(1, 4, 8000, 8, 16, null), data.toByteArray()));
		assertEquals(2, channelsOf(out));
		assertEquals(2, samples(out).length);
	}

	@Test
	void dataLengthPastTheEndMeansWhatIsLeft() throws IOException {
		final byte[] file = wav(fmt(1, 1, 8000, 1, 8, null), new byte[] { (byte) 128, (byte) 128 }, 1000);
		assertEquals(2, samples(WavPcm.toPcm16(file)).length);
	}

	@Test
	void unknownEncodingIsRefused() {
		assertThrows(IOException.class, () -> WavPcm.toPcm16(wav(fmt(0x55, 1, 8000, 1, 0, null), new byte[4])));
		assertThrows(IOException.class, () -> WavPcm.toPcm16(new byte[4]));
	}
}
