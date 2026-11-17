package com.etheller.warsmash.viewer5.handlers.mdx;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.viewer5.Shaders;
import com.etheller.warsmash.viewer5.gl.WebGL;

/**
 * Draws the shape of a model flattened onto the ground along the light, for the ground shadow mask: the skinned
 * vertices are slid along the light's direction onto the plane of the ground under the unit, so every triangle of the
 * model lands on that plane as it would in a shadow map of a flat slope.
 */
public final class MdxShadowCaster {
	private static final String VERTEX_COMMON = "    uniform mat4 u_vp;\r\n" + //
			"    uniform vec4 u_light;\r\n" + //
			"    uniform vec3 u_normal;\r\n" + //
			"    uniform vec3 u_ground;\r\n" + //
			"    attribute vec3 a_position;\r\n" + //
			"    attribute vec2 a_uv;\r\n" + //
			"    varying vec2 v_uv;\r\n" + //
			Shaders.boneTexture + "\r\n";

	private static final String VERTEX_FLATTEN = "      float height = max(dot(u_normal, position - u_ground), 0.0);\r\n" + //
			"      position -= u_light.xyz * (height * u_light.w);\r\n" + //
			"      v_uv = a_uv;\r\n" + //
			"      gl_Position = u_vp * vec4(position, 1.0);\r\n" + //
			"    }";

	private static final String VERTEX_GROUPS = VERTEX_COMMON + //
			"    attribute vec4 a_bones;\r\n" + //
			"    #ifdef EXTENDED_BONES\r\n" + //
			"    attribute vec4 a_extendedBones;\r\n" + //
			"    #endif\r\n" + //
			"    attribute float a_boneNumber;\r\n" + //
			"    void main() {\r\n" + //
			"      vec3 position = vec3(0.0);\r\n" + //
			"      if (a_boneNumber > 0.0) {\r\n" + //
			"        vec4 position4 = vec4(a_position, 1.0);\r\n" + //
			"        vec4 p = vec4(0.0);\r\n" + //
			"        for (int i = 0; i < 4; i++) {\r\n" + //
			"          if (a_bones[i] > 0.0) {\r\n" + //
			"            p += fetchMatrix(a_bones[i] - 1.0, 0.0) * position4;\r\n" + //
			"          }\r\n" + //
			"        }\r\n" + //
			"        #ifdef EXTENDED_BONES\r\n" + //
			"        for (int i = 0; i < 4; i++) {\r\n" + //
			"          if (a_extendedBones[i] > 0.0) {\r\n" + //
			"            p += fetchMatrix(a_extendedBones[i] - 1.0, 0.0) * position4;\r\n" + //
			"          }\r\n" + //
			"        }\r\n" + //
			"        #endif\r\n" + //
			"        position = p.xyz / a_boneNumber;\r\n" + //
			"      }\r\n" + //
			VERTEX_FLATTEN;

	private static final String VERTEX_SKIN = VERTEX_COMMON + //
			"    attribute vec4 a_bones;\r\n" + //
			"    attribute vec4 a_weights;\r\n" + //
			"    void main() {\r\n" + //
			"      mat4 bone = mat4(0.0);\r\n" + //
			"      bone += fetchMatrix(a_bones[0], 0.0) * a_weights[0];\r\n" + //
			"      bone += fetchMatrix(a_bones[1], 0.0) * a_weights[1];\r\n" + //
			"      bone += fetchMatrix(a_bones[2], 0.0) * a_weights[2];\r\n" + //
			"      bone += fetchMatrix(a_bones[3], 0.0) * a_weights[3];\r\n" + //
			"      vec3 position = vec3(bone * vec4(a_position, 1.0));\r\n" + //
			VERTEX_FLATTEN;

	private static final String FRAGMENT = "    uniform sampler2D u_texture;\r\n" + //
			"    uniform bool u_cutout;\r\n" + //
			"    varying vec2 v_uv;\r\n" + //
			"    void main() {\r\n" + //
			"      if (u_cutout && (texture2D(u_texture, v_uv).a < 0.6)) {\r\n" + //
			"        discard;\r\n" + //
			"      }\r\n" + //
			"      gl_FragColor = vec4(1.0);\r\n" + //
			"    }";

	private ShaderProgram groups;
	private ShaderProgram extended;
	private ShaderProgram skin;
	private final float[] lightRow = new float[4];

	/**
	 * @param light  the direction from the ground towards the light, of any length
	 * @param normal the unit normal of the ground plane
	 * @param ground a point of the ground plane, under the unit
	 */
	public void draw(final WebGL webGL, final MdxComplexInstance instance, final Matrix4 viewProjection,
			final Vector3 light, final Vector3 normal, final Vector3 ground) {
		if (this.groups == null) {
			this.groups = webGL.createShaderProgram(VERTEX_GROUPS, FRAGMENT);
			this.extended = webGL.createShaderProgram("#define EXTENDED_BONES\r\n" + VERTEX_GROUPS, FRAGMENT);
			this.skin = webGL.createShaderProgram(VERTEX_SKIN, FRAGMENT);
		}
		final MdxModel model = (MdxModel) instance.model;
		this.lightRow[0] = light.x;
		this.lightRow[1] = light.y;
		this.lightRow[2] = light.z;
		this.lightRow[3] = 1f / Math.max(normal.dot(light), 0.05f);
		drawGroups(webGL, instance, model.opaqueGroups, viewProjection, normal, ground);
		drawGroups(webGL, instance, model.translucentGroups, viewProjection, normal, ground);
	}

	private void drawGroups(final WebGL webGL, final MdxComplexInstance instance,
			final java.util.List<GenericGroup> groups, final Matrix4 viewProjection, final Vector3 normal,
			final Vector3 ground) {
		for (int i = 0, n = groups.size(); i < n; i++) {
			final GenericGroup group = groups.get(i);
			if (!(group instanceof BatchGroup)) {
				continue;
			}
			final BatchGroup batchGroup = (BatchGroup) group;
			final ShaderProgram shader;
			if (batchGroup.hd) {
				shader = this.skin;
			}
			else if (batchGroup.skinningType == SkinningType.ExtendedVertexGroups) {
				shader = this.extended;
			}
			else {
				shader = this.groups;
			}
			webGL.useShaderProgram(shader);
			shader.setUniformMatrix("u_vp", viewProjection);
			shader.setUniformf("u_light", this.lightRow[0], this.lightRow[1], this.lightRow[2], this.lightRow[3]);
			shader.setUniformf("u_normal", normal.x, normal.y, normal.z);
			shader.setUniformf("u_ground", ground.x, ground.y, ground.z);
			shader.setUniformi("u_texture", 0);
			batchGroup.renderShadow(instance, shader);
		}
	}

	public static void prepareState(final GL20 gl) {
		gl.glDisable(GL20.GL_BLEND);
		gl.glDisable(GL20.GL_CULL_FACE);
		gl.glDisable(GL20.GL_DEPTH_TEST);
		gl.glDepthMask(false);
	}
}
