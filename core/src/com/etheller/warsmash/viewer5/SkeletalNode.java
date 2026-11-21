package com.etheller.warsmash.viewer5;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.util.RenderMathUtils;

public abstract class SkeletalNode extends GenericNode {
	// The local pose of a node that no timeline moves is the same default for all of them: they read these, and a
	// node that is ever driven from outside takes a pose of its own first.
	private static final Vector3 NO_TRANSLATION = new Vector3();
	private static final Quaternion NO_ROTATION = new Quaternion(0, 0, 0, 1);
	private static final Vector3 NO_SCALING = new Vector3(1, 1, 1);

	public UpdatableObject object;

	public boolean billboarded;
	public boolean billboardedX;
	public boolean billboardedY;
	public boolean billboardedZ;

	// A node holds a blend pose only after a sequence change blended into it; most nodes of a map never do.
	public Quaternion localBlendRotation;
	public Vector3 localBlendScale;

	public Quaternion overrideWorldRotation;
	public Quaternion preOverrideLocalRotation;

	public SkeletalNode() {
		this.pivot = new Vector3();
		this.localLocation = new Vector3();
		this.localRotation = new Quaternion(0, 0, 0, 1);
		this.localScale = new Vector3(1, 1, 1);
		this.worldLocation = new Vector3();
		this.worldRotation = new Quaternion();
		this.worldScale = new Vector3(1, 1, 1);
		this.worldMatrix = new Matrix4();
		this.dontInheritTranslation = false;
		this.dontInheritRotation = false;
		this.dontInheritScaling = false;

		this.visible = true;
		this.wasDirty = false;

		/**
		 * The object associated with this node, if there is any.
		 *
		 * @member {?}
		 */
		this.object = null;

		this.localRotation.w = 1;

		this.localScale.set(1, 1, 1);

		this.dirty = true;

		this.billboarded = false;
		this.billboardedX = false;
		this.billboardedY = false;
		this.billboardedZ = false;
	}

	/** @param heaps the scratch values of the calling thread ({@link SceneUpdateWorkers#heaps()}) */
	public void recalculateTransformation(final Scene scene, final float blendTimeRatio,
			final SceneUpdateWorkers.Heaps heaps) {
		final Vector3 cameraRayHeap = heaps.cameraRay;
		final Vector3 billboardAxisHeap = heaps.billboardAxis;
		final Quaternion rotationHeap = heaps.rotation;
		final Quaternion rotationHeap2 = heaps.rotation2;
		final Vector3 scalingHeap = heaps.scaling;
		final Quaternion inverseRotationHeap = heaps.inverseRotation;
		final Vector3 inverseScaleHeap = heaps.inverseScale;
		final Matrix4 localMatrixHeap = heaps.localMatrix;
		final Vector3 blendHeap = heaps.blend;
		final Vector3 blendScaleHeap = heaps.blendScale;
		final float inverseBlendRatio = 1 - blendTimeRatio;
		final Quaternion computedRotation;
		Vector3 computedScaling;
		Vector3 computedLocation;

		if (this.dontInheritScaling) {
			computedScaling = scalingHeap;

			final Vector3 parentInverseScale = this.parent.getInverseWorldScale(inverseScaleHeap);
			computedScaling.x = parentInverseScale.x * this.localScale.x;
			computedScaling.y = parentInverseScale.y * this.localScale.y;
			computedScaling.z = parentInverseScale.z * this.localScale.z;

			this.worldScale.x = this.localScale.x;
			this.worldScale.y = this.localScale.y;
			this.worldScale.z = this.localScale.z;
		}
		else {
			if (!Float.isNaN(blendTimeRatio) && (blendTimeRatio > 0)) {
				if (this.localBlendScale == null) {
					this.localBlendScale = new Vector3(1, 1, 1);
				}
				blendScaleHeap.set(this.localScale).scl(inverseBlendRatio)
						.add(blendHeap.set(this.localBlendScale).scl(blendTimeRatio));
				computedScaling = blendScaleHeap;
			}
			else {
				computedScaling = this.localScale;
			}

			final Vector3 parentScale = this.parent.worldScale;
			this.worldScale.x = parentScale.x * this.localScale.x;
			this.worldScale.y = parentScale.y * this.localScale.y;
			this.worldScale.z = parentScale.z * this.localScale.z;
		}

		if (this.billboarded) {
			computedRotation = rotationHeap;

			this.parent.getInverseWorldRotation(computedRotation);
			if (scene != null) {
				// TODO null scene is stupid, and happens rarely
				computedRotation.mul(scene.camera.inverseRotation);
			}

			convertBasis(computedRotation);
			computedRotation.mul(this.localRotation);
		}
		else {
			computedRotation = rotationHeap.set(this.localRotation);
			if (!Float.isNaN(blendTimeRatio) && (blendTimeRatio > 0)) {
				if (this.localBlendRotation == null) {
					this.localBlendRotation = new Quaternion(0, 0, 0, 1);
				}
				computedRotation.slerp(this.localBlendRotation, blendTimeRatio);
			}

			if (this.billboardedX) {
				if (computedScaling == this.localScale) {
					computedScaling = scalingHeap.set(computedScaling);
				}
				// It took me many hours to deduce from playing around that this negative one
				// multiplier should be here. I suggest a lot of testing before you remove it.
				computedScaling.z *= -1;

				final Camera camera = scene.camera;
				cameraRayHeap.set(camera.billboardedVectors[6]);

				rotationHeap2.set(computedRotation);
				// Inverse that local rotation
				rotationHeap2.x = -rotationHeap2.x;
				rotationHeap2.y = -rotationHeap2.y;
				rotationHeap2.z = -rotationHeap2.z;

				rotationHeap2.mul(this.parent.getInverseWorldRotation(inverseRotationHeap));

				transform(heaps, rotationHeap2, cameraRayHeap);

				billboardAxisHeap.set(1, 0, 0);
				final float angle = (float) Math.atan2(cameraRayHeap.z, cameraRayHeap.y);
				rotationHeap2.setFromAxisRad(billboardAxisHeap, angle);

				computedRotation.mul(rotationHeap2);
			}
			else if (this.billboardedY) {
				final Camera camera = scene.camera;
				cameraRayHeap.set(camera.billboardedVectors[6]);

				rotationHeap2.set(computedRotation);
				// Inverse that local rotation
				rotationHeap2.x = -rotationHeap2.x;
				rotationHeap2.y = -rotationHeap2.y;
				rotationHeap2.z = -rotationHeap2.z;

				rotationHeap2.mul(this.parent.getInverseWorldRotation(inverseRotationHeap));

				transform(heaps, rotationHeap2, cameraRayHeap);

				billboardAxisHeap.set(0, 1, 0);
				final float angle = (float) Math.atan2(-cameraRayHeap.z, cameraRayHeap.x);
				rotationHeap2.setFromAxisRad(billboardAxisHeap, angle);

				computedRotation.mul(rotationHeap2);
			}
			else if (this.billboardedZ) {
				final Camera camera = scene.camera;
				cameraRayHeap.set(camera.billboardedVectors[6]);

				rotationHeap2.set(computedRotation);
				// Inverse that local rotation
				rotationHeap2.x = -rotationHeap2.x;
				rotationHeap2.y = -rotationHeap2.y;
				rotationHeap2.z = -rotationHeap2.z;

				rotationHeap2.mul(this.parent.getInverseWorldRotation(inverseRotationHeap));

				transform(heaps, rotationHeap2, cameraRayHeap);

				billboardAxisHeap.set(0, 0, 1);
				final float angle = (float) Math.atan2(cameraRayHeap.y, cameraRayHeap.x);
				rotationHeap2.setFromAxisRad(billboardAxisHeap, angle);

				computedRotation.mul(rotationHeap2);
			}
			else if (this.dontInheritRotation) {
				computedRotation.mulLeft(this.parent.getInverseWorldRotation(inverseRotationHeap));
			}
		}

		computedLocation = this.localLocation;
		RenderMathUtils.fromRotationTranslationScaleOrigin(computedRotation, computedLocation, computedScaling,
				localMatrixHeap, this.pivot);

		RenderMathUtils.mul(this.worldMatrix, this.parent.worldMatrix, localMatrixHeap);

		RenderMathUtils.mul(this.worldRotation, this.parent.worldRotation, computedRotation);

		// World location
		final float x = this.pivot.x;
		final float y = this.pivot.y;
		final float z = this.pivot.z;
		this.worldLocation.x = (this.worldMatrix.val[Matrix4.M00] * x) + (this.worldMatrix.val[Matrix4.M01] * y)
				+ (this.worldMatrix.val[Matrix4.M02] * z) + this.worldMatrix.val[Matrix4.M03];
		this.worldLocation.y = (this.worldMatrix.val[Matrix4.M10] * x) + (this.worldMatrix.val[Matrix4.M11] * y)
				+ (this.worldMatrix.val[Matrix4.M12] * z) + this.worldMatrix.val[Matrix4.M13];
		this.worldLocation.z = (this.worldMatrix.val[Matrix4.M20] * x) + (this.worldMatrix.val[Matrix4.M21] * y)
				+ (this.worldMatrix.val[Matrix4.M22] * z) + this.worldMatrix.val[Matrix4.M23];
	}

	/** What {@link Quaternion#transform(Vector3)} does, with the thread's own temporaries. */
	private static void transform(final SceneUpdateWorkers.Heaps heaps, final Quaternion rotation, final Vector3 v) {
		final Quaternion conjugate = heaps.transformTmp2.set(rotation);
		conjugate.conjugate();
		conjugate.mulLeft(heaps.transformTmp1.set(v.x, v.y, v.z, 0)).mulLeft(rotation);
		v.x = conjugate.x;
		v.y = conjugate.y;
		v.z = conjugate.z;
	}

	public void beginBlending() {
		if (this.localBlendRotation == null) {
			this.localBlendRotation = new Quaternion(0, 0, 0, 1);
			this.localBlendScale = new Vector3(1, 1, 1);
		}
		if (this.preOverrideLocalRotation != null) {
			this.localBlendRotation.set(this.preOverrideLocalRotation);
			this.preOverrideLocalRotation = null;
		}
		else {
			this.localBlendRotation.set(this.localRotation);
		}
		this.localBlendScale.set(this.localScale);
	}

	public void updateChildren(final float dt, final Scene scene) {
		if (this.children == null) {
			return;
		}
		for (int i = 0, l = this.children.size(); i < l; i++) {
			this.children.get(i).update(dt, scene);
		}
	}

	/** Makes this node hold the default pose of its own where it only shared it (see the constants above). */
	public void shareDefaultPose(final boolean translation, final boolean rotation, final boolean scaling) {
		if (translation) {
			this.localLocation = NO_TRANSLATION;
		}
		if (rotation) {
			this.localRotation = NO_ROTATION;
		}
		if (scaling) {
			this.localScale = NO_SCALING;
		}
	}

	public void ownLocalPose() {
		if (this.localLocation == NO_TRANSLATION) {
			this.localLocation = new Vector3(NO_TRANSLATION);
		}
		if (this.localRotation == NO_ROTATION) {
			this.localRotation = new Quaternion(NO_ROTATION);
		}
		if (this.localScale == NO_SCALING) {
			this.localScale = new Vector3(NO_SCALING);
		}
	}

	public void setOverrideWorldRotation(final Quaternion overrideLocalRotation) {
		ownLocalPose();
		this.overrideWorldRotation = overrideLocalRotation;
		this.preOverrideLocalRotation = new Quaternion();
		this.preOverrideLocalRotation.set(this.localRotation);
	}

	protected abstract void convertBasis(Quaternion computedRotation);
}
