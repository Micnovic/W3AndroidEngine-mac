package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;

/**
 * The simulation's stand-in for a lightning made on the render thread. The state the simulation can read back is kept
 * here and changes only through the simulation's own calls: a lightning counts as removed once the simulation removed
 * it, not when its duration runs out on the render side, so what a script sees does not depend on frame timing.
 */
public final class ProxyLightning implements SimulationRenderComponentLightning {
	private final BufferingRenderController controller;
	private final int id;
	private boolean removed;
	private float[] color;

	ProxyLightning(final BufferingRenderController controller, final int id) {
		this.controller = controller;
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	@Override
	public void remove() {
		this.removed = true;
		this.controller.begin(RenderCallCodes.LIGHTNING_REMOVE).a = this.id;
		this.controller.emitPending();
	}

	@Override
	public void setColor(final float r, final float g, final float b, final float a) {
		this.color = new float[] { r, g, b, a };
		final RenderEvent event = this.controller.begin(RenderCallCodes.LIGHTNING_SET_COLOR);
		event.a = this.id;
		event.f0 = r;
		event.f1 = g;
		event.f2 = b;
		event.f3 = a;
		this.controller.emitPending();
	}

	@Override
	public float[] getColor() {
		return this.color;
	}

	@Override
	public boolean isRemoved() {
		return this.removed;
	}
}
