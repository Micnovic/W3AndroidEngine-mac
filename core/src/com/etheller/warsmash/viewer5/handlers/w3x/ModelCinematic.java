package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.List;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.RenderMathUtils;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.Camera;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.Sequence;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;

/**
 * A model made as a whole scene (its actors, props, camera and sounds) that plays shot after shot, each shot one of
 * its sequences. Both variants put the model in the world and turn the game's camera into the model's, so the map
 * shows behind the actors: the in-game variant at the place it is given, with the interface and the world as they
 * are; PlayModelCinematic where the game camera is looking, with only the world drawn, and the game again when the
 * last shot is over. The map goes on meanwhile, so the trigger that asked for it can wait for it.
 * <p>
 * A campaign entry that is a model rather than a map plays it on a scene of the menu's own, full screen, with the
 * model at the origin and nothing of a map behind it.
 */
public final class ModelCinematic {
	private static final float MODEL_FOV_SCALE = 0.75f;
	private static final float DEFAULT_FOV = 70f;
	private static final float DEFAULT_NEAR = 16f;
	private static final float DEFAULT_FAR = 20000f;
	private static final float STEP_MILLISECONDS = WarsmashConstants.SIMULATION_STEP_TIME * 1000f;

	/**
	 * Where the cinematic is in time, counted in game steps from the step that asked for it. What the scripts ask is
	 * read from this alone, so it comes out the same on every machine, with or without a picture, and whether or not
	 * the player skips what is drawn.
	 */
	public static final class Timeline {
		private final long[] shotEnds;
		private final int startTick;

		private Timeline(final MdxModel model, final int startTick) {
			final List<Sequence> sequences = model.getSequences();
			final long origin = sequences.get(0).getInterval()[0];
			this.shotEnds = new long[sequences.size()];
			for (int i = 0; i < this.shotEnds.length; i++) {
				this.shotEnds[i] = sequences.get(i).getInterval()[1] - origin;
			}
			this.startTick = startTick;
		}

		public int getShotCount() {
			return this.shotEnds.length;
		}

		private float elapsed(final int tick) {
			return Math.max(0, tick - this.startTick) * STEP_MILLISECONDS;
		}

		/** @return the shot now playing, counted from 1, or 0 when the cinematic is over */
		public int getCurrentShot(final int tick) {
			final float elapsed = elapsed(tick);
			for (int i = 0; i < this.shotEnds.length; i++) {
				if (elapsed < this.shotEnds[i]) {
					return i + 1;
				}
			}
			return 0;
		}

		/** @return the seconds until the last shot ends */
		public float getRemainingSeconds(final int tick) {
			return Math.max(0f, (this.shotEnds[this.shotEnds.length - 1] - elapsed(tick)) / 1000f);
		}
	}

	private final Scene scene;
	private final MdxModel model;
	private final Timeline timeline;
	private final boolean inGame;
	private final MdxComplexInstance instance;
	private final com.etheller.warsmash.viewer5.handlers.mdx.Camera[] shotCameras;
	private final float[] positionOffset = new float[3];
	private final float[] targetOffset = new float[3];
	private final float[] roll = new float[3];
	private final Vector3 eye = new Vector3();
	private final Vector3 target = new Vector3();
	private final Vector3 up = new Vector3();
	private final Vector3 right = new Vector3();
	private final Vector3 forward = new Vector3();
	private final Vector3 worldUp = new Vector3(0, 0, 1);
	private final Vector3 origin = new Vector3();
	private final Quaternion facing = new Quaternion();
	private int shot;
	private boolean started;
	private boolean finished;
	private boolean skipped;

	private ModelCinematic(final Scene scene, final MdxModel model, final int startTick, final boolean inGame,
			final float x, final float y, final float z, final float rotationDegrees) {
		this.scene = scene;
		this.model = model;
		this.inGame = inGame;
		this.timeline = new Timeline(model, startTick);
		this.instance = (MdxComplexInstance) model.addInstance();
		this.instance.setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP);
		this.instance.setSequence(0);
		this.origin.set(x, y, z);
		this.facing.setFromAxisRad(RenderMathUtils.VEC3_UNIT_Z, (float) Math.toRadians(rotationDegrees));
		this.instance.setLocation(this.origin.x, this.origin.y, this.origin.z);
		this.instance.rotate(this.facing);
		this.instance.setScene(scene);
		this.shotCameras = shotCameras(model);
	}

	/** Where the game camera's view axis meets the ground. */
	private static void lookedAtGround(final War3MapViewer viewer, final Vector3 result) {
		final Camera camera = viewer.worldScene.camera;
		final Vector3 forward = new Vector3(camera.directionZ).scl(-1);
		final float eyeX = camera.location.x;
		final float eyeY = camera.location.y;
		final float eyeZ = camera.location.z;
		float x = eyeX;
		float y = eyeY;
		float z = viewer.terrain.getGroundHeight(x, y);
		if (forward.z < -0.01f) {
			for (int i = 0; i < 3; i++) {
				final float distance = (eyeZ - z) / -forward.z;
				x = eyeX + (forward.x * distance);
				y = eyeY + (forward.y * distance);
				z = viewer.terrain.getGroundHeight(x, y);
			}
		}
		result.set(x, y, z);
	}

	/**
	 * Each shot has a camera of its own: the camera named after the shot's number (Shot 020 has CAM_020), or else the
	 * camera in the shot's place in the list when there are as many as shots, or else the first.
	 */
	private static com.etheller.warsmash.viewer5.handlers.mdx.Camera[] shotCameras(final MdxModel model) {
		final List<Sequence> sequences = model.getSequences();
		final List<com.etheller.warsmash.viewer5.handlers.mdx.Camera> cameras = model.getCameras();
		final com.etheller.warsmash.viewer5.handlers.mdx.Camera[] result = new com.etheller.warsmash.viewer5.handlers.mdx.Camera[sequences.size()];
		if (cameras.isEmpty()) {
			return result;
		}
		for (int i = 0; i < result.length; i++) {
			final String number = trailingNumber(sequences.get(i).getName());
			com.etheller.warsmash.viewer5.handlers.mdx.Camera found = null;
			for (final com.etheller.warsmash.viewer5.handlers.mdx.Camera camera : cameras) {
				if (!number.isEmpty() && number.equals(trailingNumber(camera.name))) {
					found = camera;
					break;
				}
			}
			if (found == null) {
				found = cameras.size() == result.length ? cameras.get(i) : cameras.get(0);
			}
			result[i] = found;
		}
		return result;
	}

	private static String trailingNumber(final String name) {
		int start = name == null ? 0 : name.length();
		while ((start > 0) && Character.isDigit(name.charAt(start - 1))) {
			start--;
		}
		return name == null ? "" : name.substring(start);
	}

	/** @return the model of a cinematic when it is in the game data and has shots to play, else null */
	public static MdxModel load(final War3MapViewer viewer, final String path) {
		return load(viewer.dataSource, viewer, viewer.mapPathSolver, viewer.solverParams, path);
	}

	/** @return the model of a cinematic as {@link #load(War3MapViewer, String)} finds it, in any viewer */
	public static MdxModel load(final DataSource data, final ModelViewer viewer, final PathSolver pathSolver,
			final Object solverParams, final String path) {
		if ((path == null) || path.isEmpty()) {
			return null;
		}
		final String mdx = War3MapViewer.mdx(path);
		if (!data.has(mdx) && !data.has(War3MapViewer.mdl(mdx))) {
			Log.warn("The cinematic model " + path + " is not in the game data");
			return null;
		}
		final MdxModel model = War3MapViewer.loadModelMdx(data, viewer, path, pathSolver, solverParams);
		if ((model == null) || model.getSequences().isEmpty()) {
			Log.warn("The cinematic model " + path + " cannot play");
			return null;
		}
		return model;
	}

	/** @return the cinematic now playing, or null when the model is not there */
	public static ModelCinematic play(final War3MapViewer viewer, final String path, final boolean inGame,
			final float x, final float y, final float z, final float rotationDegrees) {
		final MdxModel model = load(viewer, path);
		if (model == null) {
			return null;
		}
		final Vector3 origin = new Vector3(x, y, z);
		if (!inGame) {
			lookedAtGround(viewer, origin);
		}
		return new ModelCinematic(viewer.worldScene, model, viewer.simulation.getGameTurnTick(), inGame, origin.x,
				origin.y, origin.z, rotationDegrees);
	}

	/**
	 * @return a cinematic that plays full screen on a scene with nothing else on it, at the origin, turned about the Z
	 *         axis by no degrees
	 */
	public static ModelCinematic playOnScene(final Scene scene, final MdxModel model) {
		return new ModelCinematic(scene, model, 0, false, 0, 0, 0, 0);
	}

	public MdxModel getModel() {
		return this.model;
	}

	public Timeline getTimeline() {
		return this.timeline;
	}

	public boolean isInGame() {
		return this.inGame;
	}

	public void skip() {
		this.skipped = true;
	}

	/** @return whether the last shot has played to its end or the player skipped it */
	public boolean isDone() {
		return this.skipped || this.finished;
	}

	/**
	 * Moves on to the next shot when one has ended. The model plays by the time of the picture; the first frame after
	 * loading would otherwise start the first shot a few seconds in, so the picture begins again at the very start.
	 */
	private void advance() {
		if (!this.started) {
			this.started = true;
			this.instance.setSequence(0);
			return;
		}
		if (!this.instance.sequenceEnded) {
			return;
		}
		if ((this.shot + 1) < this.model.getSequences().size()) {
			this.shot++;
			this.instance.setSequence(this.shot);
		}
		else {
			this.finished = true;
		}
	}

	/** The game's camera shows what the model's camera shows, in the place the model is put. */
	public void placeWorldCamera() {
		advance();
		final Camera camera = this.scene.camera;
		placeCamera(camera);
		camera.update();
	}

	private void placeCamera(final Camera camera) {
		if (this.shotCameras[this.shot] == null) {
			if (this.inGame) {
				return;
			}
			final float distance = Math.max(500f, this.model.bounds.r * 2f);
			camera.perspective(DEFAULT_FOV, camera.getAspect(), DEFAULT_NEAR, DEFAULT_FAR);
			this.eye.set(0, -distance, distance * 0.5f).add(this.origin);
			this.target.set(this.origin);
			camera.moveToAndFace(this.eye, this.target, this.worldUp);
			return;
		}
		final com.etheller.warsmash.viewer5.handlers.mdx.Camera modelCamera = this.shotCameras[this.shot];
		final int sequence = this.instance.sequence;
		final int frame = this.instance.frame;
		final int counter = this.instance.counter;
		modelCamera.getPositionTranslation(this.positionOffset, sequence, frame, counter);
		modelCamera.getTargetTranslation(this.targetOffset, sequence, frame, counter);
		modelCamera.getRotation(this.roll, sequence, frame, counter);
		this.eye.set(modelCamera.position[0] + this.positionOffset[0],
				modelCamera.position[1] + this.positionOffset[1],
				modelCamera.position[2] + this.positionOffset[2]);
		this.target.set(modelCamera.targetPosition[0] + this.targetOffset[0],
				modelCamera.targetPosition[1] + this.targetOffset[1],
				modelCamera.targetPosition[2] + this.targetOffset[2]);
		this.eye.mul(this.facing).add(this.origin);
		this.target.mul(this.facing).add(this.origin);
		camera.perspective(modelCamera.fieldOfView * MODEL_FOV_SCALE, camera.getAspect(),
				Math.max(1f, modelCamera.nearClippingPlane),
				Math.max(modelCamera.nearClippingPlane + 1f, modelCamera.farClippingPlane));
		if (this.roll[0] == 0) {
			camera.moveToAndFace(this.eye, this.target, this.worldUp);
			return;
		}
		this.forward.set(this.target).sub(this.eye).nor();
		this.right.set(this.forward).crs(this.worldUp);
		if (this.right.len2() < 1e-8f) {
			this.right.set(1, 0, 0);
		}
		this.right.nor();
		this.up.set(this.right).crs(this.forward).nor();
		final float cos = (float) Math.cos(this.roll[0]);
		final float sin = (float) Math.sin(this.roll[0]);
		// The view turns about its own axis: the up direction swings towards the right.
		final float upX = (this.up.x * cos) + (this.right.x * sin);
		final float upY = (this.up.y * cos) + (this.right.y * sin);
		final float upZ = (this.up.z * cos) + (this.right.z * sin);
		this.up.set(upX, upY, upZ);
		camera.moveToAndFace(this.eye, this.target, this.up);
	}

	public void dispose() {
		this.instance.detach();
	}
}
