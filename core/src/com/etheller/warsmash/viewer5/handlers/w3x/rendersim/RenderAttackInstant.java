package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import java.util.List;

import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.Sequence;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.IndexedSequence;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;

public class RenderAttackInstant implements RenderEffect {
	/** How long an effect waits for its model before it is dropped: shown later, it would no longer fit the attack. */
	private static final float MAX_WAIT_SECONDS = 1.0f;

	private final MdxComplexInstance modelInstance;
	private boolean waiting;
	private float waited;

	/**
	 * The instance may be one made before its model is built ({@link MdxComplexInstance#isWaiting()}): the effect then
	 * starts when the model is built.
	 */
	public RenderAttackInstant(final MdxComplexInstance modelInstance, final War3MapViewer war3MapViewer,
			final float yaw) {
		this.modelInstance = modelInstance;
		modelInstance.hiddenInFog = true;
		this.modelInstance.localRotation.setFromAxisRad(0, 0, 1, yaw);
		if (modelInstance.isWaiting()) {
			this.waiting = true;
			modelInstance.modelLoadedObserver = this::start;
		}
		else {
			start();
		}
	}

	private void start() {
		this.waiting = false;
		SequenceUtils.randomBirthSequence(this.modelInstance);
		final MdxModel model = (MdxModel) this.modelInstance.model;
		final List<Sequence> sequences = model.getSequences();
		final IndexedSequence sequence = SequenceUtils.selectSequence(PrimaryTag.DEATH, SequenceUtils.EMPTY, sequences,
				true);
		if ((sequence != null) && (sequence.index != -1)) {
			this.modelInstance.setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP);
			this.modelInstance.setSequence(sequence.index);
		}
	}

	@Override
	public boolean updateAnimations(final War3MapViewer war3MapViewer, final float deltaTime) {
		if (this.waiting) {
			this.waited += deltaTime;
			if ((this.waited < MAX_WAIT_SECONDS) && (this.modelInstance.model.ok || this.modelInstance.model.isPending())) {
				return false;
			}
			this.modelInstance.modelLoadedObserver = null;
			war3MapViewer.worldScene.removeInstance(this.modelInstance);
			return true;
		}
		final boolean everythingDone = this.modelInstance.sequenceEnded;
		if (everythingDone) {
			war3MapViewer.worldScene.removeInstance(this.modelInstance);
		}
		return everythingDone;
	}
}
