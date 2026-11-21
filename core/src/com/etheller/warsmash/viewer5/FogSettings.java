package com.etheller.warsmash.viewer5;

import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.etheller.warsmash.units.Element;

public class FogSettings {
	/** The map density (0 to 1, the editor slider) is not per world unit; the exponential styles read it per 1000. */
	private static final float EXP_DENSITY_SCALE = 0.001f;

	public FogStyle style = FogStyle.NONE;
	public Color color = Color.BLACK;
	public float density;
	public float start;
	public float end;
	/** Largest share of the fog colour that fog may reach (w3i v39+); 1 is the classic unlimited fog. */
	public float maxOpacity = 1f;
	/**
	 * Height fog range of a w3i v39+ map: full fog at and below heightMin, none at and above heightMax (an assumed
	 * Reforged-style falloff, not measured). Off unless heightMax > heightMin, as for every older map.
	 */
	public float heightMin;
	public float heightMax;
	/** w3i v39+: draw the fog over the sky too (the sky scene then uses the world fog). */
	public boolean drawOverSky;

	public boolean hasHeightFog() {
		return this.heightMax > this.heightMin;
	}

	public void copyFrom(final FogSettings other) {
		this.color = other.color;
		this.style = other.style;
		this.density = other.density;
		this.start = other.start;
		this.end = other.end;
		this.maxOpacity = other.maxOpacity;
		this.heightMin = other.heightMin;
		this.heightMax = other.heightMax;
		this.drawOverSky = other.drawOverSky;
	}

	/** The fog alpha is not a colour for the shaders; they read it as the opacity cap. */
	public void uploadUniforms(final ShaderProgram shader) {
		shader.setUniformf("u_fogColor", this.color.r, this.color.g, this.color.b, this.maxOpacity);
		shader.setUniformf("u_fogParams", this.style.ordinal(), this.start, this.end, this.density * EXP_DENSITY_SCALE);
		shader.setUniformf("u_fogHeight", hasHeightFog() ? 1f : 0f, this.heightMin, this.heightMax, 0f);
	}

	/** The same with the locations of u_fogColor, u_fogParams and u_fogHeight already found. */
	public void uploadUniforms(final ShaderProgram shader, final int colorLocation, final int paramsLocation,
			final int heightLocation) {
		shader.setUniformf(colorLocation, this.color.r, this.color.g, this.color.b, this.maxOpacity);
		shader.setUniformf(paramsLocation, this.style.ordinal(), this.start, this.end,
				this.density * EXP_DENSITY_SCALE);
		shader.setUniformf(heightLocation, hasHeightFog() ? 1f : 0f, this.heightMin, this.heightMax, 0f);
	}

	public void setStyleByIndex(final int styleValue) {
		this.style = ((styleValue >= 0) && (styleValue < FogStyle.values().length)) ? FogStyle.values()[styleValue]
				: FogStyle.NONE;
	}

	@Override
	public String toString() {
		return "FogSettings [style=" + this.style + ", color=" + this.color + ", density=" + this.density + ", start="
				+ this.start + ", end=" + this.end + "]";
	}

	public static FogSettings parse(final Element zFogElement, final int gameVersionIndex) {
		final FogSettings newFogSettings = new FogSettings();
		// Some entries (DefaultZFog) list one value for every game version; past its end every field reads 0, which
		// is linear black fog ending at the camera and blacks out the whole world after ResetTerrainFog.
		final List<String> styles = zFogElement.getFieldAsList("Style");
		final int index = styles == null ? gameVersionIndex
				: Math.max(0, Math.min(gameVersionIndex, styles.size() - 1));
		final int styleValue = zFogElement.getFieldAsInteger("Style", index) + 1;
		newFogSettings.setStyleByIndex(styleValue);
		newFogSettings.start = zFogElement.getFieldAsFloat("Start", index);
		newFogSettings.end = zFogElement.getFieldAsFloat("End", index);
		newFogSettings.density = zFogElement.getFieldAsFloat("Density", index);
		final float a = zFogElement.getFieldAsFloat("Color", index * 4) / 255f;
		final float r = zFogElement.getFieldAsFloat("Color", 1 + (index * 4)) / 255f;
		final float g = zFogElement.getFieldAsFloat("Color", 2 + (index * 4)) / 255f;
		final float b = zFogElement.getFieldAsFloat("Color", 3 + (index * 4)) / 255f;
		newFogSettings.color = new Color(r, g, b, a);
		return newFogSettings;
	}
}
