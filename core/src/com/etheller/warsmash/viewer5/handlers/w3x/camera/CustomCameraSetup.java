package com.etheller.warsmash.viewer5.handlers.w3x.camera;

public class CustomCameraSetup extends CameraSetup {
	private float destPositionX;
	private float destPositionY;
	private String label = "";
	private int cameraType;

	public CustomCameraSetup(final float aoa, final float fov, final float rotation, final float roll,
			final float distance, final float farZ, final float nearZ, final float height) {
		super(aoa, fov, rotation, roll, distance, farZ, nearZ, height);
	}

	public void setDestPositionX(final float destPositionX) {
		this.destPositionX = destPositionX;
	}

	public void setDestPositionY(final float destPositionY) {
		this.destPositionY = destPositionY;
	}

	public String getLabel() {
		return this.label;
	}

	public void setLabel(final String label) {
		this.label = label;
	}

	public int getCameraType() {
		return this.cameraType;
	}

	public void setCameraType(final int cameraType) {
		this.cameraType = cameraType;
	}

	public float getDestPositionX() {
		return this.destPositionX;
	}

	public float getDestPositionY() {
		return this.destPositionY;
	}

}
