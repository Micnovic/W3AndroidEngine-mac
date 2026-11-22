package com.etheller.warsmash.viewer5.gl;

import java.nio.Buffer;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * libGDX keys its location maps by the names the driver reports, so a lookup with a string literal never hits the
 * identity check and compares the name character by character; with a uniform set per batch this was about a tenth
 * of the render time on phones. These maps are keyed by the caller's strings instead.
 * <p>
 * A uniform keeps its value in the program, so setting the same value again is a wasted driver call; the setters
 * below skip it. Only writes through this class are tracked, so a uniform set here must not also be written with raw
 * GL calls. The values are dropped when libGDX relinks the program after a context loss.
 */
public class CachedShaderProgram extends ShaderProgram {
	private final Map<String, Integer> uniformLocations = new HashMap<>();
	private final Map<String, Integer> attributeLocations = new HashMap<>();
	private final UniformCache uniformCache = new UniformCache();
	private int cachedHandle;
	private int links;
	private Object holder;

	public CachedShaderProgram(final String vertexShader, final String fragmentShader) {
		super(vertexShader, fragmentShader);
	}

	@Override
	public int fetchUniformLocation(final String name, final boolean pedantic) {
		final Integer cached = this.uniformLocations.get(name);
		if (cached != null) {
			return cached;
		}
		final int location = super.fetchUniformLocation(name, pedantic);
		this.uniformLocations.put(name, location);
		return location;
	}

	/** Counts the times the program was linked anew, after which locations found before mean nothing. */
	public int getLinks() {
		return this.links;
	}

	/** What a user of the program keeps with it (its locations); dropped when the program is linked anew. */
	public Object getHolder() {
		return this.holder;
	}

	public void setHolder(final Object holder) {
		this.holder = holder;
	}

	private int cachedAttributeLocation(final String name) {
		final Integer cached = this.attributeLocations.get(name);
		if (cached != null) {
			return cached;
		}
		final int location = getAttributeLocation(name);
		this.attributeLocations.put(name, location);
		return location;
	}

	@Override
	public void setVertexAttribute(final String name, final int size, final int type, final boolean normalize,
			final int stride, final Buffer buffer) {
		final int location = cachedAttributeLocation(name);
		if (location != -1) {
			setVertexAttribute(location, size, type, normalize, stride, buffer);
		}
	}

	@Override
	public void setVertexAttribute(final String name, final int size, final int type, final boolean normalize,
			final int stride, final int offset) {
		final int location = cachedAttributeLocation(name);
		if (location != -1) {
			setVertexAttribute(location, size, type, normalize, stride, offset);
		}
	}

	@Override
	public void enableVertexAttribute(final String name) {
		final int location = cachedAttributeLocation(name);
		if (location != -1) {
			enableVertexAttribute(location);
		}
	}

	@Override
	public void disableVertexAttribute(final String name) {
		final int location = cachedAttributeLocation(name);
		if (location != -1) {
			disableVertexAttribute(location);
		}
	}

	@Override
	public void begin() {
		super.begin();
		final int handle = getHandle();
		if (handle != this.cachedHandle) {
			this.cachedHandle = handle;
			this.links++;
			this.holder = null;
			this.uniformCache.clear();
			this.uniformLocations.clear();
			this.attributeLocations.clear();
		}
	}

	private int location(final String name) {
		return fetchUniformLocation(name, pedantic);
	}

	@Override
	public void setUniformi(final String name, final int value) {
		setUniformi(location(name), value);
	}

	@Override
	public void setUniformi(final int location, final int value) {
		if (this.uniformCache.changed(location, value)) {
			Gdx.gl20.glUniform1i(location, value);
		}
	}

	@Override
	public void setUniformf(final String name, final float value) {
		setUniformf(location(name), value);
	}

	@Override
	public void setUniformf(final int location, final float value) {
		if (this.uniformCache.changed(location, value)) {
			Gdx.gl20.glUniform1f(location, value);
		}
	}

	@Override
	public void setUniformf(final String name, final float x, final float y) {
		setUniformf(location(name), x, y);
	}

	@Override
	public void setUniformf(final int location, final float x, final float y) {
		if (this.uniformCache.changed(location, x, y)) {
			Gdx.gl20.glUniform2f(location, x, y);
		}
	}

	@Override
	public void setUniformf(final String name, final float x, final float y, final float z) {
		setUniformf(location(name), x, y, z);
	}

	@Override
	public void setUniformf(final int location, final float x, final float y, final float z) {
		if (this.uniformCache.changed(location, x, y, z)) {
			Gdx.gl20.glUniform3f(location, x, y, z);
		}
	}

	@Override
	public void setUniformf(final String name, final float x, final float y, final float z, final float w) {
		setUniformf(location(name), x, y, z, w);
	}

	@Override
	public void setUniformf(final int location, final float x, final float y, final float z, final float w) {
		if (this.uniformCache.changed(location, x, y, z, w)) {
			Gdx.gl20.glUniform4f(location, x, y, z, w);
		}
	}

	@Override
	public void setUniformf(final String name, final Vector2 values) {
		setUniformf(location(name), values.x, values.y);
	}

	@Override
	public void setUniformf(final int location, final Vector2 values) {
		setUniformf(location, values.x, values.y);
	}

	@Override
	public void setUniformf(final String name, final Vector3 values) {
		setUniformf(location(name), values.x, values.y, values.z);
	}

	@Override
	public void setUniformf(final int location, final Vector3 values) {
		setUniformf(location, values.x, values.y, values.z);
	}

	@Override
	public void setUniformf(final String name, final Color values) {
		setUniformf(location(name), values.r, values.g, values.b, values.a);
	}

	@Override
	public void setUniformf(final int location, final Color values) {
		setUniformf(location, values.r, values.g, values.b, values.a);
	}

	@Override
	public void setUniform1fv(final String name, final float[] values, final int offset, final int length) {
		setUniform1fv(location(name), values, offset, length);
	}

	@Override
	public void setUniform1fv(final int location, final float[] values, final int offset, final int length) {
		if (this.uniformCache.changed(location, values, offset, length)) {
			Gdx.gl20.glUniform1fv(location, length, values, offset);
		}
	}

	@Override
	public void setUniform2fv(final String name, final float[] values, final int offset, final int length) {
		setUniform2fv(location(name), values, offset, length);
	}

	@Override
	public void setUniform2fv(final int location, final float[] values, final int offset, final int length) {
		if (this.uniformCache.changed(location, values, offset, length)) {
			Gdx.gl20.glUniform2fv(location, length / 2, values, offset);
		}
	}

	@Override
	public void setUniform3fv(final String name, final float[] values, final int offset, final int length) {
		setUniform3fv(location(name), values, offset, length);
	}

	@Override
	public void setUniform3fv(final int location, final float[] values, final int offset, final int length) {
		if (this.uniformCache.changed(location, values, offset, length)) {
			Gdx.gl20.glUniform3fv(location, length / 3, values, offset);
		}
	}

	@Override
	public void setUniform4fv(final String name, final float[] values, final int offset, final int length) {
		setUniform4fv(location(name), values, offset, length);
	}

	@Override
	public void setUniform4fv(final int location, final float[] values, final int offset, final int length) {
		if (this.uniformCache.changed(location, values, offset, length)) {
			Gdx.gl20.glUniform4fv(location, length / 4, values, offset);
		}
	}

	@Override
	public void setUniformMatrix(final String name, final Matrix4 matrix) {
		setUniformMatrix(location(name), matrix, false);
	}

	@Override
	public void setUniformMatrix(final String name, final Matrix4 matrix, final boolean transpose) {
		setUniformMatrix(location(name), matrix, transpose);
	}

	@Override
	public void setUniformMatrix(final int location, final Matrix4 matrix) {
		setUniformMatrix(location, matrix, false);
	}

	@Override
	public void setUniformMatrix(final int location, final Matrix4 matrix, final boolean transpose) {
		if (transpose) {
			this.uniformCache.forget(location);
			Gdx.gl20.glUniformMatrix4fv(location, 1, true, matrix.val, 0);
		}
		else if (this.uniformCache.changed(location, matrix.val, 0, 16)) {
			Gdx.gl20.glUniformMatrix4fv(location, 1, false, matrix.val, 0);
		}
	}

	@Override
	public void setUniformMatrix4fv(final String name, final float[] values, final int offset, final int length) {
		setUniformMatrix4fv(location(name), values, offset, length);
	}

	@Override
	public void setUniformMatrix4fv(final int location, final float[] values, final int offset, final int length) {
		if (this.uniformCache.changed(location, values, offset, length)) {
			Gdx.gl20.glUniformMatrix4fv(location, length / 16, false, values, offset);
		}
	}
}
