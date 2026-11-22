package com.etheller.warsmash.viewer5.handlers.mdx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.viewer5.gl.ANGLEInstancedArrays;
import com.hiveworkshop.rms.parsers.mdlx.MdlxGeoset;

public class Geoset {
	public MdxModel model;
	public int index;
	/**
	 * The geoset's place in the model file, which geoset animations and bones refer to. The other levels of detail
	 * of a model are left out, so it differs from the index after the first of them.
	 */
	public final int fileIndex;
	public int positionOffset;
	public int normalOffset;
	public int uvOffset;
	public final int tangentOffset;
	public int skinOffset;
	public int faceOffset;
	public int vertices;
	public int elements;
	public GeosetAnimation geosetAnimation;
	public Variants variants;
	public boolean hasAlphaAnim;
	public boolean hasColorAnim;
	public boolean hasObjectAnim;
	private final int openGLSkinType;
	private final int skinStride;
	/** HD skin with 16 bit bone indices, for models with more than 255 bones. */
	public boolean wideSkin;
	private final int boneCountOffsetBytes;
	public final boolean unselectable;
	/**
	 * Only what picking and sorting read stays once the geometry is in the GPU buffers: the normals, tangents, skin
	 * and texture coordinates of HD models are several times larger and do not fit a phone's heap. The positions and
	 * faces of the models of a map are tens of megabytes, which only a ray test reads: they are kept in native
	 * memory, outside the Java heap. On Android a direct buffer is an array in the Java heap, so there they are
	 * allocated outside the runtime and freed with the model. A geoset that is never picked keeps none (null).
	 */
	public FloatBuffer pickVertices;
	public CharBuffer pickFaces;
	private PickGrid pickGrid;
	private ByteBuffer unsafePickVertices;
	private ByteBuffer unsafePickFaces;
	public final float centerZ;

	public Geoset(final MdxModel model, final int index, final int fileIndex, final int positionOffset,
			final int normalOffset, final int uvOffset, final int tangentOffset, final int skinOffset,
			final int faceOffset, final int vertices, final int elements, final int openGLSkinType,
			final int skinStride, final int boneCountOffsetBytes, final boolean unselectable,
			final MdlxGeoset mdlxGeoset, final PickData preparedPick) {
		this.model = model;
		this.index = index;
		this.fileIndex = fileIndex;
		this.positionOffset = positionOffset;
		this.normalOffset = normalOffset;
		this.uvOffset = uvOffset;
		this.tangentOffset = tangentOffset;
		this.skinOffset = skinOffset;
		this.faceOffset = faceOffset;
		this.vertices = vertices;
		this.elements = elements;
		this.openGLSkinType = openGLSkinType;
		this.skinStride = skinStride;
		this.boneCountOffsetBytes = boneCountOffsetBytes;
		this.unselectable = unselectable;
		if (!unselectable) {
			final PickData pick = (preparedPick != null) ? preparedPick : PickData.prepare(mdlxGeoset);
			this.unsafePickFaces = pick.unsafe ? pick.faceBytes : null;
			this.unsafePickVertices = pick.unsafe ? pick.vertexBytes : null;
			this.pickFaces = pick.faceView;
			this.pickVertices = pick.vertexView;
		}
		this.centerZ = (mdlxGeoset.extent.max[2] + mdlxGeoset.extent.min[2]) / 2;

		for (final GeosetAnimation geosetAnimation : model.getGeosetAnimations()) {
			if (geosetAnimation.geosetId == fileIndex) {
				this.geosetAnimation = geosetAnimation;
			}
		}

		final Variants variants = new Variants(model.getSequences().size());

		final GeosetAnimation geosetAnimation = this.geosetAnimation;
		boolean hasAlphaAnim = false;
		boolean hasColorAnim = false;

		if (geosetAnimation != null) {
			for (int i = 0, l = model.getSequences().size(); i < l; i++) {
				final boolean alpha = geosetAnimation.isAlphaVariant(i);
				final boolean color = geosetAnimation.isColorVariant(i);

				variants.alpha[i] = alpha;
				variants.color[i] = color;
				variants.object[i] = alpha || color;

				hasAlphaAnim = hasAlphaAnim || alpha;
				hasColorAnim = hasColorAnim || color;
			}
		}
		else {
			for (int i = 0, l = model.getSequences().size(); i < l; i++) {
				variants.alpha[i] = false;
				variants.color[i] = false;
				variants.object[i] = false;
			}
		}

		this.variants = variants;
		this.hasAlphaAnim = hasAlphaAnim;
		this.hasColorAnim = hasColorAnim;
		this.hasObjectAnim = hasAlphaAnim || hasColorAnim;
	}

	/**
	 * What a geoset's ray test reads, made from the parsed geoset alone, so that it can be made on a helper thread
	 * before the model is built.
	 */
	public static final class PickData {
		final ByteBuffer faceBytes;
		final ByteBuffer vertexBytes;
		final CharBuffer faceView;
		final FloatBuffer vertexView;
		final boolean unsafe;

		private PickData(final char[] faces, final float[] vertices, final boolean unsafe) {
			this.unsafe = unsafe;
			this.faceBytes = pickBuffer(faces.length * 2, unsafe);
			this.vertexBytes = pickBuffer(vertices.length * 4, unsafe);
			this.faceView = this.faceBytes.asCharBuffer().put(faces);
			this.vertexView = this.vertexBytes.asFloatBuffer().put(vertices);
		}

		/** Frees the native memory of a pick buffer set that no geoset took over. */
		public void dispose() {
			if (this.unsafe) {
				BufferUtils.disposeUnsafeByteBuffer(this.faceBytes);
				BufferUtils.disposeUnsafeByteBuffer(this.vertexBytes);
			}
		}

		public static PickData prepare(final MdlxGeoset mdlxGeoset) {
			final float[] allVertices = mdlxGeoset.getVertices();
			final short[] faces = mdlxGeoset.getFaces();
			final char[] pickFaces = new char[faces.length];
			for (int i = 0; i < faces.length; i++) {
				pickFaces[i] = (char) faces[i];
			}
			final float[] pickVertices = uniquePositions(allVertices, pickFaces);
			final boolean unsafe = (Gdx.app != null) && (Gdx.app.getType() == ApplicationType.Android);
			return new PickData(pickFaces, pickVertices, unsafe);
		}
	}

	private static ByteBuffer pickBuffer(final int bytes, final boolean unsafe) {
		final ByteBuffer buffer = unsafe ? BufferUtils.newUnsafeByteBuffer(bytes) : ByteBuffer.allocateDirect(bytes);
		return buffer.order(ByteOrder.nativeOrder());
	}

	/** The triangles sorted by ground position, made when the first vertical ray asks; null without pick data. */
	PickGrid pickGrid() {
		if ((this.pickGrid == null) && (this.pickVertices != null) && (this.pickFaces != null)) {
			this.pickGrid = new PickGrid(this.pickVertices, this.pickFaces, 3);
		}
		return this.pickGrid;
	}

	/** Frees what picking reads; the geoset is not picked any more. */
	void disposePickData() {
		this.pickGrid = null;
		this.pickVertices = null;
		this.pickFaces = null;
		if (this.unsafePickVertices != null) {
			BufferUtils.disposeUnsafeByteBuffer(this.unsafePickVertices);
			this.unsafePickVertices = null;
		}
		if (this.unsafePickFaces != null) {
			BufferUtils.disposeUnsafeByteBuffer(this.unsafePickFaces);
			this.unsafePickFaces = null;
		}
	}

	/**
	 * The vertices of a model repeat a position for each normal and texture seam, which picking does not need; the
	 * faces are renumbered to the positions in place. The triangles stay the same ones.
	 */
	static float[] uniquePositions(final float[] vertices, final char[] faces) {
		final int count = vertices.length / 3;
		if (((vertices.length % 3) != 0) || (count < 2)) {
			return vertices;
		}
		for (final char face : faces) {
			if (face >= count) {
				return vertices;
			}
		}
		int capacity = Integer.highestOneBit(count * 2 - 1) << 1;
		final int[] table = new int[capacity];
		Arrays.fill(table, -1);
		final int mask = capacity - 1;
		final int[] renumbered = new int[count];
		final float[] unique = new float[vertices.length];
		int uniqueCount = 0;
		for (int i = 0; i < count; i++) {
			final int x = Float.floatToRawIntBits(vertices[i * 3]);
			final int y = Float.floatToRawIntBits(vertices[(i * 3) + 1]);
			final int z = Float.floatToRawIntBits(vertices[(i * 3) + 2]);
			int slot = (((x * 31) + y) * 31 + z) * 0x9E3779B1 >>> 7 & mask;
			int found = -1;
			while (table[slot] != -1) {
				final int other = table[slot];
				if ((Float.floatToRawIntBits(unique[other * 3]) == x)
						&& (Float.floatToRawIntBits(unique[(other * 3) + 1]) == y)
						&& (Float.floatToRawIntBits(unique[(other * 3) + 2]) == z)) {
					found = other;
					break;
				}
				slot = (slot + 1) & mask;
			}
			if (found == -1) {
				found = uniqueCount++;
				unique[found * 3] = vertices[i * 3];
				unique[(found * 3) + 1] = vertices[(i * 3) + 1];
				unique[(found * 3) + 2] = vertices[(i * 3) + 2];
				table[slot] = found;
			}
			renumbered[i] = found;
		}
		if (uniqueCount == count) {
			return vertices;
		}
		for (int i = 0; i < faces.length; i++) {
			faces[i] = (char) renumbered[faces[i]];
		}
		return Arrays.copyOf(unique, uniqueCount * 3);
	}

	public int getAlpha(final float[] out, final int sequence, final int frame, final int counter) {
		if (this.geosetAnimation != null) {
			return this.geosetAnimation.getAlpha(out, sequence, frame, counter);
		}

		out[0] = 1;
		return -1;
	}

	public int getColor(final float[] out, final int sequence, final int frame, final int counter) {
		if (this.geosetAnimation != null) {
			return this.geosetAnimation.getColor(out, sequence, frame, counter);
		}

		Arrays.fill(out, 1);
		return -1;
	}

	public void bind(final ShaderProgram shader, final int coordId) {
		// TODO use indices instead of strings for attributes
		shader.setVertexAttribute("a_position", 3, GL20.GL_FLOAT, false, 0, this.positionOffset);
		shader.setVertexAttribute("a_normal", 3, GL20.GL_FLOAT, false, 0, this.normalOffset);
		shader.setVertexAttribute("a_uv", 2, GL20.GL_FLOAT, false, 0, this.uvOffset + (coordId * this.vertices * 8));
		shader.setVertexAttribute("a_bones", 4, this.openGLSkinType, false, this.skinStride, this.skinOffset);
		shader.setVertexAttribute("a_boneNumber", 1, this.openGLSkinType, false, this.skinStride,
				this.skinOffset + this.boneCountOffsetBytes);
	}

	void bind(final ShaderProgram shader, final MdxLocations at, final int coordId) {
		attribute(shader, at.position, 3, GL20.GL_FLOAT, false, 0, this.positionOffset);
		attribute(shader, at.normal, 3, GL20.GL_FLOAT, false, 0, this.normalOffset);
		attribute(shader, at.uv, 2, GL20.GL_FLOAT, false, 0, this.uvOffset + (coordId * this.vertices * 8));
		attribute(shader, at.bones, 4, this.openGLSkinType, false, this.skinStride, this.skinOffset);
		attribute(shader, at.boneNumber, 1, this.openGLSkinType, false, this.skinStride,
				this.skinOffset + this.boneCountOffsetBytes);
	}

	void bindExtended(final ShaderProgram shader, final MdxLocations at, final int coordId) {
		attribute(shader, at.position, 3, GL20.GL_FLOAT, false, 0, this.positionOffset);
		attribute(shader, at.normal, 3, GL20.GL_FLOAT, false, 0, this.normalOffset);
		attribute(shader, at.uv, 2, GL20.GL_FLOAT, false, 0, this.uvOffset + (coordId * this.vertices * 8));
		attribute(shader, at.bones, 4, this.openGLSkinType, false, this.skinStride, this.skinOffset);
		attribute(shader, at.extendedBones, 4, this.openGLSkinType, false, this.skinStride,
				this.skinOffset + (this.boneCountOffsetBytes / 2));
		attribute(shader, at.boneNumber, 1, this.openGLSkinType, false, this.skinStride,
				this.skinOffset + this.boneCountOffsetBytes);
	}

	void bindHd(final ShaderProgram shader, final MdxLocations at, final SkinningType skinningType, final int coordId) {
		attribute(shader, at.position, 3, GL20.GL_FLOAT, false, 0, this.positionOffset);
		attribute(shader, at.normal, 3, GL20.GL_FLOAT, false, 0, this.normalOffset);
		attribute(shader, at.uv, 2, GL20.GL_FLOAT, false, 0, this.uvOffset + (coordId * this.vertices * 8));
		attribute(shader, at.tangent, 4, GL20.GL_FLOAT, false, 0, this.tangentOffset);
		if (this.wideSkin) {
			attribute(shader, at.bones, 4, GL20.GL_UNSIGNED_SHORT, false, SetupGeosets.WIDE_SKIN_STRIDE,
					this.skinOffset);
			attribute(shader, at.weights, 4, GL20.GL_UNSIGNED_BYTE, true, SetupGeosets.WIDE_SKIN_STRIDE,
					this.skinOffset + 8);
		}
		else {
			attribute(shader, at.bones, 4, GL20.GL_UNSIGNED_BYTE, false, 8, this.skinOffset);
			attribute(shader, at.weights, 4, GL20.GL_UNSIGNED_BYTE, true, 8, this.skinOffset + 4);
		}
	}

	private static void attribute(final ShaderProgram shader, final int location, final int size, final int type,
			final boolean normalize, final int stride, final int offset) {
		if (location != -1) {
			shader.setVertexAttribute(location, size, type, normalize, stride, offset);
		}
	}

	public void bindExtended(final ShaderProgram shader, final int coordId) {
		// TODO use indices instead of strings for attributes
		shader.setVertexAttribute("a_position", 3, GL20.GL_FLOAT, false, 0, this.positionOffset);
		shader.setVertexAttribute("a_normal", 3, GL20.GL_FLOAT, false, 0, this.normalOffset);
		shader.setVertexAttribute("a_uv", 2, GL20.GL_FLOAT, false, 0, this.uvOffset + (coordId * this.vertices * 8));
		shader.setVertexAttribute("a_bones", 4, this.openGLSkinType, false, this.skinStride, this.skinOffset);
		shader.setVertexAttribute("a_extendedBones", 4, this.openGLSkinType, false, this.skinStride,
				this.skinOffset + (this.boneCountOffsetBytes / 2));
		shader.setVertexAttribute("a_boneNumber", 1, this.openGLSkinType, false, this.skinStride,
				this.skinOffset + this.boneCountOffsetBytes);
	}

	public void render() {
		final GL20 gl = this.model.viewer.gl;

		PerfStats.draw();
		gl.glDrawElements(GL20.GL_TRIANGLES, this.elements, GL20.GL_UNSIGNED_SHORT, this.faceOffset);
	}

	public void bindSimple(final ShaderProgram shader) {
		shader.setVertexAttribute("a_position", 3, GL20.GL_FLOAT, false, 0, this.positionOffset);
		shader.setVertexAttribute("a_uv", 2, GL20.GL_FLOAT, false, 0, this.uvOffset);
	}

	public void renderSimple(final int instances) {
		final ANGLEInstancedArrays instancedArrays = this.model.viewer.webGL.instancedArrays;
		PerfStats.draw();
		instancedArrays.glDrawElementsInstancedANGLE(GL20.GL_TRIANGLES, this.elements, GL20.GL_UNSIGNED_SHORT,
				this.faceOffset, instances);
	}

	public void bindHd(final ShaderProgram shader, final SkinningType skinningType, final int coordId) {
		shader.setVertexAttribute("a_position", 3, GL20.GL_FLOAT, false, 0, this.positionOffset);
		shader.setVertexAttribute("a_normal", 3, GL20.GL_FLOAT, false, 0, this.normalOffset);
		shader.setVertexAttribute("a_uv", 2, GL20.GL_FLOAT, false, 0, this.uvOffset + (coordId * this.vertices * 8));

		shader.setVertexAttribute("a_tangent", 4, GL20.GL_FLOAT, false, 0, this.tangentOffset);

		// TODO ghostwolf splits here and allows HD with non-skin, or SD with skin
		if (this.wideSkin) {
			shader.setVertexAttribute("a_bones", 4, GL20.GL_UNSIGNED_SHORT, false, SetupGeosets.WIDE_SKIN_STRIDE,
					this.skinOffset);
			shader.setVertexAttribute("a_weights", 4, GL20.GL_UNSIGNED_BYTE, true, SetupGeosets.WIDE_SKIN_STRIDE,
					this.skinOffset + 8);
		}
		else {
			shader.setVertexAttribute("a_bones", 4, GL20.GL_UNSIGNED_BYTE, false, 8, this.skinOffset);
			shader.setVertexAttribute("a_weights", 4, GL20.GL_UNSIGNED_BYTE, true, 8, this.skinOffset + 4);
		}
	}

	private static final class Variants {
		private final boolean[] alpha;
		private final boolean[] color;
		private final boolean[] object;

		public Variants(final int size) {
			this.alpha = new boolean[size];
			this.color = new boolean[size];
			this.object = new boolean[size];
		}

	}
}
