package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.function.Supplier;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.facefx.FaceFxActor;
import com.etheller.warsmash.parsers.facefx.FaceFxAnimSet;
import com.etheller.warsmash.parsers.facefx.FaceFxLibrary;
import com.etheller.warsmash.viewer5.handlers.mdx.FaceFxPlayback;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;

/**
 * Matches the dialogue sound a script starts with the portrait it shows for the line and plays the line's FaceFX
 * animation on that portrait's face. A script starts the sound and sets the scene in either order, within the same
 * moment, so whichever comes second finds the other waiting. Portraits without FaceFX data keep their sequence.
 */
final class PortraitLipSync {
	/** How long a sound or a scene waits for the other half of the pair, in nanoseconds. */
	private static final long PAIR_WINDOW = 1_000_000_000L;

	private final DataSource data;
	private FaceFxLibrary library;
	private String soundPath;
	private long soundAt;
	private Supplier<MdxComplexInstance> portrait;
	private long portraitAt;

	PortraitLipSync(final DataSource data) {
		this.data = data;
	}

	/** A script started the sound file of a line. */
	void soundStarted(final String path) {
		if (path == null) {
			return;
		}
		final long now = System.nanoTime();
		this.soundPath = path;
		this.soundAt = now;
		if ((this.portrait != null) && ((now - this.portraitAt) < PAIR_WINDOW) && attach(this.portrait.get(), path)) {
			this.portrait = null;
			this.soundPath = null;
		}
	}

	/**
	 * A scene with a talking portrait was set up.
	 *
	 * @param portrait gives the portrait's instance, which the scene has just made
	 */
	void sceneStarted(final Supplier<MdxComplexInstance> portrait) {
		final long now = System.nanoTime();
		if ((this.soundPath != null) && ((now - this.soundAt) < PAIR_WINDOW) && attach(portrait.get(), this.soundPath)) {
			this.soundPath = null;
			this.portrait = null;
		}
		else {
			this.portrait = portrait;
			this.portraitAt = now;
		}
	}

	/** @return whether the line's face now plays on the instance */
	private boolean attach(final MdxComplexInstance instance, final String path) {
		if ((instance == null) || !(instance.model instanceof MdxModel)) {
			return false;
		}
		final MdxModel model = (MdxModel) instance.model;
		if (this.library == null) {
			this.library = new FaceFxLibrary(this.data);
		}
		final FaceFxActor actor = this.library.actorForModel(model.fetchUrl);
		if (actor == null) {
			return false;
		}
		final FaceFxAnimSet.Anim anim = this.library.animFor(model.fetchUrl, path);
		final FaceFxPlayback playback = FaceFxPlayback.create(actor, anim, model);
		if (playback == null) {
			return false;
		}
		instance.setFacePlayback(playback);
		return true;
	}
}
