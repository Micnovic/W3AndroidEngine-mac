package com.etheller.warsmash.viewer5.handlers.w3x;

import com.etheller.warsmash.viewer5.gl.DataTexture;

public interface W3xSceneLightManager {
	public DataTexture getUnitLightsTexture();

	public int getUnitLightCount();

	public DataTexture getTerrainLightsTexture();

	public int getTerrainLightCount();

	/** The share of the environment map light the HD materials get in this scene. */
	public default float getHdIblScale() {
		return 1f;
	}

	/** The share of the specular highlights (direct and environment map) the HD materials get in this scene. */
	public default float getHdSpecularScale() {
		return 1f;
	}
}
