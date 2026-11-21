package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

/**
 * What only the local interface knows and the simulation's scripts may ask for: the camera, the screen size, the
 * pointer and the unit under it. The render thread fills one every frame and publishes it through a
 * {@link Exchange}; the simulation thread reads the latest published copy whenever a script native asks (these reads
 * already go through the replay's recorded local values, so a replay stays exact).
 * <p>
 * Plain data; one instance is owned by one thread at a time.
 */
public final class LocalUiSnapshot {
	public static final int SHIFT = 1;
	public static final int CONTROL = 1 << 1;
	public static final int ALT = 1 << 2;
	/** Entries of {@link #cameraFields}: the camera field enumeration has this many values. */
	public static final int CAMERA_FIELDS = 14;

	/** Render frame number the values were taken in. */
	public long renderFrame;
	/** How many of the scripts' camera commands the render thread had run when the values were taken. */
	public long cameraCommandsRun;
	public float cameraX;
	public float cameraY;
	public float cameraZ;
	public float cameraDistance;
	public float cameraAngleOfAttack;
	public float cameraRotation;
	public float cameraFieldOfView;
	/** What the camera natives report: the target and the eye with the camera's shake in them. */
	public float targetX;
	public float targetY;
	public float targetZ;
	public float eyeX;
	public float eyeY;
	public float eyeZ;
	/** {@code GetCameraField} of every {@code CCameraField}, by ordinal (angles in radians, as the native gives them). */
	public final float[] cameraFields = new float[CAMERA_FIELDS];
	/** The rectangle the script keeps the camera in. */
	public float boundsX;
	public float boundsY;
	public float boundsWidth;
	public float boundsHeight;
	public int cameraType;
	/** Bit i is set while camera field i follows the player's input. */
	public int fieldsControlledByInput;
	public boolean cineFilterDisplayed;
	public int screenWidth;
	public int screenHeight;
	public float pointerScreenX;
	public float pointerScreenY;
	public float pointerWorldX;
	public float pointerWorldY;
	/** Handle of the unit under the pointer, 0 for none; the simulation puts its details in the next frame. */
	public int hoveredUnitHandle;
	public int modifiers;
	/** The meta keys held, as the mask the key event natives use. */
	public int osMetaKeys;
	/** Bit per libGDX mouse button held. */
	public int mouseButtons;
	public static final int KEYS = 256;
	/** Keys held by libGDX key code; only kept up to date once a script has asked for a key. */
	public final boolean[] keysDown = new boolean[KEYS];

	public void copyFrom(final LocalUiSnapshot other) {
		renderFrame = other.renderFrame;
		cameraCommandsRun = other.cameraCommandsRun;
		cameraX = other.cameraX;
		cameraY = other.cameraY;
		cameraZ = other.cameraZ;
		cameraDistance = other.cameraDistance;
		cameraAngleOfAttack = other.cameraAngleOfAttack;
		cameraRotation = other.cameraRotation;
		cameraFieldOfView = other.cameraFieldOfView;
		targetX = other.targetX;
		targetY = other.targetY;
		targetZ = other.targetZ;
		eyeX = other.eyeX;
		eyeY = other.eyeY;
		eyeZ = other.eyeZ;
		System.arraycopy(other.cameraFields, 0, cameraFields, 0, CAMERA_FIELDS);
		boundsX = other.boundsX;
		boundsY = other.boundsY;
		boundsWidth = other.boundsWidth;
		boundsHeight = other.boundsHeight;
		cameraType = other.cameraType;
		fieldsControlledByInput = other.fieldsControlledByInput;
		cineFilterDisplayed = other.cineFilterDisplayed;
		screenWidth = other.screenWidth;
		screenHeight = other.screenHeight;
		pointerScreenX = other.pointerScreenX;
		pointerScreenY = other.pointerScreenY;
		pointerWorldX = other.pointerWorldX;
		pointerWorldY = other.pointerWorldY;
		hoveredUnitHandle = other.hoveredUnitHandle;
		modifiers = other.modifiers;
		osMetaKeys = other.osMetaKeys;
		mouseButtons = other.mouseButtons;
		System.arraycopy(other.keysDown, 0, keysDown, 0, KEYS);
	}

	/**
	 * Hand-over point between the two threads. Writer: the render thread calls {@link #publish} once per frame.
	 * Reader: the simulation thread calls {@link #read}. Both copy a small object under one lock, which a reader never
	 * holds for longer than the copy, so neither can see a half-written snapshot or wait noticeably.
	 */
	public static final class Exchange {
		private final LocalUiSnapshot latest = new LocalUiSnapshot();
		private boolean published;

		public synchronized void publish(final LocalUiSnapshot source) {
			latest.copyFrom(source);
			published = true;
		}

		/** Copies the latest snapshot into the target; false (target untouched) before the first publication. */
		public synchronized boolean read(final LocalUiSnapshot target) {
			if (!published) {
				return false;
			}
			target.copyFrom(latest);
			return true;
		}
	}
}
