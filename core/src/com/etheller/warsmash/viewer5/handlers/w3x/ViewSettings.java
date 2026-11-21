package com.etheller.warsmash.viewer5.handlers.w3x;

/**
 * Presentation switches scripts set (EnableOcclusion, EnableWorldFogBoundary, SetWaterDeforms). They are local to the
 * picture and never read by the simulation.
 */
public final class ViewSettings {
	public boolean occlusion = true;
	public boolean worldFogBoundary = true;
	public boolean waterDeforms = true;
}
