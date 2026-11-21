package com.etheller.warsmash.viewer5.handlers.mdx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceArray;

import com.etheller.warsmash.util.ParseUtils;
import com.etheller.warsmash.util.RenderMathUtils;
import com.etheller.warsmash.viewer5.SceneUpdateWorkers;
import com.hiveworkshop.rms.parsers.mdlx.AnimationMap;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxTimeline;

/**
 * The keys of one timeline in each sequence of its model (or in its one global sequence). A model of an HD map has
 * a few hundred thousand of these runs, so they are kept as numbers in arrays, not as an object each.
 */
public final class SdSequence<TYPE> {
	private static final boolean INJECT_FRAMES_GHOSTWOLF_STYLE = false;

	// The interval of every sequence, those of the model shared by all its timelines.
	private final int[] starts;
	private final int[] ends;
	// The keys of HD models fill most of a phone's heap. Float keys are kept in flat arrays: those of the whole
	// timeline, shared by its sequences, where the sequence's keys are a run of them (first, count); otherwise the
	// sequence's own. A constant sequence keeps only its first key; one without keys has none and shows the
	// timeline's default value.
	// Per sequence: the index of its first key, then its number of keys shifted up one with a set bit when constant.
	private int[] runs;
	// The keys of each sequence; null when they are the same holder in all that have keys.
	private final SharedKeys[] keys;
	private final SharedKeys sameKeys;
	// For a rotation timeline that is played: the slerp setups of every pair of keys a sequence goes between, made
	// when first needed (the sequences of a model that are never played do not get one). The scene update's worker
	// threads may make them at the same time; they all make the same ones.
	private volatile AtomicReferenceArray<float[]> slerpSetups;
	// Keys taken out of the flat arrays for the interpolation are put in the calling thread's own heaps.
	private static final float[][] NO_TANS = new float[0][];

	public int size() {
		return this.starts.length;
	}

	public long getEnd(final int sequence) {
		return this.ends[sequence];
	}

	public boolean isConstant(final int sequence) {
		return (this.runs[(sequence * 2) + 1] & 1) != 0;
	}

	/**
	 * The runs of a timeline in the sequences with these intervals; the arrays are kept, not copied. A global
	 * sequence is one interval.
	 */
	public SdSequence(final Sd<TYPE> sd, final int[] starts, final int[] ends, final MdlxTimeline<TYPE> timeline,
			final boolean isGlobalSequence, final SdArrayDescriptor<TYPE> arrayDescriptor, final SharedKeys shared) {
		this.starts = starts;
		this.ends = ends;
		final int sequences = starts.length;
		this.runs = new int[sequences * 2];
		final SharedKeys[] runKeys = new SharedKeys[sequences];
		final long[] frames = timeline.getFrames();
		SharedKeys same = null;
		boolean allSame = true;
		for (int i = 0; i < sequences; i++) {
			// A sequence of the model without a key of this timeline shows the default value.
			if (!isGlobalSequence && !hasKeyIn(frames, starts[i], ends[i])) {
				continue;
			}
			buildRun(sd, i, timeline, isGlobalSequence, arrayDescriptor, shared, runKeys);
			if (same == null) {
				same = runKeys[i];
			}
			else if (same != runKeys[i]) {
				allSame = false;
			}
		}
		final ArrayPool pool = sd.model.arrayPool;
		if (pool != null) {
			// Timelines of a model mostly put their keys alike, and many have none in any sequence.
			this.runs = pool.intern(this.runs);
		}
		this.sameKeys = allSame ? same : null;
		this.keys = allSame ? null : runKeys;
	}

	private static boolean hasKeyIn(final long[] frames, final long start, final long end) {
		for (final long frame : frames) {
			if ((frame >= start) && (frame <= end)) {
				return true;
			}
		}
		return false;
	}

	private void buildRun(final Sd<TYPE> sd, final int index, final MdlxTimeline<TYPE> timeline,
			final boolean isGlobalSequence, final SdArrayDescriptor<TYPE> arrayDescriptor, final SharedKeys shared,
			final SharedKeys[] runKeys) {
		final long start = this.starts[index];
		final long end = this.ends[index];
		final ArrayList<Long> framesBuilder = new ArrayList<>();
		final ArrayList<TYPE> valuesBuilder = new ArrayList<>();
		final ArrayList<TYPE> inTansBuilder = new ArrayList<>();
		final ArrayList<TYPE> outTansBuilder = new ArrayList<>();
		boolean constant = false;

		final int interpolationType = sd.interpolationType;
		final long[] frames = timeline.getFrames();
		final TYPE[] values = getValues(timeline);
		final TYPE[] inTans = getInTans(timeline);
		final TYPE[] outTans = getOutTans(timeline);
		final TYPE defval = sd.defval;

		// When using a global sequence, where the first key is outside of the
		// sequence's length, it becomes its constant value.
		// When having one key in the sequence's range, and one key outside of
		// it, results seem to be non-deterministic.
		// Sometimes the second key is used too, sometimes not.
		// It also differs depending where the model is viewed - the WE
		// previewer, the WE itself, or the game.
		// All three show different results, none of them make sense.
		// Therefore, only handle the case where the first key is outside.
		// This fixes problems spread over many models, e.g. HeroMountainKing
		// (compare in WE and in Magos).
		final boolean outsideKeyAdded = isGlobalSequence && (frames.length > 0) && (frames[0] > end);
		int firstInRange = -1;
		int lastInRange = -1;
		if (outsideKeyAdded) {
			if (start == end) {
				framesBuilder.add(start);
			}
			else {
				framesBuilder.add(frames[0]);
			}
			valuesBuilder.add(values[0]);
		}

		// Go over the keyframes, and add all of the ones that are in this
		// sequence (start <= frame <= end).
		for (int i = 0, l = frames.length; i < l; i++) {
			final long frame = frames[i];

			if ((frame >= start) && (frame <= end)) {
				if (firstInRange < 0) {
					firstInRange = i;
				}
				lastInRange = i;
				framesBuilder.add(frame);
				valuesBuilder.add(values[i]);

				if (interpolationType > 1) {
					inTansBuilder.add(inTans[i]);
					outTansBuilder.add(outTans[i]);
				}
			}
		}

		final int keyframeCount = framesBuilder.size();

		if (keyframeCount == 0) {
			// if there are no keys, use the default value directly.
			constant = true;
			framesBuilder.add(start);
			valuesBuilder.add(defval);
		}
		else if (keyframeCount == 1) {
			// If there's only one key, use it directly
			constant = true;
		}
		else {
			final TYPE firstValue = valuesBuilder.get(0);

			// If all of the values in this sequence are the same, might as well
			// make it constant.
			boolean allFramesMatch = true;
			for (final TYPE value : valuesBuilder) {
				if (!equals(firstValue, value)) {
					allFramesMatch = false;
				}
			}
			constant = allFramesMatch;

			if (!constant && INJECT_FRAMES_GHOSTWOLF_STYLE) {
				// If there is no opening keyframe for this sequence, inject one
				// with the default value.
				final boolean hasStart = framesBuilder.get(0) == start;
				if (!hasStart) {
					framesBuilder.add(start);
					valuesBuilder.add(defval);

					if (interpolationType > 1) {
						inTansBuilder.add(defval);
						outTansBuilder.add(defval);
					}
				}

				// If there is no closing keyframe for this sequence, inject one
				// with the default value.
				if (framesBuilder.get(framesBuilder.size() - 1) != end) {
					framesBuilder.add(end);
					final int sourceIndex = hasStart ? 0 : (valuesBuilder.size() - 1);
					valuesBuilder.add(valuesBuilder.get(sourceIndex));

					if (interpolationType > 1) {
						inTansBuilder.add(inTansBuilder.get(sourceIndex));
						outTansBuilder.add(outTansBuilder.get(sourceIndex));
					}
				}
			}
		}
		final int keyCount = constant ? 1 : framesBuilder.size();
		final int tans = constant ? 0 : inTansBuilder.size();
		this.runs[index * 2 + 1] = (keyCount << 1) | (constant ? 1 : 0);
		if ((shared != null) && !outsideKeyAdded && (firstInRange >= 0)
				&& ((lastInRange - firstInRange + 1) == framesBuilder.size())
				&& ((tans == 0) || ((tans == keyCount) && (shared.inTans != null)))) {
			// Sequences that use the keys the same way share one holder; the others of the timeline hold the
			// tangents of their own use.
			runKeys[index] = tans == 0 ? shared.withoutTans() : shared;
			this.runs[index * 2] = firstInRange;
			return;
		}
		final int[] keyFrames = new int[keyCount];
		for (int i = 0; i < keyCount; i++) {
			keyFrames[i] = framesBuilder.get(i).intValue();
		}
		final int stride = packedStride(valuesBuilder, keyCount, inTansBuilder, outTansBuilder, tans);
		if (stride > 0) {
			runKeys[index] = new SharedKeys(keyFrames, pack(valuesBuilder, keyCount, stride),
					tans == 0 ? null : pack(inTansBuilder, tans, stride),
					tans == 0 ? null : pack(outTansBuilder, tans, stride), stride);
		}
		else {
			final SharedKeys own = new SharedKeys(keyFrames, null, null, null, 0);
			own.objectValues = valuesBuilder.subList(0, keyCount).toArray(arrayDescriptor.create(keyCount));
			own.objectInTans = inTansBuilder.subList(0, tans).toArray(arrayDescriptor.create(tans));
			own.objectOutTans = outTansBuilder.subList(0, tans).toArray(arrayDescriptor.create(tans));
			runKeys[index] = own;
		}
	}

	/** The float keys of one timeline in flat arrays, or null when they are not float keys of one length. */
	static SharedKeys sharedKeys(final ArrayPool pool, final MdlxTimeline<?> timeline) {
		final long[] frames = timeline.getFrames();
		final Object[] values = fixTimelineArray(timeline, timeline.getValues());
		final Object[] inTans = fixTimelineArray(timeline, timeline.getInTans());
		final Object[] outTans = fixTimelineArray(timeline, timeline.getOutTans());
		final int keys = frames.length;
		if ((keys == 0) || (values == null) || (values.length != keys)) {
			return null;
		}
		final boolean hasTans = (inTans != null) && (outTans != null) && (inTans.length == keys)
				&& (outTans.length == keys);
		final ArrayList<Object> valueList = new ArrayList<>(Arrays.asList(values));
		final ArrayList<Object> inList = hasTans ? new ArrayList<>(Arrays.asList(inTans)) : new ArrayList<>();
		final ArrayList<Object> outList = hasTans ? new ArrayList<>(Arrays.asList(outTans)) : new ArrayList<>();
		final int stride = packedStride(valueList, keys, inList, outList, hasTans ? keys : 0);
		if (stride == 0) {
			return null;
		}
		final int[] intFrames = new int[keys];
		for (int i = 0; i < keys; i++) {
			intFrames[i] = (int) frames[i];
		}
		if (pool == null) {
			return new SharedKeys(intFrames, pack(valueList, keys, stride),
					hasTans ? pack(inList, keys, stride) : null, hasTans ? pack(outList, keys, stride) : null, stride);
		}
		return new SharedKeys(pool.intern(intFrames), pool.intern(pack(valueList, keys, stride)),
				pool.intern(hasTans ? pack(inList, keys, stride) : null),
				pool.intern(hasTans ? pack(outList, keys, stride) : null), stride);
	}

	static final class SharedKeys {
		private final int[] frames;
		private final float[] values;
		private final float[] inTans;
		private final float[] outTans;
		private final int stride;
		private Object[] objectValues;
		private Object[] objectInTans;
		private Object[] objectOutTans;
		private SharedKeys noTans;

		private SharedKeys(final int[] frames, final float[] values, final float[] inTans, final float[] outTans,
				final int stride) {
			this.frames = frames;
			this.values = values;
			this.inTans = inTans;
			this.outTans = outTans;
			this.stride = stride;
		}

		private SharedKeys withoutTans() {
			if ((this.inTans == null) || (this.values == null)) {
				return this;
			}
			if (this.noTans == null) {
				this.noTans = new SharedKeys(this.frames, this.values, null, null, this.stride);
			}
			return this.noTans;
		}
	}

	/** The length of every key when all are float arrays of one length the flat form handles, else 0. */
	private static int packedStride(final ArrayList<?> values, final int keys, final ArrayList<?> inTans,
			final ArrayList<?> outTans, final int tans) {
		if (!(values.get(0) instanceof float[]) || ((tans != 0) && (tans != keys))) {
			return 0;
		}
		final int stride = ((float[]) values.get(0)).length;
		if ((stride < 1) || (stride > SceneUpdateWorkers.Heaps.MAX_KEY_LENGTH)) {
			return 0;
		}
		for (int i = 0; i < keys; i++) {
			if (((float[]) values.get(i)).length != stride) {
				return 0;
			}
		}
		for (int i = 0; i < tans; i++) {
			if ((((float[]) inTans.get(i)).length != stride) || (((float[]) outTans.get(i)).length != stride)) {
				return 0;
			}
		}
		return stride;
	}

	private static float[] pack(final ArrayList<?> keys, final int count, final int stride) {
		final float[] packed = new float[count * stride];
		for (int i = 0; i < count; i++) {
			System.arraycopy(keys.get(i), 0, packed, i * stride, stride);
		}
		return packed;
	}

	private static float[] unpack(final float[] packed, final int index, final float[] key) {
		System.arraycopy(packed, index * key.length, key, 0, key.length);
		return key;
	}

	private TYPE[] getValues(final MdlxTimeline<TYPE> timeline) {
		final TYPE[] values = timeline.getValues();
		return fixTimelineArray(timeline, values);
	}

	private TYPE[] getOutTans(final MdlxTimeline<TYPE> timeline) {
		final TYPE[] outTans = timeline.getOutTans();
		return fixTimelineArray(timeline, outTans);
	}

	private TYPE[] getInTans(final MdlxTimeline<TYPE> timeline) {
		final TYPE[] inTans = timeline.getInTans();
		return fixTimelineArray(timeline, inTans);
	}

	@SuppressWarnings("unchecked")
	private static <T> T[] fixTimelineArray(final MdlxTimeline<?> timeline, final T[] values) {
		if (values == null) {
			return null;
		}
		if (timeline.getName().equals(AnimationMap.KLAC.getWar3id())
				|| timeline.getName().equals(AnimationMap.KLBC.getWar3id())) {
			final float[][] flippedColorData = new float[values.length][3];
			for (int i = 0; i < values.length; i++) {
				flippedColorData[i] = ParseUtils.newFlippedRGB((float[]) values[i]);
			}
			return (T[]) flippedColorData;
		}
		return values;
	}

	@SuppressWarnings("unchecked")
	public int getValue(final Sd<TYPE> sd, final TYPE out, final int sequence, final long frame) {
		final int run = this.runs[(sequence * 2) + 1];
		if (run == 0) {
			sd.copy(out, sd.defval);

			return -1;
		}
		final SharedKeys keys = this.keys == null ? this.sameKeys : this.keys[sequence];
		final int length = run >>> 1;
		final int[] frames = keys.frames;
		final int first = this.runs[sequence * 2];
		final int start = this.starts[sequence];

		if (((run & 1) != 0) || (frame < start)) {
			if (keys.values == null) {
				sd.copy(out, (TYPE) keys.objectValues[0]);
			}
			else if (!copyPacked(sd, keys, out, first)) {
				sd.copy(out, (TYPE) unpack(keys.values, first, SceneUpdateWorkers.heaps().keys[keys.stride][0]));
			}

			return -1;
		}
		else {
			int startFrameIndex = -1;
			int endFrameIndex = -1;
			final int lengthLessOne = length - 1;
			if ((frame < frames[first]) || (frame >= frames[first + lengthLessOne])) {
				startFrameIndex = lengthLessOne;
				endFrameIndex = 0;
			}
			else {
				for (int i = 1; i < length; i++) {
					if (frames[first + i] > frame) {
						startFrameIndex = i - 1;
						endFrameIndex = i;
						break;
					}
				}
			}
			long startFrame = frames[first + startFrameIndex];
			final long endFrame = frames[first + endFrameIndex];
			long timeBetweenFrames = endFrame - startFrame;
			if (timeBetweenFrames < 0) {
				timeBetweenFrames += (this.ends[sequence] - start);
				if (frame < startFrame) {
					startFrame = endFrame;
				}
			}
			final float t = ((timeBetweenFrames) == 0 ? 0 : ((frame - startFrame) / (float) (timeBetweenFrames)));
			if (keys.values != null) {
				if (!interpolateRotation(sd, sequence, keys, out, startFrameIndex, first + startFrameIndex,
						first + endFrameIndex, t)
						&& !interpolateFlat(sd, keys, out, first + startFrameIndex, first + endFrameIndex, t)) {
					interpolatePacked(sd, keys, out, first + startFrameIndex, first + endFrameIndex, t);
				}
			}
			else {
				sd.interpolate(out, (TYPE[]) keys.objectValues, (TYPE[]) keys.objectInTans,
						(TYPE[]) keys.objectOutTans, startFrameIndex, endFrameIndex, t);
			}
			return startFrameIndex;
		}
	}

	/*
	 * What copy and interpolate of the three float timelines do, read straight from the flat arrays: the scratch
	 * arrays of the general path below are the calling thread's, and finding the calling thread costs more than the
	 * whole interpolation on Android. The cases left out (unusual key lengths, tangent interpolation without
	 * tangents) take the general path.
	 */
	private static boolean copyPacked(final Sd<?> sd, final SharedKeys keys, final Object out, final int index) {
		final int stride = keys.stride;
		final float[] values = keys.values;
		if (sd instanceof ScalarSd) {
			((float[]) out)[0] = values[index * stride];
			return true;
		}
		if ((sd instanceof VectorSd) || (sd instanceof QuaternionSd)) {
			System.arraycopy(values, index * stride, out, 0, stride);
			return true;
		}
		return false;
	}

	private static boolean interpolateFlat(final Sd<?> sd, final SharedKeys keys, final Object target,
			final int startIndex, final int endIndex, final float t) {
		final int type = sd.interpolationType;
		final int stride = keys.stride;
		final float[] values = keys.values;
		final boolean tangents = keys.inTans != null;
		if (sd instanceof ScalarSd) {
			if (stride != 1) {
				return false;
			}
			final float[] out = (float[]) target;
			final float startValue = values[startIndex];
			switch (type) {
			case 0:
				out[0] = startValue;
				break;
			case 1:
				out[0] = RenderMathUtils.lerp(startValue, values[endIndex], t);
				break;
			case 2:
				out[0] = RenderMathUtils.hermite(startValue, tangents ? keys.outTans[startIndex] : 0f,
						tangents ? keys.inTans[endIndex] : 0f, values[endIndex], t);
				break;
			case 3:
				out[0] = RenderMathUtils.bezier(startValue, tangents ? keys.outTans[startIndex] : 0f,
						tangents ? keys.inTans[endIndex] : 0f, values[endIndex], t);
				break;
			}
			return true;
		}
		if (type == 0) {
			if ((sd instanceof VectorSd) || (sd instanceof QuaternionSd)) {
				System.arraycopy(values, startIndex * stride, target, 0, stride);
				return true;
			}
			return false;
		}
		if (sd instanceof VectorSd) {
			if ((stride != 3) || ((type != 1) && !tangents) || (type > 3)) {
				return false;
			}
			final float[] out = (float[]) target;
			final int a = startIndex * 3;
			final int d = endIndex * 3;
			if (type == 1) {
				out[0] = RenderMathUtils.lerp(values[a], values[d], t);
				out[1] = RenderMathUtils.lerp(values[a + 1], values[d + 1], t);
				out[2] = RenderMathUtils.lerp(values[a + 2], values[d + 2], t);
			}
			else {
				final float[] outTans = keys.outTans;
				final float[] inTans = keys.inTans;
				if (type == 2) {
					out[0] = RenderMathUtils.hermite(values[a], outTans[a], inTans[d], values[d], t);
					out[1] = RenderMathUtils.hermite(values[a + 1], outTans[a + 1], inTans[d + 1], values[d + 1], t);
					out[2] = RenderMathUtils.hermite(values[a + 2], outTans[a + 2], inTans[d + 2], values[d + 2], t);
				}
				else {
					out[0] = RenderMathUtils.bezier(values[a], outTans[a], inTans[d], values[d], t);
					out[1] = RenderMathUtils.bezier(values[a + 1], outTans[a + 1], inTans[d + 1], values[d + 1], t);
					out[2] = RenderMathUtils.bezier(values[a + 2], outTans[a + 2], inTans[d + 2], values[d + 2], t);
				}
			}
			return true;
		}
		if ((sd instanceof QuaternionSd) && (stride == 4)) {
			final int a = startIndex * 4;
			final int d = endIndex * 4;
			if (type == 1) {
				RenderMathUtils.slerp((float[]) target, values, a, values, d, t);
				return true;
			}
			if (((type == 2) || (type == 3)) && tangents) {
				RenderMathUtils.sqlerp((float[]) target, values, a, keys.outTans, a, keys.inTans, d, values, d, t);
				return true;
			}
		}
		return false;
	}

	/** The keys are put at 0 and 1 of arrays shaped as the unpacked ones, so the interpolation is the same. */
	@SuppressWarnings("unchecked")
	private void interpolatePacked(final Sd<TYPE> sd, final SharedKeys keys, final TYPE out, final int startIndex,
			final int endIndex, final float t) {
		final SceneUpdateWorkers.Heaps heaps = SceneUpdateWorkers.heaps();
		final float[][] heap = heaps.keys[keys.stride];
		final float[][] valuesHeap = heaps.values;
		valuesHeap[0] = unpack(keys.values, startIndex, heap[0]);
		valuesHeap[1] = unpack(keys.values, endIndex, heap[1]);
		float[][] inTans = NO_TANS;
		float[][] outTans = NO_TANS;
		if (keys.inTans != null) {
			heaps.outTans[0] = unpack(keys.outTans, startIndex, heap[2]);
			heaps.inTans[1] = unpack(keys.inTans, endIndex, heap[3]);
			inTans = heaps.inTans;
			outTans = heaps.outTans;
		}
		sd.interpolate(out, (TYPE[]) valuesHeap, (TYPE[]) inTans, (TYPE[]) outTans, 0, 1, t);
	}

	/**
	 * What {@link QuaternionSd#interpolate} does with the keys of the segment that starts at key index segment
	 * (the last one goes back to the first), with the slerp setups of the segment's pairs of keys kept; false when
	 * the sequence is not one of those.
	 */
	private boolean interpolateRotation(final Sd<?> sd, final int sequence, final SharedKeys keys, final Object out,
			final int segment, final int startIndex, final int endIndex, final float t) {
		final int type = sd.interpolationType;
		if ((keys.stride != 4) || !(sd instanceof QuaternionSd) || (type < 1) || (type > 3)
				|| ((type != 1) && (keys.inTans == null))) {
			return false;
		}
		final float[] setups = slerpSetups(sequence, type, keys);
		final int offset = segment * 2 * RenderMathUtils.SLERP_SETUP_SIZE;
		final int a = startIndex * 4;
		final int d = endIndex * 4;
		if (type == 1) {
			RenderMathUtils.slerp((float[]) out, keys.values, a, keys.values, d, t, setups, offset);
		}
		else {
			RenderMathUtils.sqlerp((float[]) out, keys.values, a, keys.outTans, a, keys.inTans, d, keys.values, d, t,
					setups, offset);
		}
		return true;
	}

	/** The slerp setups of the pairs of keys of every segment, (key, next key) and (out tangent, next in tangent). */
	private float[] slerpSetups(final int sequence, final int interpolationType, final SharedKeys keys) {
		AtomicReferenceArray<float[]> all = this.slerpSetups;
		if (all == null) {
			all = new AtomicReferenceArray<>(this.starts.length);
			this.slerpSetups = all;
		}
		float[] setups = all.get(sequence);
		if (setups == null) {
			final int count = this.runs[(sequence * 2) + 1] >>> 1;
			final int first = this.runs[sequence * 2];
			setups = new float[count * 2 * RenderMathUtils.SLERP_SETUP_SIZE];
			for (int segment = 0; segment < count; segment++) {
				final int start = first + segment;
				final int end = segment == (count - 1) ? first : start + 1;
				final int offset = segment * 2 * RenderMathUtils.SLERP_SETUP_SIZE;
				RenderMathUtils.slerpSetup(keys.values, start * 4, keys.values, end * 4, setups, offset);
				if (interpolationType != 1) {
					RenderMathUtils.slerpSetup(keys.outTans, start * 4, keys.inTans, end * 4, setups,
							offset + RenderMathUtils.SLERP_SETUP_SIZE);
				}
			}
			all.set(sequence, setups);
		}
		return setups;
	}

	protected final boolean equals(final TYPE a, final TYPE b) {
		if ((a instanceof Float) && (b instanceof Float)) {
			return a.equals(b);
		}
		else if ((a instanceof Long) && (b instanceof Long)) {
			return a.equals(b);
		}
		else if ((a instanceof float[]) && (b instanceof float[])) {
			return Arrays.equals(((float[]) a), (float[]) b);
		}
		else if ((a instanceof long[]) && (b instanceof long[])) {
			return Arrays.equals(((long[]) a), (long[]) b);
		}
		return false;
	}
}
