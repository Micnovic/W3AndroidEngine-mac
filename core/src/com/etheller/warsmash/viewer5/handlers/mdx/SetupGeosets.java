package com.etheller.warsmash.viewer5.handlers.mdx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.etheller.warsmash.util.Log;
import com.hiveworkshop.rms.parsers.mdlx.MdlxGeoset;
import com.hiveworkshop.rms.parsers.mdlx.MdlxMaterial;

public class SetupGeosets {

	/**
	 * The geometry of a model made ready for upload, from the parsed model alone: this can be made on a helper thread
	 * before the model is built, which then only copies it to the GPU buffers.
	 */
	public static final class Prepared {
		private final boolean bigNodeSpace;
		private final PreparedGeoset[] geosets;
		private int positionBytes;
		private int normalBytes;
		private int uvBytes;
		private int tangentBytes;
		private int skinBytes;
		private int faceBytes;

		private Prepared(final boolean bigNodeSpace, final int geosetCount) {
			this.bigNodeSpace = bigNodeSpace;
			this.geosets = new PreparedGeoset[geosetCount];
		}

		/** Frees the native memory the geosets hold for picking, for a model that is not built after all. */
		public void dispose() {
			for (final PreparedGeoset geoset : this.geosets) {
				if ((geoset != null) && (geoset.pick != null)) {
					geoset.pick.dispose();
					geoset.pick = null;
				}
			}
		}
	}

	private static final class PreparedGeoset {
		SkinningType batchType;
		boolean wideSkin;
		/** The bytes of the skin buffer section, as they are uploaded. */
		byte[] skin;
		short[] faces;
		Geoset.PickData pick;
	}

	public static Prepared prepare(final List<MdlxGeoset> geosets, final List<MdlxMaterial> materials,
			final boolean bigNodeSpace) {
		final Prepared prepared = new Prepared(bigNodeSpace, geosets.size());
		final int extendedBatchStride = bigNodeSpace ? 36 : 9;
		final int normalBatchStride = bigNodeSpace ? 20 : 5;

		for (int i = 0, l = geosets.size(); i < l; i++) {
			final MdlxGeoset geoset = geosets.get(i);

			if ((geoset.lod == 0) || (geoset.lod == -1)) {
				final PreparedGeoset out = new PreparedGeoset();
				prepared.geosets[i] = out;
				final int vertices = geoset.getVertices().length / 3;

				prepared.positionBytes += vertices * 12;
				prepared.normalBytes += vertices * 12;
				prepared.uvBytes += geoset.getUvSets().length * vertices * 8;

				if (geoset.tangents != null) {
					prepared.tangentBytes += vertices * 16;
				}

				final boolean hd = "Shader_HD_DefaultUnit".equals(materials.get((int) geoset.getMaterialId()).shader);
				if ((geoset.skin != null) && hd) {
					prepared.skinBytes += vertices * (needsWideSkin(geoset) ? WIDE_SKIN_STRIDE : 8);

					out.batchType = SkinningType.Skin;
				}
				else if (geoset.skin != null) {
					prepared.skinBytes += vertices * extendedBatchStride;

					out.batchType = SkinningType.ExtendedVertexGroups;
				}
				else {
					long biggestGroup = 0;

					for (final long group : geoset.getMatrixGroups()) {
						if (group > biggestGroup) {
							biggestGroup = group;
						}
					}

					if (biggestGroup > 4) {
						prepared.skinBytes += vertices * extendedBatchStride;

						out.batchType = SkinningType.ExtendedVertexGroups;
					}
					else {
						prepared.skinBytes += vertices * normalBatchStride;

						out.batchType = SkinningType.VertexGroups;
					}
				}

				prepared.faceBytes += geoset.getFaces().length * 2;

				prepareSkin(out, geoset, vertices, bigNodeSpace);
				out.faces = geoset.getFaces();
				if (geoset.getSelectionFlags() != 4) {
					out.pick = Geoset.PickData.prepare(geoset);
				}
			}
		}
		return prepared;
	}

	private static void prepareSkin(final PreparedGeoset out, final MdlxGeoset geoset, final int vertices,
			final boolean bigNodeSpace) {
		final int maxBones = (out.batchType == SkinningType.ExtendedVertexGroups) ? 8 : 4;
		final int[] skin;

		if (out.batchType == SkinningType.Skin) {
			skin = new int[geoset.skin.length];
			for (int j = 0; j < geoset.skin.length; j++) {
				skin[j] = geoset.skin[j] & 0xFFFF;
			}
			out.wideSkin = needsWideSkin(geoset);
		}
		else if (geoset.skin != null) {
			skin = skinToVertexGroups(geoset.skin, vertices);
		}
		else {
			final long[] matrixIndices = geoset.getMatrixIndices();
			final short[] vertexGroups = geoset.getVertexGroups();
			final List<long[]> matrixGroups = new ArrayList<>();
			int offset = 0;
			// Normally the shader supports up to 4 bones per vertex.
			// This is enough for almost every existing Warcraft 3 model.
			// That being said, there are a few models with geosets that need more, for
			// example the Water Elemental.
			// These geosets use a different shader, which support up to 8 bones per vertex.

			skin = new int[vertices * (maxBones + 1)];

			// Slice the matrix groups
			for (final long size : geoset.getMatrixGroups()) {
				matrixGroups.add(Arrays.copyOfRange(matrixIndices, offset, (int) (offset + size)));
				offset += size;
			}

			// Parse the skinning.
			for (int si = 0; si < vertices; si++) {
				final short vertexGroup = vertexGroups[si];
				final long[] matrixGroup = (vertexGroup >= matrixGroups.size()) ? null : matrixGroups.get(vertexGroup);

				offset = si * (maxBones + 1);

				// Somehow in some bad models a vertex group index refers to an invalid matrix
				// group.
				// Such models are still loaded by the game.
				if (matrixGroup != null) {
					final int bones = Math.min(matrixGroup.length, maxBones);

					for (int j = 0; j < bones; j++) {
						skin[offset + j] = (int) (matrixGroup[j] + 1); // 1 is added to diffrentiate
																		// between matrix 0, and no matrix.
					}

					skin[offset + maxBones] = bones;
				}
			}
		}

		if (out.wideSkin) {
			out.skin = wideSkinBytes(skin);
		}
		else if ((out.batchType != SkinningType.Skin) && bigNodeSpace) {
			final ByteBuffer ints = ByteBuffer.allocate(skin.length * 4).order(ByteOrder.nativeOrder());
			ints.asIntBuffer().put(skin);
			out.skin = ints.array();
		}
		else {
			out.skin = new byte[skin.length];
			for (int j = 0; j < skin.length; j++) {
				out.skin[j] = (byte) skin[j];
			}
		}
	}

	/** Uploads a prepared model's geometry to its GPU buffers, a geoset at a time. */
	static final class Upload {
		private final MdxModel model;
		private final List<MdlxGeoset> geosets;
		private final Prepared prepared;
		private int next;
		private int positionOffset;
		private int normalOffset;
		private int uvOffset;
		private int tangentOffset;
		private int skinOffset;
		private int faceOffset;
		private final int extendedBatchStride;
		private final int normalBatchStride;
		private final int openGLSkinType;
		private final int normalBatchBoneCountOffsetBytes;
		private final int extendedBatchBoneCountOffsetBytes;

		Upload(final MdxModel model, final List<MdlxGeoset> geosets, final Prepared prepared) {
			this.model = model;
			this.geosets = geosets;
			this.prepared = prepared;
			final boolean bigNodeSpace = prepared.bigNodeSpace;
			this.extendedBatchStride = bigNodeSpace ? 36 : 9;
			this.normalBatchStride = bigNodeSpace ? 20 : 5;
			this.openGLSkinType = bigNodeSpace ? GL20.GL_UNSIGNED_INT : GL20.GL_UNSIGNED_BYTE;
			this.normalBatchBoneCountOffsetBytes = bigNodeSpace ? 16 : 4;
			this.extendedBatchBoneCountOffsetBytes = bigNodeSpace ? 32 : 8;
			this.normalOffset = prepared.positionBytes;
			this.uvOffset = this.normalOffset + prepared.normalBytes;
			this.tangentOffset = this.uvOffset + prepared.uvBytes;
			this.skinOffset = this.tangentOffset + prepared.tangentBytes;
			if (!geosets.isEmpty()) {
				final GL20 gl = model.viewer.gl;
				model.arrayBuffer = gl.glGenBuffer();
				gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, model.arrayBuffer);
				gl.glBufferData(GL20.GL_ARRAY_BUFFER, this.skinOffset + prepared.skinBytes, null,
						GL20.GL_STATIC_DRAW);

				model.elementBuffer = gl.glGenBuffer();
				gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, model.elementBuffer);
				gl.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, prepared.faceBytes, null, GL20.GL_STATIC_DRAW);
			}
		}

		/** Frees the pick memory of the geosets not made yet. */
		void discard() {
			this.prepared.dispose();
		}

		boolean isDone() {
			return this.next >= this.geosets.size();
		}

		/** Uploads the next geoset if it is one that is kept; a frame in between may have bound other buffers. */
		void uploadNext() {
			final int i = this.next++;
			final PreparedGeoset prepared = this.prepared.geosets[i];
			if (prepared == null) {
				return;
			}
			final MdxModel model = this.model;
			final GL20 gl = model.viewer.gl;
			final MdlxGeoset geoset = this.geosets.get(i);
			gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, model.arrayBuffer);
			gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, model.elementBuffer);

			final SkinningType batchType = prepared.batchType;
			final float[] positions = geoset.getVertices();
			final float[] normals = geoset.getNormals();
			final float[][] uvSets = geoset.getUvSets();
			final float[] tangents = geoset.getTangents();
			final int vertices = positions.length / 3;

			final int skinStride;
			final int boneCountOffsetBytes;
			if (batchType == SkinningType.ExtendedVertexGroups) {
				skinStride = this.extendedBatchStride;
				boneCountOffsetBytes = this.extendedBatchBoneCountOffsetBytes;
			}
			else {
				skinStride = this.normalBatchStride;
				boneCountOffsetBytes = this.normalBatchBoneCountOffsetBytes;
			}

			final boolean unselectable = geoset.getSelectionFlags() == 4;
			final Geoset vGeoset = new Geoset(model, model.getGeosets().size(), i, this.positionOffset,
					this.normalOffset, this.uvOffset, this.tangentOffset, this.skinOffset, this.faceOffset, vertices,
					prepared.faces.length, this.openGLSkinType, skinStride, boneCountOffsetBytes, unselectable, geoset,
					prepared.pick);
			vGeoset.wideSkin = prepared.wideSkin;
			prepared.pick = null;

			model.getGeosets().add(vGeoset);

			final Material material = model.materials.get((int) geoset.getMaterialId());
			final boolean isHd = "Shader_HD_DefaultUnit".equals(material.shader);

			if (isHd) {
				model.batches.add(new Batch(model.batches.size(), vGeoset, material, batchType));
			}
			else {
				for (final Layer layer : model.getMaterials().get((int) geoset.getMaterialId()).layers) {
					model.batches.add(new Batch(model.batches.size(), vGeoset, layer, batchType));
				}
			}

			// Positions.
			gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, this.positionOffset, positions.length * 4,
					uploadFloats(positions));
			this.positionOffset += positions.length * 4;

			// Normals.
			gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, this.normalOffset, normals.length * 4, uploadFloats(normals));
			this.normalOffset += normals.length * 4;

			// Texture coordinates.
			for (final float[] uvSet : uvSets) {
				gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, this.uvOffset, uvSet.length * 4, uploadFloats(uvSet));
				this.uvOffset += uvSet.length * 4;
			}

			if (tangents != null) {
				gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, this.tangentOffset, tangents.length * 4,
						uploadFloats(tangents));
				this.tangentOffset += tangents.length * 4;
			}

			// Skin.
			final byte[] skin = prepared.skin;
			gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, this.skinOffset, skin.length, uploadBytes(skin));
			this.skinOffset += skin.length;

			// Faces.
			gl.glBufferSubData(GL20.GL_ELEMENT_ARRAY_BUFFER, this.faceOffset, prepared.faces.length * 2,
					uploadFaces(prepared.faces));
			this.faceOffset += prepared.faces.length * 2;

			// What was uploaded is kept in the GPU buffers only.
			prepared.skin = null;
			prepared.faces = null;
			if (isDone()) {
				reportGpuMemory(gl, model);
			}
		}

		/**
		 * A GPU whose memory is used up (several games share the memory of an integrated one) leaves the buffers it
		 * could not fill with whatever was there, and the model draws as scattered triangles. Only desktops ask:
		 * glGetError waits for the GPU on phones.
		 */
		private static void reportGpuMemory(final GL20 gl, final MdxModel model) {
			if ((Gdx.app != null) && (Gdx.app.getType() == ApplicationType.Desktop)
					&& (gl.glGetError() == GL20.GL_OUT_OF_MEMORY)) {
				Log.error("The GPU is out of memory: " + model.fetchUrl + " is not uploaded whole");
			}
		}
	}

	public static void setupGeosets(final MdxModel model, final List<MdlxGeoset> geosets,
			final List<MdlxMaterial> materials, final boolean bigNodeSpace) {
		final Upload upload = new Upload(model, geosets, prepare(geosets, materials, bigNodeSpace));
		while (!upload.isDone()) {
			upload.uploadNext();
		}
	}

	/**
	 * Bytes per vertex of a wide HD skin: four bone indices as unsigned shorts, then four weights as bytes.
	 */
	static final int WIDE_SKIN_STRIDE = 12;

	/**
	 * The HD skin keeps a bone index in one byte, which cannot address the bones of a model with more than 255 of them.
	 * Only the geosets that really use such an index take the wider layout.
	 */
	private static boolean needsWideSkin(final MdlxGeoset geoset) {
		final short[] skin = geoset.skin;
		for (int in = 0; in < skin.length; in += 8) {
			for (int j = 0; j < 4; j++) {
				if ((skin[in + j] & 0xFFFF) > 255) {
					return true;
				}
			}
		}
		return false;
	}

	private static byte[] wideSkinBytes(final int[] skin) {
		final ByteBuffer buffer = ByteBuffer.allocate((skin.length / 8) * WIDE_SKIN_STRIDE).order(ByteOrder.nativeOrder());
		for (int in = 0; in < skin.length; in += 8) {
			for (int j = 0; j < 4; j++) {
				buffer.putShort((short) skin[in + j]);
			}
			for (int j = 4; j < 8; j++) {
				buffer.put((byte) skin[in + j]);
			}
		}
		return buffer.array();
	}

	// The geometry of every model went through its own native buffer, which only a collection frees; a model load
	// then piled up as much native memory as the models' geometry came to. One buffer serves each upload in turn.
	private static final int UPLOAD_SCRATCH_KEEP = 4 << 20;
	private static final ThreadLocal<ByteBuffer> UPLOAD_SCRATCH = new ThreadLocal<>();

	private static ByteBuffer uploadScratch(final int bytes) {
		ByteBuffer buffer = UPLOAD_SCRATCH.get();
		if ((buffer == null) || (buffer.capacity() < bytes)) {
			buffer = ByteBuffer.allocateDirect(Math.max(bytes, 1 << 16)).order(ByteOrder.nativeOrder());
			if (bytes <= UPLOAD_SCRATCH_KEEP) {
				UPLOAD_SCRATCH.set(buffer);
			}
		}
		buffer.clear();
		buffer.limit(bytes);
		return buffer;
	}

	private static ByteBuffer uploadFloats(final float[] values) {
		final ByteBuffer buffer = uploadScratch(values.length * 4);
		buffer.asFloatBuffer().put(values);
		return buffer;
	}

	private static ByteBuffer uploadBytes(final byte[] values) {
		final ByteBuffer buffer = uploadScratch(values.length);
		buffer.put(values);
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer uploadFaces(final short[] faces) {
		final ByteBuffer buffer = uploadScratch(faces.length * 2);
		buffer.asShortBuffer().put(faces);
		return buffer;
	}

	/**
	 * Reforged SD models can carry weighted skin, but the SD shader averages its bones equally. A bone therefore fills
	 * as many of the 8 extended slots as its weight is worth.
	 */
	private static int[] skinToVertexGroups(final short[] skin, final int vertices) {
		final int slots = 8;
		final int[] groups = new int[vertices * (slots + 1)];
		for (int vertex = 0; vertex < vertices; vertex++) {
			final int in = vertex * 8;
			final int out = vertex * (slots + 1);
			int total = 0;
			for (int j = 0; j < 4; j++) {
				total += skin[in + 4 + j] & 0xFF;
			}
			if (total == 0) {
				groups[out] = (skin[in] & 0xFFFF) + 1;
				groups[out + slots] = 1;
				continue;
			}
			int used = 0;
			int weightSoFar = 0;
			for (int j = 0; j < 4; j++) {
				weightSoFar += skin[in + 4 + j] & 0xFF;
				final int end = Math.round(((float) weightSoFar * slots) / total);
				while (used < end) {
					groups[out + used++] = (skin[in + j] & 0xFFFF) + 1;
				}
			}
			groups[out + slots] = used;
		}
		return groups;
	}
}
