package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

/**
 * What the simulation holds in place of a render component (an effect, a sound, a splat) while the real one is made
 * later on the render thread. Every call becomes an event naming {@link #getId()}, which the
 * {@link RenderEventPlayer} maps to the real component. It keeps nothing the simulation could read back, so the game
 * cannot depend on the render side. Serves as a plain component and as a model component; a height on a component
 * that is no model is ignored by the player.
 */
public final class ProxyRenderComponent implements SimulationRenderComponentModel {
	private final BufferingRenderController controller;
	private final int id;

	ProxyRenderComponent(final BufferingRenderController controller, final int id) {
		this.controller = controller;
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	@Override
	public void remove() {
		this.controller.begin(RenderCallCodes.COMPONENT_REMOVE).a = this.id;
		this.controller.emitPending();
	}

	@Override
	public void setHeight(final float height) {
		final RenderEvent event = this.controller.begin(RenderCallCodes.COMPONENT_SET_HEIGHT);
		event.a = this.id;
		event.f0 = height;
		this.controller.emitPending();
	}
}
