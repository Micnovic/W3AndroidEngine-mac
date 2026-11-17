package com.etheller.warsmash.parsers.facefx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FaceFxTest {
	/** Builds the header, class table and name table of an archive, in the layout of the game's files. */
	static final class Builder {
		private final ByteArrayOutputStream out = new ByteArrayOutputStream();

		Builder(final String[] classes, final String[] names) {
			putInt(0x45434146);
			putInt(2181);
			putInt(13);
			putString("Maker", true);
			putString("Project", true);
			putInt(1000);
			putShort(2);
			putInt(classes.length);
			putInt(classes.length);
			for (int i = 0; i < classes.length; i++) {
				putInt(26 + i);
				putInt(1);
				putInt(i);
				putString(classes[i], false);
				putShort(0);
				putInt(1);
				for (int j = 0; j < 12; j++) {
					out.write(0xFF);
				}
				putShort(0);
			}
			putInt(names.length);
			for (int i = 0; i < names.length; i++) {
				putInt(classes.length + i);
				putInt(i);
				putString(names[i], false);
				// The links to the neighbours; their length varies.
				putInt(1);
				putInt(classes.length);
				for (int j = 0; j < (i % 3) * 4; j++) {
					out.write(0xFF);
				}
				putShort(0);
			}
		}

		void putInt(final int value) {
			putShort(value & 0xFFFF);
			putShort(value >>> 16);
		}

		void putShort(final int value) {
			out.write(value & 0xFF);
			out.write((value >>> 8) & 0xFF);
		}

		void putFloat(final float value) {
			putInt(Float.floatToIntBits(value));
		}

		void putString(final String value, final boolean terminated) {
			final byte[] chars = value.getBytes(StandardCharsets.ISO_8859_1);
			putInt(chars.length + (terminated ? 1 : 0));
			out.write(chars, 0, chars.length);
			if (terminated) {
				out.write(0);
			}
		}

		void putKey(final float time, final float value, final float slopeIn, final float slopeOut) {
			putFloat(time);
			putFloat(value);
			putFloat(slopeIn);
			putFloat(slopeOut);
		}

		byte[] bytes() {
			return out.toByteArray();
		}
	}

	static byte[] animSet() {
		final Builder b = new Builder(new String[] { "FxObject", "FxAnim", "FxAnimCurve" },
				new String[] { "Speaker", "Line01", "FxAnim", "open", "wide" });
		// Some other object data comes first and must be skipped.
		b.putInt(7);
		b.putInt(3);
		b.putInt(5);
		b.putInt(3);
		b.putInt(0x150);
		b.putInt(1);
		b.putInt(2);
		b.putInt(3);
		b.putInt(3);
		b.putKey(0f, 0f, 0f, 0f);
		b.putKey(1f, 1f, 0f, 0f);
		b.putKey(2f, 0f, 0f, 0f);
		b.putInt(4);
		b.putInt(1);
		b.putKey(0.5f, 0.25f, 0f, 0f);
		b.putInt(0);
		return b.bytes();
	}

	@Test
	void readsTheHeaderAndTheNameTable() throws IOException {
		final FaceFxArchive archive = FaceFxArchive.read(animSet());
		assertNotNull(archive);
		assertEquals(2181, archive.version);
		assertEquals("Maker", archive.creator);
		assertEquals(3, archive.getClassNames().size());
		assertTrue(archive.hasNames());
		assertEquals(5, archive.getNameCount());
		assertEquals("open", archive.getName(3));
		assertEquals("Line01", archive.getName(1));
		assertNull(archive.getName(5));
	}

	@Test
	void rejectsOtherData() throws IOException {
		assertNull(FaceFxArchive.read(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 }));
		assertNull(FaceFxAnimSet.read(new byte[0]));
		final byte[] truncated = animSet();
		assertNull(FaceFxAnimSet.read(java.util.Arrays.copyOf(truncated, 120)));
	}

	@Test
	void readsTheCurvesOfAnAnimation() throws IOException {
		final FaceFxAnimSet set = FaceFxAnimSet.read(animSet());
		assertNotNull(set);
		assertEquals(1, set.getAnims().size());
		final FaceFxAnimSet.Anim anim = set.find("line01");
		assertNotNull(anim);
		assertEquals("Line01", anim.name);
		assertEquals(2f, anim.duration, 1e-6f);
		assertEquals(2, anim.getCurves().size());
		final FaceFxAnimSet.Curve open = anim.findCurve("open");
		assertEquals(3, open.getKeyCount());
		assertEquals(0f, open.evaluate(-1f), 1e-6f);
		assertEquals(0.5f, open.evaluate(0.5f), 1e-6f);
		assertEquals(1f, open.evaluate(1f), 1e-6f);
		assertEquals(0f, open.evaluate(5f), 1e-6f);
		assertEquals(0.25f, anim.findCurve("wide").evaluate(3f), 1e-6f);
		assertNull(set.find("Line02"));
	}

	@Test
	void usesTheSlopesOfTheKeys() throws IOException {
		final float[] time = { 0f, 1f };
		final FaceFxAnimSet.Curve curve = new FaceFxAnimSet.Curve("x", time, new float[] { 0f, 0f },
				new float[] { 0f, -2f }, new float[] { 2f, 0f });
		// A hump: leaves at slope 2 and arrives at slope -2, so the middle of the cubic is 0.5.
		assertEquals(0.5f, curve.evaluate(0.5f), 1e-6f);
	}

	@Test
	void namesTheAnimationAfterTheSoundFile() {
		assertEquals("H01Arthas08", FaceFxAnimSet.animNameForSound("Sound\\Dialogue\\Human01\\H01Arthas08.flac"));
		assertEquals("H03Footman47", FaceFxAnimSet.animNameForSound("a/b/H03Footman47.mp3"));
		assertEquals("Line", FaceFxAnimSet.animNameForSound("Line"));
	}

	static byte[] actor() {
		final Builder b = new Builder(new String[] { "FxBone", "FxActor" },
				new String[] { "Face", "jaw", "lip", "open", "wide" });
		b.putInt(2);
		b.putInt(-1);
		b.putInt(1);
		for (int bone = 1; bone <= 2; bone++) {
			if (bone > 1) {
				b.putInt(-1);
				b.putInt(bone);
			}
			for (int pass = 0; pass < 2; pass++) {
				b.putFloat(0f);
				b.putFloat(bone);
				b.putFloat(2f);
				b.putFloat(1f);
				b.putFloat(0f);
				b.putFloat(0f);
				b.putFloat(0f);
				b.putFloat(1f);
				b.putFloat(1f);
				b.putFloat(1f);
			}
			b.putFloat(1f);
			if (bone == 1) {
				b.putInt(2);
				b.putInt(0);
				for (final float v : new float[] { 0f, -0.5f, 0f, 0f, 0.5f, 0f, 0f, 0f, 0f, 0f }) {
					b.putFloat(v);
				}
				b.putInt(1);
				for (final float v : new float[] { 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0.25f, 0f, 0f }) {
					b.putFloat(v);
				}
			}
			else {
				b.putInt(0);
			}
		}
		return b.bytes();
	}

	@Test
	void readsTheBonesAndTargetsOfAnActor() throws IOException {
		final FaceFxActor actor = FaceFxActor.read(actor());
		assertNotNull(actor);
		assertEquals(2, actor.getBones().size());
		assertEquals(2, actor.getTargets().size());
		assertEquals("open", actor.getTargets().get(0));
		assertEquals(1, actor.getTargetIndex("wide"));
		assertEquals(-1, actor.getTargetIndex("jaw"));
		final FaceFxActor.Bone jaw = actor.findBone("jaw");
		assertNotNull(jaw);
		assertEquals(2, jaw.getPoses().size());
		assertArrayEquals(new float[] { 0f, 1f, 2f }, jaw.getReferencePosition(), 0f);
		assertEquals(0, actor.findBone("lip").getPoses().size());
	}

	@Test
	void combinesTheReferencePoseWithTheTargetsOffsets() throws IOException {
		final FaceFxActor actor = FaceFxActor.read(actor());
		final FaceFxActor.Bone jaw = actor.findBone("jaw");
		final float[] position = new float[3];
		final float[] rotation = new float[4];
		final float[] scale = new float[3];
		jaw.solve(new float[] { 0f, 0f }, position, rotation, scale);
		assertArrayEquals(new float[] { 0f, 1f, 2f }, position, 1e-6f);
		assertArrayEquals(new float[] { 1f, 0f, 0f, 0f }, rotation, 1e-6f);
		jaw.solve(new float[] { 1f, 0f }, position, rotation, scale);
		assertArrayEquals(new float[] { 0f, 0.5f, 2f }, position, 1e-6f);
		final float inverse = (float) (1 / Math.sqrt(1.25));
		assertArrayEquals(new float[] { inverse, 0.5f * inverse, 0f, 0f }, rotation, 1e-6f);
		jaw.solve(new float[] { 0f, 2f }, position, rotation, scale);
		assertArrayEquals(new float[] { 1.5f, 1f, 1f }, scale, 1e-6f);
	}

	@Test
	void samplesTheTargetsOfAnAnimation() throws IOException {
		final FaceFxActor actor = FaceFxActor.read(actor());
		final FaceFxAnimSet.Anim anim = FaceFxAnimSet.read(animSet()).find("Line01");
		final float[] weights = actor.sampleTargets(anim, 1f, null);
		assertEquals(2, weights.length);
		assertEquals(1f, weights[0], 1e-6f);
		assertEquals(0.25f, weights[1], 1e-6f);
	}

	@Test
	void keepsLittleEndianAndReadOnlyBuffers() throws IOException {
		final FaceFxArchive archive = FaceFxArchive.read(ByteBuffer.wrap(animSet()).asReadOnlyBuffer());
		assertNotNull(archive);
		assertEquals(ByteOrder.LITTLE_ENDIAN, archive.data().order());
	}
}
