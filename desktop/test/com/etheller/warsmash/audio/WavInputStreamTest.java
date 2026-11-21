package com.etheller.warsmash.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

/**
 * The WAV reader finds the format and data chunks past other chunks, whose odd lengths are followed by a pad byte.
 */
class WavInputStreamTest {
	private static void le(final ByteArrayOutputStream out, final long value, final int bytes) {
		for (int i = 0; i < bytes; i++) {
			out.write((int) ((value >> (8 * i)) & 0xFF));
		}
	}

	private static void chunk(final ByteArrayOutputStream out, final String id, final byte[] body) {
		out.writeBytes(id.getBytes());
		le(out, body.length, 4);
		out.writeBytes(body);
		if ((body.length & 1) != 0) {
			out.write(0);
		}
	}

	private static byte[] fmt() {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		le(out, 1, 2);
		le(out, 2, 2);
		le(out, 22050, 4);
		le(out, 22050 * 4, 4);
		le(out, 4, 2);
		le(out, 16, 2);
		return out.toByteArray();
	}

	private static byte[] wav(final byte[] before, final byte[] data) {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes("RIFF".getBytes());
		le(out, 0, 4);
		out.writeBytes("WAVE".getBytes());
		chunk(out, "fmt ", fmt());
		if (before != null) {
			chunk(out, "LIST", before);
		}
		chunk(out, "data", data);
		return out.toByteArray();
	}

	private static Wav.WavInputStream open(final byte[] bytes) {
		return new Wav.WavInputStream(new ByteArrayInputStream(bytes), "test");
	}

	@Test
	void readsFormatAndDataLength() {
		final Wav.WavInputStream stream = open(wav(null, new byte[400]));
		assertEquals(2, stream.channels);
		assertEquals(22050, stream.sampleRate);
		assertEquals(400, stream.dataRemaining);
	}

	@Test
	void skipsEvenChunkBeforeData() {
		assertEquals(40, open(wav(new byte[6], new byte[40])).dataRemaining);
	}

	@Test
	void skipsPadByteAfterOddChunkBeforeData() {
		assertEquals(40, open(wav(new byte[5], new byte[40])).dataRemaining);
		assertEquals(40, open(wav(new byte[1], new byte[40])).dataRemaining);
	}

	@Test
	void eightBitFileIsConvertedToSixteenBit() {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes("RIFF".getBytes());
		le(out, 0, 4);
		out.writeBytes("WAVE".getBytes());
		final ByteArrayOutputStream fmt = new ByteArrayOutputStream();
		le(fmt, 1, 2);
		le(fmt, 1, 2);
		le(fmt, 11025, 4);
		le(fmt, 11025, 4);
		le(fmt, 1, 2);
		le(fmt, 8, 2);
		chunk(out, "fmt ", fmt.toByteArray());
		chunk(out, "data", new byte[100]);
		final byte[] bytes = out.toByteArray();
		final Wav.WavInputStream converted = Wav.open(bytes, null);
		assertEquals(1, converted.channels);
		assertEquals(11025, converted.sampleRate);
		assertEquals(200, converted.dataRemaining);
	}
}
