package com.etheller.warsmash.viewer5.gl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.badlogic.gdx.graphics.GL20;

public class ClientBuffer {
	private final GL20 gl;
	private final int buffer;
	private int size;
	private ByteBuffer arrayBuffer;
	public ByteBuffer byteView;
	public FloatBuffer floatView;
	// Emitters fill this and hand it over in one bulk copy, because a direct buffer takes a float at a time slowly.
	public float[] staging;

	public ClientBuffer(final GL20 gl) {
		this(gl, 4);
	}

	public ClientBuffer(final GL20 gl, final int size) {
		this.gl = gl;
		this.buffer = gl.glGenBuffer();
		this.arrayBuffer = null;

		this.reserve(size);
	}

	public void reserve(final int size) {
		if (this.size < size) {

			// Ensure the size is on a 4 byte boundary.
			this.size = (int) Math.ceil(size / 4.) * 4;

			this.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, this.buffer);

			this.arrayBuffer = ByteBuffer.allocateDirect(this.size).order(ByteOrder.nativeOrder());
			this.gl.glBufferData(GL20.GL_ARRAY_BUFFER, this.size, this.arrayBuffer, GL20.GL_DYNAMIC_DRAW);
			this.byteView = this.arrayBuffer;
			this.floatView = this.arrayBuffer.asFloatBuffer();
			this.staging = new float[this.size >> 2];

		}
	}

	public void commitStaging(final int floats) {
		commitStaging(0, floats);
	}

	/** Copies staging floats [from, from + floats) to the same place in the buffer. */
	public void commitStaging(final int from, final int floats) {
		this.floatView.clear();
		this.floatView.position(from);
		this.floatView.put(this.staging, from, floats);
	}

	public void bind() {
		this.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, this.buffer);
	}

	public void dispose() {
		this.gl.glDeleteBuffer(this.buffer);
	}

	/**
	 * Uploads the first size bytes by giving the buffer fresh storage with the data in it, instead of overwriting
	 * storage that queued draws may still be reading: some mobile GPUs wait for those draws or copy the buffer on
	 * every partial update.
	 */
	public void bindAndReplace(final int size) {
		final GL20 gl = this.gl;
		gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, this.buffer);
		gl.glBufferData(GL20.GL_ARRAY_BUFFER, size, this.byteView, GL20.GL_STREAM_DRAW);
	}

	public void bindAndUpdate() {
		bindAndUpdate(this.size);
	}

	public void bindAndUpdate(final int size) {
		final GL20 gl = this.gl;

		gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, this.buffer);
		gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, 0, size, this.byteView);
	}
}
