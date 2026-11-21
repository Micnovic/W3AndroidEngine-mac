package com.etheller.warsmash.viewer5.handlers.mdx;

import java.util.HashMap;
import java.util.Map;

import com.etheller.warsmash.util.War3ID;
import com.hiveworkshop.rms.parsers.mdlx.MdlxAnimatedObject;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxFloatArrayTimeline;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxFloatTimeline;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxTimeline;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxUInt32Timeline;

public class AnimatedObject {
	public MdxModel model;
	public final VariantFlags variants = new VariantFlags();
	// An object has a handful of timelines and they are read for every node every frame, so they are also kept in
	// arrays searched by id: cheaper than a hash map lookup, which showed up in phone frame times.
	private int[] timelineIds = new int[0];
	private Sd<?>[] timelineArray = new Sd<?>[0];

	public AnimatedObject(final MdxModel model, final MdlxAnimatedObject object) {
		this.model = model;
		final Map<War3ID, Sd<?>> timelines = new HashMap<>();

		for (final MdlxTimeline<?> timeline : object.getTimelines()) {
			timelines.put(timeline.getName(), createTypedSd(model, timeline));
		}
		this.timelineIds = new int[timelines.size()];
		this.timelineArray = new Sd<?>[timelines.size()];
		int index = 0;
		for (final Map.Entry<War3ID, Sd<?>> entry : timelines.entrySet()) {
			this.timelineIds[index] = entry.getKey().getValue();
			this.timelineArray[index] = entry.getValue();
			index++;
		}
	}

	public boolean hasTimeline(final War3ID name) {
		return findTimeline(name) != null;
	}

	private Sd<?> findTimeline(final War3ID name) {
		final int id = name.getValue();
		final int[] ids = this.timelineIds;
		for (int i = 0; i < ids.length; i++) {
			if (ids[i] == id) {
				return this.timelineArray[i];
			}
		}
		return null;
	}

	public int getScalarValue(final float[] out, final War3ID name, final int sequence, final int frame,
			final int counter, final float defaultValue) {
		if (sequence != -1) {
			final Sd<?> animation = findTimeline(name);

			if (animation instanceof ScalarSd) {
				return ((ScalarSd) animation).getValue(out, sequence, frame, counter);
			}
		}

		out[0] = defaultValue;

		return -1;
	}

	public int getScalarValue(final long[] out, final War3ID name, final int sequence, final int frame,
			final int counter, final long defaultValue) {
		if (sequence != -1) {
			final Sd<?> animation = findTimeline(name);

			if (animation instanceof UInt32Sd) {
				return ((UInt32Sd) animation).getValue(out, sequence, frame, counter);
			}
		}

		out[0] = defaultValue;

		return -1;
	}

	public int getVectorValue(final float[] out, final War3ID name, final int sequence, final int frame,
			final int counter, final float[] defaultValue) {
		if (sequence != -1) {
			final Sd<?> animation = findTimeline(name);

			if (animation instanceof VectorSd) {
				return ((VectorSd) animation).getValue(out, sequence, frame, counter);
			}
		}

		System.arraycopy(defaultValue, 0, out, 0, 3);

		return -1;
	}

	public int getQuatValue(final float[] out, final War3ID name, final int sequence, final int frame,
			final int counter, final float[] defaultValue) {
		if (sequence != -1) {
			final Sd<?> animation = findTimeline(name);

			if (animation instanceof QuaternionSd) {
				return ((QuaternionSd) animation).getValue(out, sequence, frame, counter);
			}
		}

		System.arraycopy(defaultValue, 0, out, 0, 4);

		return -1;
	}

	public void addVariants(final War3ID name, final String variantName) {
		final Sd<?> timeline = findTimeline(name);
		final int sequences = this.model.getSequences().size();
		byte[] variants = new byte[sequences];

		boolean any = false;
		if (timeline != null) {
			for (int i = 0; i < sequences; i++) {
				if (timeline.isVariant(i)) {
					variants[i] = 1;
					any = true;
				}
			}
		}
		if (!any) {
			// The flags are only read: the objects without a varying timeline share one array of the model.
			variants = this.model.noVariants(sequences);
		}

		this.variants.put(variantName, variants);
	}

	/** The flags of an object's timelines by name: an object has two or three, so a plain list. */
	public static final class VariantFlags {
		private String[] names = new String[0];
		private byte[][] flags = new byte[0][];

		public byte[] get(final String name) {
			for (int i = 0; i < this.names.length; i++) {
				if (this.names[i].equals(name)) {
					return this.flags[i];
				}
			}
			return null;
		}

		void put(final String name, final byte[] value) {
			for (int i = 0; i < this.names.length; i++) {
				if (this.names[i].equals(name)) {
					this.flags[i] = value;
					return;
				}
			}
			this.names = java.util.Arrays.copyOf(this.names, this.names.length + 1);
			this.flags = java.util.Arrays.copyOf(this.flags, this.flags.length + 1);
			this.names[this.names.length - 1] = name;
			this.flags[this.flags.length - 1] = value;
		}
	}

	public boolean isVariant(final War3ID name, final int sequence) {
		final Sd<?> timeline = findTimeline(name);

		if (timeline != null) {
			return timeline.isVariant(sequence);
		}

		return false;
	}

	private Sd<?> createTypedSd(final MdxModel model, final MdlxTimeline<?> timeline) {
		if (timeline instanceof MdlxUInt32Timeline) {
			return new UInt32Sd(model, (MdlxUInt32Timeline) timeline);
		}
		else if (timeline instanceof MdlxFloatTimeline) {
			return new ScalarSd(model, (MdlxFloatTimeline) timeline);
		}
		else if (timeline instanceof MdlxFloatArrayTimeline) {
			final MdlxFloatArrayTimeline faTimeline = (MdlxFloatArrayTimeline) timeline;
			final int arraySize = faTimeline.getArraySize();
			if (arraySize == 3) {
				return new VectorSd(model, faTimeline);
			}
			else if (arraySize == 4) {
				return new QuaternionSd(model, faTimeline);
			}
			else {
				throw new IllegalStateException("Unsupported arraySize = " + arraySize);
			}
		}
		throw new IllegalStateException("Unsupported timeline type " + timeline.getClass());
	}
}
