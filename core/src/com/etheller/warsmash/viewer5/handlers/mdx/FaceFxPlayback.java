package com.etheller.warsmash.viewer5.handlers.mdx;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.Quaternion;
import com.etheller.warsmash.parsers.facefx.FaceFxActor;
import com.etheller.warsmash.parsers.facefx.FaceFxAnimSet;
import com.etheller.warsmash.parsers.facefx.FaceFxBoneMap;
import com.etheller.warsmash.viewer5.SkeletalNode;

/**
 * One spoken line played on the face bones of a portrait instance: the bones named like the character's FaceFX face
 * bones are taken from the line's animation instead of the portrait sequence while the line lasts, blending in at its
 * start and back to the sequence after its end.
 */
public final class FaceFxPlayback {
	private static final float FADE_IN = 0.12f;
	/** The curves hold their last values past the last key, so the face rests a moment before it is let go. */
	private static final float HOLD = 0.1f;
	private static final float FADE_OUT = 0.2f;
	private static final Quaternion targetHeap = new Quaternion();

	private final FaceFxActor actor;
	private final FaceFxAnimSet.Anim anim;
	/** For each of the model's sorted nodes the index of the actor's bone that drives it, or -1. */
	private final int[] boneOfNode;
	private final float[] weights;
	private final float[] work = new float[10];
	private final float[] translation = new float[3];
	private final float[] rotation = new float[4];
	private final float[] scale = new float[3];
	private float time;
	private float blend;

	FaceFxPlayback(final FaceFxActor actor, final FaceFxAnimSet.Anim anim, final int[] boneOfNode) {
		this.actor = actor;
		this.anim = anim;
		this.boneOfNode = boneOfNode;
		this.weights = new float[actor.getTargets().size()];
		update(0);
	}

	/**
	 * @return the playback of a line on the model, null when the model has too few of the character's face bones (a
	 *         Classic portrait) and so keeps its own animation
	 */
	public static FaceFxPlayback create(final FaceFxActor actor, final FaceFxAnimSet.Anim anim, final MdxModel model) {
		if ((actor == null) || (anim == null) || (model == null) || !model.ok) {
			return null;
		}
		final List<String> names = new ArrayList<>(model.sortedGenericObjects.size());
		for (final GenericObject object : model.sortedGenericObjects) {
			names.add(object.name);
		}
		final int[] boneOfNode = FaceFxBoneMap.map(actor, names);
		return (boneOfNode == null) ? null : new FaceFxPlayback(actor, anim, boneOfNode);
	}

	/** @return whether the line and its fade out are over */
	boolean advance(final float dt) {
		this.time += dt;
		update(this.time);
		return this.time > (this.anim.duration + HOLD + FADE_OUT);
	}

	private void update(final float t) {
		this.actor.sampleTargets(this.anim, t, this.weights);
		final float in = Math.min(1f, t / FADE_IN);
		final float out = Math.min(1f, Math.max(0f, 1f - ((t - this.anim.duration - HOLD) / FADE_OUT)));
		this.blend = Math.min(in, out);
	}

	boolean drives(final int nodeIndex) {
		return (nodeIndex < this.boneOfNode.length) && (this.boneOfNode[nodeIndex] >= 0);
	}

	/** Moves the node's local pose, which holds the sequence's, toward the face bone's pose of the line. */
	void apply(final int nodeIndex, final SkeletalNode node) {
		this.actor.getBones().get(this.boneOfNode[nodeIndex]).solveModelDelta(this.weights, this.work,
				this.translation, this.rotation, this.scale);
		final float blend = this.blend;
		node.localLocation.x += (this.translation[0] - node.localLocation.x) * blend;
		node.localLocation.y += (this.translation[1] - node.localLocation.y) * blend;
		node.localLocation.z += (this.translation[2] - node.localLocation.z) * blend;
		node.localScale.x += ((this.scale[0] - node.localScale.x) * blend);
		node.localScale.y += ((this.scale[1] - node.localScale.y) * blend);
		node.localScale.z += ((this.scale[2] - node.localScale.z) * blend);
		targetHeap.set(this.rotation[0], this.rotation[1], this.rotation[2], this.rotation[3]);
		node.localRotation.slerp(targetHeap, blend);
	}
}
