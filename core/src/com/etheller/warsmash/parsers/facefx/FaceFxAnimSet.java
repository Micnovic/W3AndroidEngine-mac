package com.etheller.warsmash.parsers.facefx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The spoken lines of one speaker ({@code *.animset_ingame}): for every line an animation named like the sound file of
 * the line, made of curves named after the targets of the speaker's {@link FaceFxActor} (the visemes {@code open},
 * {@code wide}, {@code tBack}, ..., {@code Blink}, the eye and head movements, the emotions) with keys of time in
 * seconds from the start of the sound, value, and the slopes in and out.
 */
public final class FaceFxAnimSet {
	/** The two words that precede the animation's name index and its curves in the object data. */
	private static final int ANIM_MARKER_A = 3;
	private static final int ANIM_MARKER_B = 0x150;
	private static final int MAX_CURVES = 128;
	private static final int MAX_KEYS = 65536;

	private final List<Anim> anims;
	private final Map<String, Anim> byName = new HashMap<>();

	private FaceFxAnimSet(final List<Anim> anims) {
		this.anims = Collections.unmodifiableList(anims);
		for (final Anim anim : anims) {
			this.byName.putIfAbsent(anim.name.toLowerCase(Locale.ROOT), anim);
		}
	}

	public List<Anim> getAnims() {
		return this.anims;
	}

	/** @return the animation of a spoken line, by the name of its sound file without folders and extension */
	public Anim find(final String animName) {
		return (animName == null) ? null : this.byName.get(animName.toLowerCase(Locale.ROOT));
	}

	/** @return the name of the animation that belongs to a sound file: its name without folders and extension */
	public static String animNameForSound(final String soundPath) {
		if (soundPath == null) {
			return null;
		}
		final int slash = Math.max(soundPath.lastIndexOf('/'), soundPath.lastIndexOf('\\'));
		String name = soundPath.substring(slash + 1);
		final int dot = name.lastIndexOf('.');
		if (dot > 0) {
			name = name.substring(0, dot);
		}
		return name;
	}

	/** @return the animations in the file, or null when the data is not an animset of the layout read here */
	public static FaceFxAnimSet read(final byte[] bytes) throws IOException {
		final FaceFxArchive archive = FaceFxArchive.read(bytes);
		if ((archive == null) || !archive.hasNames() || !archive.hasClass("FxAnim")) {
			return null;
		}
		final ByteBuffer data = archive.data();
		final int nameCount = archive.getNameCount();
		final List<Anim> anims = new ArrayList<>();
		int position = archive.getNamesEnd();
		final int limit = bytes.length - 16;
		while (position <= limit) {
			if ((data.getInt(position) == ANIM_MARKER_A) && (data.getInt(position + 4) == ANIM_MARKER_B)) {
				final Anim anim = readAnim(archive, data, position + 8, nameCount);
				if (anim != null) {
					anims.add(anim);
					position = anim.endOffset;
					continue;
				}
			}
			position++;
		}
		return anims.isEmpty() ? null : new FaceFxAnimSet(anims);
	}

	private static Anim readAnim(final FaceFxArchive archive, final ByteBuffer data, final int start,
			final int nameCount) {
		final int length = data.limit();
		int p = start;
		final int nameIndex = data.getInt(p);
		final int curveCount = data.getInt(p + 4);
		p += 8;
		if ((nameIndex < 0) || (nameIndex >= nameCount) || (curveCount < 0) || (curveCount > MAX_CURVES)) {
			return null;
		}
		final List<Curve> curves = new ArrayList<>();
		float duration = 0;
		for (int c = 0; c < curveCount; c++) {
			if (p + 8 > length) {
				return null;
			}
			final int targetIndex = data.getInt(p);
			final int keyCount = data.getInt(p + 4);
			p += 8;
			if ((targetIndex < 0) || (targetIndex >= nameCount) || (keyCount < 0) || (keyCount > MAX_KEYS)
					|| (p + (16L * keyCount) > length)) {
				return null;
			}
			final float[] time = new float[keyCount];
			final float[] value = new float[keyCount];
			final float[] slopeIn = new float[keyCount];
			final float[] slopeOut = new float[keyCount];
			for (int k = 0; k < keyCount; k++) {
				time[k] = data.getFloat(p);
				value[k] = data.getFloat(p + 4);
				slopeIn[k] = data.getFloat(p + 8);
				slopeOut[k] = data.getFloat(p + 12);
				if (!isFinite(time[k]) || !isFinite(value[k]) || !isFinite(slopeIn[k]) || !isFinite(slopeOut[k])
						|| ((k > 0) && (time[k] < (time[k - 1] - 0.01f)))) {
					return null;
				}
				p += 16;
			}
			if (keyCount > 0) {
				duration = Math.max(duration, time[keyCount - 1]);
			}
			curves.add(new Curve(archive.getName(targetIndex), time, value, slopeIn, slopeOut));
		}
		return new Anim(archive.getName(nameIndex), duration, curves, p);
	}

	private static boolean isFinite(final float value) {
		return !Float.isNaN(value) && !Float.isInfinite(value);
	}

	public static final class Anim {
		public final String name;
		/** The time of the last key of any curve, in seconds */
		public final float duration;
		private final List<Curve> curves;
		private final int endOffset;

		Anim(final String name, final float duration, final List<Curve> curves, final int endOffset) {
			this.name = name;
			this.duration = duration;
			this.curves = Collections.unmodifiableList(curves);
			this.endOffset = endOffset;
		}

		public List<Curve> getCurves() {
			return this.curves;
		}

		public Curve findCurve(final String target) {
			for (final Curve curve : this.curves) {
				if (curve.target.equals(target)) {
					return curve;
				}
			}
			return null;
		}
	}

	public static final class Curve {
		public final String target;
		public final float[] time;
		public final float[] value;
		public final float[] slopeIn;
		public final float[] slopeOut;

		Curve(final String target, final float[] time, final float[] value, final float[] slopeIn,
				final float[] slopeOut) {
			this.target = target;
			this.time = time;
			this.value = value;
			this.slopeIn = slopeIn;
			this.slopeOut = slopeOut;
		}

		public int getKeyCount() {
			return this.time.length;
		}

		/** @return the value at the time (seconds from the start of the sound): cubic Hermite between the keys, held outside them */
		public float evaluate(final float t) {
			final int n = this.time.length;
			if (n == 0) {
				return 0;
			}
			if (t <= this.time[0]) {
				return this.value[0];
			}
			if (t >= this.time[n - 1]) {
				return this.value[n - 1];
			}
			int hi = 1;
			while (this.time[hi] < t) {
				hi++;
			}
			final int lo = hi - 1;
			final float h = this.time[hi] - this.time[lo];
			if (h <= 1e-6f) {
				return this.value[hi];
			}
			final float s = (t - this.time[lo]) / h;
			final float s2 = s * s;
			final float s3 = s2 * s;
			final float m0 = this.slopeOut[lo] * h;
			final float m1 = this.slopeIn[hi] * h;
			return (((2 * s3) - (3 * s2)) + 1) * this.value[lo] + ((s3 - (2 * s2)) + s) * m0
					+ (((-2 * s3) + (3 * s2)) * this.value[hi]) + ((s3 - s2) * m1);
		}
	}
}
