package com.etheller.warsmash.viewer5.handlers.mdx;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.etheller.warsmash.datasources.CompoundDataSource;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.LayeredDataSource;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.hiveworkshop.rms.parsers.mdlx.AnimationMap;
import com.hiveworkshop.rms.parsers.mdlx.InterpolationType;
import com.hiveworkshop.rms.parsers.mdlx.MdlxAnimatedObject;
import com.hiveworkshop.rms.parsers.mdlx.MdlxGenericObject;
import com.hiveworkshop.rms.parsers.mdlx.MdlxLayer;
import com.hiveworkshop.rms.parsers.mdlx.MdlxMaterial;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdlxParticleEmitter;
import com.hiveworkshop.rms.parsers.mdlx.MdlxParticleEmitter2;
import com.hiveworkshop.rms.parsers.mdlx.MdlxParticleEmitterPopcorn;
import com.hiveworkshop.rms.parsers.mdlx.MdlxRibbonEmitter;
import com.hiveworkshop.rms.parsers.mdlx.MdlxSequence;
import com.hiveworkshop.rms.parsers.mdlx.MdlxTexture;
import com.hiveworkshop.rms.parsers.mdlx.MdlxTextureAnimation;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxFloatTimeline;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxTimeline;

/**
 * The effects of the Reforged graphics packages are mostly "popcorn" emitters: they name a PopcornFX effect file
 * ({@code CORN} chunk) that this engine cannot play, so a model with only those draws nothing. The Classic data has
 * the same model with particle emitters, ribbons and event objects, which are drawn instead: a model without any
 * geometry is replaced by the Classic one, and a model with geometry (a doodad, a building, a unit) keeps its own
 * meshes and takes the Classic emitters, attached to the bones of the same name.
 */
final class ClassicEffects {
	private ClassicEffects() {
	}

	/**
	 * Scale of the emission of the Classic emitters taken over from the Classic files, to spend less on particles where
	 * the GPU is weak (system property {@code warsmash.vfx.scale}); zero leaves the effects out.
	 */
	public static volatile float emissionScale = readScale();

	private static final String GLOW_TEXTURE = "Textures\\Genericglow64.blp";
	private static final String GLOW_TEXTURE_ALT = "Textures\\GenericGlow64.blp";
	private static final int MAX_FALLBACK_EMITTERS = 4;

	private static float readScale() {
		try {
			return Math.max(0, Float.parseFloat(System.getProperty("warsmash.vfx.scale", "1")));
		}
		catch (final NumberFormatException e) {
			return 1;
		}
	}

	static MdlxModel resolve(final ModelViewer viewer, final String path, final MdlxModel model) {
		if ((model.getVersion() <= 800) || model.particleEmittersPopcorn.isEmpty() || (path == null)
				|| path.isEmpty()) {
			return model;
		}
		final LayeredDataSource layered = findLayered(viewer.dataSource);
		if (layered == null) {
			return model;
		}
		MdlxModel classic = null;
		for (final String candidate : classicPaths(path)) {
			try {
				final ByteBuffer buffer = layered.readClassic(candidate);
				if (buffer != null) {
					classic = new MdlxModel(buffer);
					break;
				}
			}
			catch (final Exception e) {
				Log.warn("Classic version of " + candidate + " not read: " + e);
			}
		}
		if ((classic != null) && model.geosets.isEmpty()) {
			return classic;
		}
		if (emissionScale > 0) {
			final boolean grafted = (classic != null) && graft(model, classic);
			if (!grafted) {
				synthesize(viewer, model);
			}
		}
		return model;
	}

	/**
	 * The Classic files that can stand for the model: the Reforged variants (a {@code _teen} or {@code _HD} suffix)
	 * share the effects of the plain model.
	 */
	static List<String> classicPaths(final String path) {
		final List<String> paths = new ArrayList<>();
		paths.add(path);
		final String stripped = path.replaceAll("(?i)(_teen|_hd)+(\\.[^.\\\\/]+)$", "$2");
		if (!stripped.equals(path)) {
			paths.add(stripped);
		}
		return paths;
	}

	private static LayeredDataSource findLayered(final DataSource data) {
		if (data instanceof LayeredDataSource) {
			return (LayeredDataSource) data;
		}
		if (data instanceof CompoundDataSource) {
			for (final DataSource source : ((CompoundDataSource) data).getSources()) {
				final LayeredDataSource found = findLayered(source);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** Adds the particle emitters and ribbons of the Classic model to the model; whether there were any. */
	private static boolean graft(final MdlxModel model, final MdlxModel classic) {
		if (classic.particleEmitters.isEmpty() && classic.particleEmitters2.isEmpty()
				&& classic.ribbonEmitters.isEmpty()) {
			return false;
		}
		final List<MdlxGenericObject> nodes = new ArrayList<>();
		nodes.addAll(model.bones);
		nodes.addAll(model.lights);
		nodes.addAll(model.helpers);
		nodes.addAll(model.attachments);
		nodes.addAll(model.particleEmitters);
		nodes.addAll(model.particleEmitters2);
		nodes.addAll(model.particleEmittersPopcorn);
		nodes.addAll(model.ribbonEmitters);
		nodes.addAll(model.eventObjects);
		nodes.addAll(model.collisionShapes);
		int nextId = nodes.size();
		for (final MdlxGenericObject node : nodes) {
			nextId = Math.max(nextId, node.objectId + 1);
		}
		final Map<String, Integer> idByName = new HashMap<>();
		for (final List<? extends MdlxGenericObject> group : java.util.Arrays.asList(model.bones, model.helpers,
				model.attachments)) {
			for (final MdlxGenericObject node : group) {
				idByName.putIfAbsent(node.name.toLowerCase(Locale.ROOT), node.objectId);
			}
		}
		final List<MdlxGenericObject> classicNodes = new ArrayList<>();
		classicNodes.addAll(classic.bones);
		classicNodes.addAll(classic.lights);
		classicNodes.addAll(classic.helpers);
		classicNodes.addAll(classic.attachments);
		classicNodes.addAll(classic.particleEmitters);
		classicNodes.addAll(classic.particleEmitters2);
		classicNodes.addAll(classic.ribbonEmitters);
		classicNodes.addAll(classic.eventObjects);
		classicNodes.addAll(classic.collisionShapes);
		final Map<Integer, MdlxGenericObject> classicById = new HashMap<>();
		for (final MdlxGenericObject node : classicNodes) {
			classicById.put(node.objectId, node);
		}

		final SequenceMap sequences = new SequenceMap(classic.sequences, model.sequences, model.globalSequences.size());
		final int textureBase = model.textures.size();
		model.textures.addAll(classic.textures);
		model.globalSequences.addAll(classic.globalSequences);
		final int textureAnimationBase = model.textureAnimations.size();
		for (final MdlxTextureAnimation textureAnimation : classic.textureAnimations) {
			sequences.remap(textureAnimation);
			model.textureAnimations.add(textureAnimation);
		}
		final int materialBase = model.materials.size();
		for (final MdlxMaterial material : classic.materials) {
			for (final MdlxLayer layer : material.layers) {
				if (layer.textureId >= 0) {
					layer.textureId += textureBase;
				}
				if (layer.textureAnimationId >= 0) {
					layer.textureAnimationId += textureAnimationBase;
				}
				sequences.remap(layer);
			}
			model.materials.add(material);
		}

		while (model.pivotPoints.size() < nextId) {
			model.pivotPoints.add(new float[3]);
		}
		final float scale = emissionScale;
		final List<MdlxParticleEmitterPopcorn> popcorn = model.particleEmittersPopcorn;
		for (final MdlxParticleEmitter emitter : classic.particleEmitters) {
			place(model, emitter, nextId++, classic, classicById, idByName, sequences);
			emitter.emissionRate *= scale;
			scaleTimeline(emitter, AnimationMap.KPEE.getWar3id(), scale);
			sequences.gate(model, emitter, AnimationMap.KPEV.getWar3id(), popcorn);
			model.particleEmitters.add(emitter);
		}
		for (final MdlxParticleEmitter2 emitter : classic.particleEmitters2) {
			place(model, emitter, nextId++, classic, classicById, idByName, sequences);
			if (emitter.replaceableId == 0) {
				emitter.textureId += textureBase;
			}
			emitter.emissionRate *= scale;
			scaleTimeline(emitter, AnimationMap.KP2E.getWar3id(), scale);
			sequences.gate(model, emitter, AnimationMap.KP2V.getWar3id(), popcorn);
			model.particleEmitters2.add(emitter);
		}
		for (final MdlxRibbonEmitter emitter : classic.ribbonEmitters) {
			place(model, emitter, nextId++, classic, classicById, idByName, sequences);
			emitter.materialId += materialBase;
			sequences.gate(model, emitter, AnimationMap.KRVS.getWar3id(), popcorn);
			model.ribbonEmitters.add(emitter);
		}
		return true;
	}

	private static void scaleTimeline(final MdlxAnimatedObject object, final War3ID name, final float scale) {
		if (scale == 1) {
			return;
		}
		final MdlxTimeline<?> timeline = findTimeline(object, name);
		if (timeline != null) {
			for (final Object value : timeline.values) {
				((float[]) value)[0] *= scale;
			}
		}
	}

	/**
	 * Models without a Classic counterpart: each popcorn emitter gets a plain glow emitter of its own life span, rate,
	 * speed and color, with its visibility and rate tracks, so that something shows in its place. The PopcornFX effect
	 * itself is not played.
	 */
	private static void synthesize(final ModelViewer viewer, final MdlxModel model) {
		final String texture = viewer.dataSource.findFirst(GLOW_TEXTURE, GLOW_TEXTURE_ALT);
		if (texture == null) {
			return;
		}
		int nextId = model.pivotPoints.size();
		for (final MdlxGenericObject node : model.particleEmittersPopcorn) {
			nextId = Math.max(nextId, node.objectId + 1);
		}
		for (final MdlxGenericObject node : model.bones) {
			nextId = Math.max(nextId, node.objectId + 1);
		}
		for (final MdlxGenericObject node : model.particleEmitters2) {
			nextId = Math.max(nextId, node.objectId + 1);
		}
		int textureId = -1;
		int count = 0;
		for (final MdlxParticleEmitterPopcorn popcorn : model.particleEmittersPopcorn) {
			if (count == MAX_FALLBACK_EMITTERS) {
				break;
			}
			final MdlxTimeline<?> rate = findTimeline(popcorn, AnimationMap.KPPE.getWar3id());
			if ((popcorn.emissionRate <= 0) && (rate == null)) {
				continue;
			}
			if (textureId == -1) {
				final MdlxTexture glow = new MdlxTexture();
				glow.path = texture;
				model.textures.add(glow);
				textureId = model.textures.size() - 1;
			}
			final MdlxParticleEmitter2 emitter = new MdlxParticleEmitter2();
			emitter.name = popcorn.name + " (fallback)";
			emitter.objectId = nextId++;
			emitter.parentId = popcorn.parentId;
			emitter.flags |= popcorn.flags & (0x10000 | 0x8000 | 0x40000);
			emitter.speed = popcorn.speed;
			emitter.variation = 0.2f;
			emitter.latitude = 180;
			emitter.lifeSpan = popcorn.lifeSpan > 0 ? popcorn.lifeSpan : 1;
			emitter.emissionRate = Math.min(Math.max(popcorn.emissionRate, 1), 40) * emissionScale;
			emitter.width = 10;
			emitter.length = 10;
			emitter.filterMode = MdlxParticleEmitter2.FilterMode.ADDITIVE;
			emitter.rows = 1;
			emitter.columns = 1;
			emitter.timeMiddle = 0.5f;
			final short alpha = (short) (popcorn.alpha > 0 ? Math.min(popcorn.alpha, 1) * 255 : 160);
			for (int i = 0; i < 3; i++) {
				System.arraycopy(popcorn.color, 0, emitter.segmentColors[i], 0, 3);
			}
			emitter.segmentAlphas = new short[] { 0, alpha, 0 };
			emitter.segmentScaling = new float[] { 12, 18, 6 };
			emitter.headIntervals = new long[][] { { 0, 0, 1 }, { 0, 0, 1 } };
			emitter.tailIntervals = new long[][] { { 0, 0, 1 }, { 0, 0, 1 } };
			emitter.textureId = textureId;
			copyTimeline(popcorn, AnimationMap.KPPV.getWar3id(), emitter, AnimationMap.KP2V.getWar3id(), 1);
			copyTimeline(popcorn, AnimationMap.KPPE.getWar3id(), emitter, AnimationMap.KP2E.getWar3id(),
					emissionScale);
			while (model.pivotPoints.size() <= emitter.objectId) {
				model.pivotPoints.add(new float[3]);
			}
			if (popcorn.objectId < model.pivotPoints.size()) {
				model.pivotPoints.set(emitter.objectId, model.pivotPoints.get(popcorn.objectId));
			}
			model.particleEmitters2.add(emitter);
			count++;
		}
	}

	private static MdlxTimeline<?> findTimeline(final MdlxAnimatedObject object, final War3ID name) {
		for (final MdlxTimeline<?> timeline : object.timelines) {
			if (name.equals(timeline.name)) {
				return timeline;
			}
		}
		return null;
	}

	private static void copyTimeline(final MdlxAnimatedObject from, final War3ID fromName, final MdlxAnimatedObject to,
			final War3ID toName, final float scale) {
		final MdlxTimeline<?> source = findTimeline(from, fromName);
		if (!(source instanceof MdlxFloatTimeline)) {
			return;
		}
		final MdlxFloatTimeline copy = new MdlxFloatTimeline();
		copy.name = toName;
		copy.interpolationType = source.interpolationType;
		copy.globalSequenceId = source.globalSequenceId;
		copy.frames = source.frames.clone();
		copy.values = new float[source.values.length][];
		for (int i = 0; i < copy.values.length; i++) {
			copy.values[i] = new float[] { ((float[]) source.values[i])[0] * scale };
		}
		if (source.interpolationType.tangential()) {
			copy.inTans = new float[source.inTans.length][];
			copy.outTans = new float[source.outTans.length][];
			for (int i = 0; i < copy.inTans.length; i++) {
				copy.inTans[i] = new float[] { ((float[]) source.inTans[i])[0] * scale };
				copy.outTans[i] = new float[] { ((float[]) source.outTans[i])[0] * scale };
			}
		}
		to.timelines.add(copy);
	}

	private static void place(final MdlxModel model, final MdlxGenericObject emitter, final int id,
			final MdlxModel classic, final Map<Integer, MdlxGenericObject> classicById,
			final Map<String, Integer> idByName, final SequenceMap sequences) {
		final int classicId = emitter.objectId;
		int parent = emitter.parentId;
		// The nearest bone up the Classic chain that the model has by name.
		int parentId = -1;
		while ((parent != -1) && (parent != classicId)) {
			final MdlxGenericObject node = classicById.get(parent);
			if (node == null) {
				break;
			}
			final Integer found = idByName.get(node.name.toLowerCase(Locale.ROOT));
			if (found != null) {
				parentId = found;
				break;
			}
			parent = node.parentId;
		}
		emitter.objectId = id;
		emitter.parentId = parentId;
		if (classicId < classic.pivotPoints.size()) {
			model.pivotPoints.add(classic.pivotPoints.get(classicId));
		}
		else {
			model.pivotPoints.add(new float[3]);
		}
		sequences.remap(emitter);
	}

	/**
	 * Moves the key frames of the Classic model into the sequences of the model; the sequences of the two models are
	 * not the same length, and the Reforged ones are named differently. Each sequence of the model takes the keys of the
	 * Classic sequence of the same name, or of the same name without its number, or of the same first word. A sequence
	 * without a counterpart gets no keys, and the effects are put out in it (see {@link #gate}).
	 */
	private static final class SequenceMap {
		private final List<MdlxSequence> to;
		/** The Classic sequence for each sequence of the model. */
		private final MdlxSequence[] source;
		private final int globalSequenceBase;
		private final boolean anyMatched;

		SequenceMap(final List<MdlxSequence> from, final List<MdlxSequence> to, final int globalSequenceBase) {
			this.to = to;
			this.globalSequenceBase = globalSequenceBase;
			final Map<String, MdlxSequence> byName = new HashMap<>();
			final Map<String, MdlxSequence> byBaseName = new HashMap<>();
			final Map<String, MdlxSequence> byFirstWord = new HashMap<>();
			for (final MdlxSequence sequence : from) {
				final String name = sequence.name.toLowerCase(Locale.ROOT);
				byName.putIfAbsent(name, sequence);
				byBaseName.putIfAbsent(baseName(name), sequence);
				byFirstWord.putIfAbsent(firstWord(name), sequence);
			}
			this.source = new MdlxSequence[to.size()];
			boolean matched = false;
			for (int i = 0; i < this.source.length; i++) {
				final String name = to.get(i).name.toLowerCase(Locale.ROOT);
				MdlxSequence match = byName.get(name);
				if (match == null) {
					match = byBaseName.get(baseName(name));
				}
				if (match == null) {
					match = byFirstWord.get(firstWord(name));
				}
				this.source[i] = match;
				matched |= match != null;
			}
			this.anyMatched = matched;
		}

		/** The name without its " - n" suffix and trailing number. */
		private static String baseName(final String name) {
			final int dash = name.indexOf(" - ");
			String base = (dash == -1 ? name : name.substring(0, dash)).trim();
			int end = base.length();
			while ((end > 0) && (Character.isDigit(base.charAt(end - 1)) || (base.charAt(end - 1) == ' '))) {
				end--;
			}
			return base.substring(0, end);
		}

		private static String firstWord(final String name) {
			final String base = baseName(name);
			final int space = base.indexOf(' ');
			return space == -1 ? base : base.substring(0, space);
		}

		void remap(final MdlxAnimatedObject object) {
			for (final Iterator<MdlxTimeline<?>> iterator = object.timelines.iterator(); iterator.hasNext();) {
				final MdlxTimeline<?> timeline = iterator.next();
				if (timeline.globalSequenceId >= 0) {
					timeline.globalSequenceId += this.globalSequenceBase;
					continue;
				}
				if (!this.<Object>remapAny(timeline)) {
					iterator.remove();
				}
			}
		}

		@SuppressWarnings("unchecked")
		private <T> boolean remapAny(final MdlxTimeline<?> timeline) {
			return remap((MdlxTimeline<T>) timeline);
		}

		/** Whether any key frame is left. */
		private <T> boolean remap(final MdlxTimeline<T> timeline) {
			final int count = timeline.frames.length;
			final TreeMap<Long, Integer> kept = new TreeMap<>();
			for (int s = 0; s < this.source.length; s++) {
				final MdlxSequence classic = this.source[s];
				if (classic == null) {
					continue;
				}
				final long[] target = this.to.get(s).interval;
				for (int i = 0; i < count; i++) {
					final long frame = timeline.frames[i];
					if ((frame >= classic.interval[0]) && (frame <= classic.interval[1])) {
						final long offset = frame - classic.interval[0];
						if (offset <= (target[1] - target[0])) {
							kept.put(target[0] + offset, i);
						}
					}
				}
			}
			if (kept.isEmpty()) {
				return false;
			}
			final int keptCount = kept.size();
			final long[] frames = new long[keptCount];
			final int[] indices = new int[keptCount];
			int next = 0;
			for (final Map.Entry<Long, Integer> entry : kept.entrySet()) {
				frames[next] = entry.getKey();
				indices[next++] = entry.getValue();
			}
			timeline.frames = frames;
			timeline.values = pick(timeline.values, indices, keptCount);
			timeline.inTans = pick(timeline.inTans, indices, keptCount);
			timeline.outTans = pick(timeline.outTans, indices, keptCount);
			return true;
		}

		@SuppressWarnings("unchecked")
		private static <T> T[] pick(final T[] source, final int[] kept, final int keptCount) {
			if (source == null) {
				return null;
			}
			final T[] result = (T[]) Array.newInstance(source.getClass().getComponentType(), keptCount);
			for (int i = 0; i < keptCount; i++) {
				result[i] = source[kept[i]];
			}
			return result;
		}

		/**
		 * Puts the effect out in the sequences that it has no counterpart in, and in those where the popcorn emitters
		 * on the same node of the model are not shown (their visibility track, or none); without it the emitter runs in
		 * every sequence the Classic keys do not reach.
		 */
		void gate(final MdlxModel model, final MdlxGenericObject emitter, final War3ID visibility,
				final List<MdlxParticleEmitterPopcorn> popcorn) {
			if (!this.anyMatched) {
				return;
			}
			final List<MdlxParticleEmitterPopcorn> paired = new ArrayList<>();
			for (final MdlxParticleEmitterPopcorn candidate : popcorn) {
				if (candidate.parentId == emitter.parentId) {
					paired.add(candidate);
				}
			}
			final List<MdlxSequence> off = new ArrayList<>();
			for (int s = 0; s < this.to.size(); s++) {
				boolean active = this.source[s] != null;
				if (active && !paired.isEmpty()) {
					active = false;
					for (final MdlxParticleEmitterPopcorn candidate : paired) {
						active |= shownIn(candidate, this.to.get(s));
					}
				}
				if (!active) {
					off.add(this.to.get(s));
				}
			}
			putOut(emitter, visibility, off);
		}

		/** Whether the popcorn emitter is shown in the sequence; keys outside of it say nothing. */
		private static boolean shownIn(final MdlxParticleEmitterPopcorn popcorn, final MdlxSequence sequence) {
			final MdlxTimeline<?> visibility = findTimeline(popcorn, AnimationMap.KPPV.getWar3id());
			if ((visibility == null) || (visibility.globalSequenceId >= 0) || !numeric(visibility)) {
				return true;
			}
			boolean any = false;
			for (int i = 0; i < visibility.frames.length; i++) {
				if ((visibility.frames[i] >= sequence.interval[0]) && (visibility.frames[i] <= sequence.interval[1])) {
					any = true;
					if (((float[]) visibility.values[i])[0] > 0) {
						return true;
					}
				}
			}
			return !any;
		}

		/** Parsed timelines keep their values in an Object array, so the type of the array says nothing. */
		private static boolean numeric(final MdlxTimeline<?> timeline) {
			for (final Object value : timeline.values) {
				if (!(value instanceof float[])) {
					return false;
				}
			}
			return true;
		}

		private static void putOut(final MdlxGenericObject emitter, final War3ID name, final List<MdlxSequence> off) {
			if (off.isEmpty()) {
				return;
			}
			final MdlxTimeline<?> existing = findTimeline(emitter, name);
			if ((existing != null) && ((existing.globalSequenceId >= 0) || !numeric(existing))) {
				return;
			}
			final TreeMap<Long, float[]> keys = new TreeMap<>();
			if (existing != null) {
				for (int i = 0; i < existing.frames.length; i++) {
					keys.put(existing.frames[i], (float[]) existing.values[i]);
				}
			}
			for (final MdlxSequence sequence : off) {
				keys.subMap(sequence.interval[0], true, sequence.interval[1], true).clear();
				// One key holds its value for the whole sequence.
				keys.put(sequence.interval[0], new float[] { 0 });
			}
			final MdlxFloatTimeline timeline;
			if (existing instanceof MdlxFloatTimeline) {
				timeline = (MdlxFloatTimeline) existing;
			}
			else {
				timeline = new MdlxFloatTimeline();
				timeline.name = name;
				timeline.interpolationType = InterpolationType.DONT_INTERP;
				emitter.timelines.add(timeline);
			}
			if (timeline.interpolationType.tangential()) {
				timeline.interpolationType = InterpolationType.LINEAR;
			}
			timeline.inTans = null;
			timeline.outTans = null;
			timeline.frames = new long[keys.size()];
			timeline.values = new float[keys.size()][];
			int next = 0;
			for (final Map.Entry<Long, float[]> entry : keys.entrySet()) {
				timeline.frames[next] = entry.getKey();
				timeline.values[next++] = entry.getValue();
			}
		}
	}
}
