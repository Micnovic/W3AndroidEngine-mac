package com.etheller.warsmash.viewer5.handlers.mdx;

import java.util.List;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.viewer5.AudioBufferSource;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.AudioPanner;
import com.etheller.warsmash.viewer5.EmittedObject;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;

public class EventObjectSnd extends EmittedObject<MdxComplexInstance, EventObjectSndEmitter> {
	public EventObjectSnd(final EventObjectSndEmitter emitter) {
		super(emitter);
	}

	@Override
	protected void bind(final int flags) {
		final EventObjectSndEmitter emitter = this.emitter;
		final MdxComplexInstance instance = emitter.instance;
		final ModelViewer viewer = instance.model.viewer;
		final Scene scene = instance.scene;

		// Is audio enabled both viewer-wide and in this scene?
		if (viewer.audioEnabled && scene.audioEnabled) {
			final EventObjectEmitterObject emitterObject = emitter.emitterObject;
			final MdxNode node = instance.nodes[emitterObject.index];
			final AudioContext audioContext = scene.audioContext;
			final List<EventObjectSound> decodedBuffers = emitterObject.decodedBuffers;
			if (decodedBuffers.isEmpty()) {
				return;
			}
			final Vector3 location = node.worldLocation;
			if ((audioContext.visibility != null) && !audioContext.visibility.isHeardAt(location.x, location.y)) {
				return;
			}
			final float x = location.x;
			final float y = location.y;
			final float z = location.z;
			decodedBuffers.get((int) (Math.random() * decodedBuffers.size()))
					.whenReady(buffer -> start(audioContext, emitterObject, buffer, x, y, z));
		}
	}

	private static void start(final AudioContext audioContext, final EventObjectEmitterObject emitterObject,
			final Sound buffer, final float x, final float y, final float z) {
		if (buffer == null) {
			return;
		}
		final AudioPanner panner = audioContext.createPanner();
		final AudioBufferSource source = audioContext.createBufferSource();

		// Panner settings
		panner.setPosition(x, y, z);
		panner.setDistances(emitterObject.distanceCutoff, emitterObject.minDistance);
		panner.connect(audioContext.destination);

		// Source.
		source.buffer = buffer;
		source.connect(panner);

		// Make a sound.
		source.start(0, emitterObject.volume * CSoundMixer.audible().channelScale(emitterObject.channel),
				(emitterObject.pitch + ((float) Math.random() * emitterObject.pitchVariance * 2))
						- emitterObject.pitchVariance,
				false);
	}

	@Override
	public void update(final float dt) {

	}
}
