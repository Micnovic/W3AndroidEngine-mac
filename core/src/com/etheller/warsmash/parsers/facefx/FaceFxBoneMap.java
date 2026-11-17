package com.etheller.warsmash.parsers.facefx;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which bone of a model each face bone of a {@link FaceFxActor} drives, by name: the actor calls a bone
 * {@code footman:jaw_bind_jnt} and the portrait model {@code jaw_bind_jnt} (or the same name with the prefix), in any
 * letter case.
 */
public final class FaceFxBoneMap {
	/** A model that shares fewer bone names with the actor is not taken for the character's face. */
	public static final int MIN_MATCHED = 4;

	private FaceFxBoneMap() {
	}

	/**
	 * @param nodeNames the names of the model's nodes in the order the caller drives them
	 * @return for every node the index in {@link FaceFxActor#getBones()} of the bone that drives it, or -1; null when
	 *         fewer than {@link #MIN_MATCHED} nodes match
	 */
	public static int[] map(final FaceFxActor actor, final List<String> nodeNames) {
		return map(actor, nodeNames, MIN_MATCHED);
	}

	static int[] map(final FaceFxActor actor, final List<String> nodeNames, final int minMatched) {
		final Map<String, Integer> byName = new HashMap<>();
		final List<FaceFxActor.Bone> bones = actor.getBones();
		for (int i = 0; i < bones.size(); i++) {
			byName.putIfAbsent(plainName(bones.get(i).name), i);
		}
		final int[] result = new int[nodeNames.size()];
		int matched = 0;
		for (int i = 0; i < result.length; i++) {
			final String name = nodeNames.get(i);
			final Integer bone = (name == null) ? null : byName.get(plainName(name));
			result[i] = (bone == null) ? -1 : bone;
			if (bone != null) {
				matched++;
			}
		}
		return (matched >= minMatched) ? result : null;
	}

	/** The name without the character prefix before a colon, lower case. */
	static String plainName(final String name) {
		return name.substring(name.lastIndexOf(':') + 1).toLowerCase(Locale.ROOT);
	}
}
