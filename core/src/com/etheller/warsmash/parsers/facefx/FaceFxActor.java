package com.etheller.warsmash.parsers.facefx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The face of one character ({@code *.facefx_ingame}, next to the model): its face bones with their reference pose
 * and, for every bone, the pose offsets the targets (visemes, blink, eyebrows, eyes, head, emotions, jaw sideways)
 * add to it. The values of the targets come from the curves of an {@link FaceFxAnimSet} animation.
 *
 * <p>
 * Positions are three floats, rotations are quaternions stored as w, x, y, z, scales are three floats. The offsets of
 * the rotations are not unit quaternions: they are added to the reference rotation and the sum is normalized.
 */
public final class FaceFxActor {
	private static final int MAX_BONES = 512;
	private static final int MAX_POSES = 1024;
	private static final int POSE_FLOATS = 10;

	private final List<Bone> bones;
	private final List<String> targets;
	private final Map<String, Integer> targetIndices = new HashMap<>();

	private FaceFxActor(final List<Bone> bones, final List<String> targets) {
		this.bones = Collections.unmodifiableList(bones);
		this.targets = Collections.unmodifiableList(targets);
		for (int i = 0; i < targets.size(); i++) {
			this.targetIndices.putIfAbsent(targets.get(i), i);
		}
	}

	public List<Bone> getBones() {
		return this.bones;
	}

	/** @return the names of the targets in the order of {@link Pose#target} */
	public List<String> getTargets() {
		return this.targets;
	}

	/** @return the index of the target in {@link #getTargets()}, or -1 */
	public int getTargetIndex(final String name) {
		final Integer index = this.targetIndices.get(name);
		return (index == null) ? -1 : index;
	}

	public Bone findBone(final String name) {
		for (final Bone bone : this.bones) {
			if (bone.name.equals(name)) {
				return bone;
			}
		}
		return null;
	}

	/**
	 * Fills the weight of every target at a time of an animation: the value of the curve of the same name, 0 for the
	 * targets the animation has no curve for.
	 */
	public float[] sampleTargets(final FaceFxAnimSet.Anim anim, final float time, float[] weights) {
		if ((weights == null) || (weights.length != this.targets.size())) {
			weights = new float[this.targets.size()];
		}
		Arrays.fill(weights, 0f);
		for (final FaceFxAnimSet.Curve curve : anim.getCurves()) {
			final int index = getTargetIndex(curve.target);
			if (index >= 0) {
				weights[index] = curve.evaluate(time);
			}
		}
		return weights;
	}

	/** @return the face bones and targets in the file, or null when the data is not an actor of the layout read here */
	public static FaceFxActor read(final byte[] bytes) throws IOException {
		final FaceFxArchive archive = FaceFxArchive.read(bytes);
		if ((archive == null) || !archive.hasNames() || !archive.hasClass("FxBone")) {
			return null;
		}
		final ByteBuffer data = archive.data();
		final int nameCount = archive.getNameCount();
		final int limit = bytes.length - 12;
		for (int p = archive.getNamesEnd(); p <= limit; p++) {
			final int boneCount = data.getInt(p);
			if ((boneCount < 1) || (boneCount > MAX_BONES) || (boneCount >= nameCount) || (data.getInt(p + 4) != -1)
					|| (data.getInt(p + 8) != 1)) {
				continue;
			}
			final FaceFxActor actor = readBones(archive, data, p + 4, boneCount);
			if (actor != null) {
				return actor;
			}
		}
		return null;
	}

	/**
	 * The bones are numbered like the first names of the name table (the character's own name is 0); the names after
	 * them are the targets. Every bone is {@code -1}, its number, two poses, a weight and the pose count, then the pose
	 * entries: the target's number (its name's index minus the bones') and the offsets.
	 */
	private static FaceFxActor readBones(final FaceFxArchive archive, final ByteBuffer data, final int start,
			final int boneCount) {
		final int length = data.limit();
		final int nameCount = archive.getNameCount();
		final int firstTarget = boneCount + 1;
		final List<Bone> bones = new ArrayList<>();
		int p = start;
		for (int k = 1; k <= boneCount; k++) {
			if (((p + 8 + (((2 * POSE_FLOATS) + 1) * 4) + 4) > length) || (data.getInt(p) != -1)
					|| (data.getInt(p + 4) != k)) {
				return null;
			}
			p += 8;
			final float[] reference = readFloats(data, p, POSE_FLOATS);
			if (reference == null) {
				return null;
			}
			// The second pose equals the first in every file seen; the weight after it is 1.
			p += ((2 * POSE_FLOATS) + 1) * 4;
			final int poseCount = data.getInt(p);
			p += 4;
			if ((poseCount < 0) || (poseCount > MAX_POSES)
					|| ((p + (poseCount * (long) (1 + POSE_FLOATS) * 4)) > length)) {
				return null;
			}
			final List<Pose> poses = new ArrayList<>();
			for (int i = 0; i < poseCount; i++) {
				final int target = data.getInt(p);
				if ((target < 0) || ((firstTarget + target) >= nameCount)) {
					return null;
				}
				final float[] values = readFloats(data, p + 4, POSE_FLOATS);
				if (values == null) {
					return null;
				}
				poses.add(new Pose(target, values));
				p += (1 + POSE_FLOATS) * 4;
			}
			bones.add(new Bone(archive.getName(k), reference, poses));
		}
		final List<String> targets = new ArrayList<>();
		for (int i = firstTarget; i < nameCount; i++) {
			targets.add(archive.getName(i));
		}
		return new FaceFxActor(bones, targets);
	}

	private static float[] readFloats(final ByteBuffer data, final int position, final int count) {
		final float[] values = new float[count];
		for (int i = 0; i < count; i++) {
			final float v = data.getFloat(position + (i * 4));
			if (Float.isNaN(v) || Float.isInfinite(v) || (Math.abs(v) > 1e5f)) {
				return null;
			}
			values[i] = v;
		}
		return values;
	}

	public static final class Bone {
		public final String name;
		private final float[] reference;
		private final List<Pose> poses;

		Bone(final String name, final float[] reference, final List<Pose> poses) {
			this.name = name;
			this.reference = reference;
			this.poses = Collections.unmodifiableList(poses);
		}

		public float[] getReferencePosition() {
			return new float[] { this.reference[0], this.reference[1], this.reference[2] };
		}

		/** @return w, x, y, z */
		public float[] getReferenceRotation() {
			return new float[] { this.reference[3], this.reference[4], this.reference[5], this.reference[6] };
		}

		public float[] getReferenceScale() {
			return new float[] { this.reference[7], this.reference[8], this.reference[9] };
		}

		public List<Pose> getPoses() {
			return this.poses;
		}

		/**
		 * Combines the reference pose with the offsets of the targets by their weights (see
		 * {@link FaceFxActor#sampleTargets}): positions and scales add up, rotations add up and are normalized.
		 *
		 * @param position out, x y z
		 * @param rotation out, w x y z
		 * @param scale out, x y z
		 */
		public void solve(final float[] weights, final float[] position, final float[] rotation, final float[] scale) {
			final float[] work = new float[POSE_FLOATS];
			solveInto(weights, work);
			System.arraycopy(work, 0, position, 0, 3);
			System.arraycopy(work, 3, rotation, 0, 4);
			System.arraycopy(work, 7, scale, 0, 3);
		}

		/**
		 * What {@link #solve} changes against the reference pose, in the axes of the portrait model: the character's
		 * FaceFX space is x left, y up, z forward, the model's x forward, y left, z up, a cyclic turn of the axes, so
		 * positions and the vector part of rotations just move to the next place. The model's face bones have the
		 * identity as bind pose, so these are the values a bone takes on top of it.
		 *
		 * @param work     scratch space of 10 floats
		 * @param translation out, x y z
		 * @param rotation out, x y z w, the turn from the reference rotation to the solved one by the shortest way
		 * @param scale out, the solved scale divided by the reference scale, x y z
		 */
		public void solveModelDelta(final float[] weights, final float[] work, final float[] translation,
				final float[] rotation, final float[] scale) {
			solveInto(weights, work);
			translation[0] = work[2] - this.reference[2];
			translation[1] = work[0] - this.reference[0];
			translation[2] = work[1] - this.reference[1];

			final float rw = this.reference[3];
			final float rx = -this.reference[4];
			final float ry = -this.reference[5];
			final float rz = -this.reference[6];
			final float referenceLength = (float) Math.sqrt((rw * rw) + (rx * rx) + (ry * ry) + (rz * rz));
			final float inverse = (referenceLength > 1e-6f) ? 1 / referenceLength : 1;
			final float aw = work[3];
			final float ax = work[4];
			final float ay = work[5];
			final float az = work[6];
			final float bw = rw * inverse;
			final float bx = rx * inverse;
			final float by = ry * inverse;
			final float bz = rz * inverse;
			float w = (aw * bw) - (ax * bx) - (ay * by) - (az * bz);
			float x = (aw * bx) + (ax * bw) + (ay * bz) - (az * by);
			float y = (aw * by) - (ax * bz) + (ay * bw) + (az * bx);
			float z = (aw * bz) + (ax * by) - (ay * bx) + (az * bw);
			if (w < 0) {
				w = -w;
				x = -x;
				y = -y;
				z = -z;
			}
			rotation[0] = z;
			rotation[1] = x;
			rotation[2] = y;
			rotation[3] = w;

			scale[0] = ratio(work[9], this.reference[9]);
			scale[1] = ratio(work[7], this.reference[7]);
			scale[2] = ratio(work[8], this.reference[8]);
		}

		private static float ratio(final float value, final float reference) {
			return (Math.abs(reference) > 1e-6f) ? value / reference : 1f;
		}

		/** @param work position x y z, rotation w x y z, scale x y z */
		private void solveInto(final float[] weights, final float[] work) {
			System.arraycopy(this.reference, 0, work, 0, POSE_FLOATS);
			for (final Pose pose : this.poses) {
				final float w = weights[pose.target];
				if (w == 0f) {
					continue;
				}
				for (int i = 0; i < 3; i++) {
					work[i] += w * pose.values[i];
					work[7 + i] += w * pose.values[7 + i];
				}
				for (int i = 3; i < 7; i++) {
					work[i] += w * pose.values[i];
				}
			}
			final float lengthSquared = (work[3] * work[3]) + (work[4] * work[4]) + (work[5] * work[5])
					+ (work[6] * work[6]);
			if (lengthSquared > 1e-12f) {
				final float inverse = (float) (1.0 / Math.sqrt(lengthSquared));
				for (int i = 3; i < 7; i++) {
					work[i] *= inverse;
				}
			}
		}
	}

	public static final class Pose {
		/** Index into {@link FaceFxActor#getTargets()} */
		public final int target;
		/** Position x y z, rotation w x y z, scale x y z */
		public final float[] values;

		Pose(final int target, final float[] values) {
			this.target = target;
			this.values = values;
		}
	}
}
