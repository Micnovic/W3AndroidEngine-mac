package com.etheller.warsmash.viewer5;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Runs the part of a scene's instance updates that touches nothing but the instance itself (animation pose, bones,
 * batch colors) on a few worker threads, while the scene's thread takes a share of it too. What reaches other
 * objects (emitting particles, effects, sounds) is done afterwards on the scene's thread in the order the instances
 * were updated.
 *
 * <code>warsmash.sceneupdate.threads</code> sets the number of workers (0 keeps everything on the calling thread);
 * by default it follows the performance cores of the device. Where there are no command line properties (Android),
 * a file named warsmash.sceneupdate in the application's local files holding the number does the same.
 */
public final class SceneUpdateWorkers {
	/** An instance whose update is split in a part safe to run on any thread, and a part that is not. */
	public interface Deferred {
		/** Reads and writes only this instance (and read-only model and camera data). */
		void updateIsolated();

		/** Runs on the scene's thread, in the order the instances were updated. */
		void updateShared();
	}

	/**
	 * Scratch values of the update code that used to be static: every thread that runs it has its own set.
	 */
	public static final class Heaps {
		public static final int MAX_KEY_LENGTH = 4;

		// Interpolation of timeline keys.
		public final float[][][] keys = new float[MAX_KEY_LENGTH + 1][][];
		public final float[][] values = new float[2][];
		public final float[][] inTans = new float[2][];
		public final float[][] outTans = new float[2][];

		// Node transformation.
		public final Vector3 cameraRay = new Vector3();
		public final Vector3 billboardAxis = new Vector3();
		public final Quaternion rotation = new Quaternion();
		public final Quaternion rotation2 = new Quaternion();
		public final Vector3 scaling = new Vector3();
		public final Quaternion inverseRotation = new Quaternion();
		public final Vector3 inverseScale = new Vector3();
		public final Matrix4 localMatrix = new Matrix4();
		public final Vector3 blend = new Vector3();
		public final Vector3 blendScale = new Vector3();
		public final Quaternion transformTmp1 = new Quaternion(0, 0, 0, 0);
		public final Quaternion transformTmp2 = new Quaternion(0, 0, 0, 0);

		// Reading the animation tracks of an instance.
		public final float[] visibility = new float[1];
		public final float[] translation = new float[3];
		public final float[] rotationValue = new float[4];
		public final float[] scale = new float[3];
		public final float[] color = new float[3];
		public final float[] alpha = new float[1];
		public final long[] textureId = new long[1];

		private Heaps() {
			for (int i = 1; i < this.keys.length; i++) {
				this.keys[i] = new float[][] { new float[i], new float[i], new float[i], new float[i] };
			}
		}
	}

	private static final Heaps MAIN_HEAPS = new Heaps();
	private static final String SETTING_FILE = "warsmash.sceneupdate";
	/** Fewer instances than this are never worth waking the workers for. */
	private static final int MIN_PARALLEL = Integer.getInteger("warsmash.sceneupdate.min", 24);
	/**
	 * Waking the workers and waiting for the last of them costs about a tenth of a millisecond on a phone, and the
	 * workers run on slower cores than the scene's thread: a batch whose serial update is cheaper than this runs
	 * faster on the scene's thread alone.
	 */
	private static final long MIN_PARALLEL_NANOS = Long.getLong("warsmash.sceneupdate.minus", 500L) * 1000L;
	private static final int MAX_WORKERS = 3;
	private static boolean configured;
	private static SceneUpdateWorkers instance;
	/** What updating one instance took on the scene's thread lately, to tell cheap batches from costly ones. */
	private static long nanosPerInstance;

	private final Worker[] workers;
	private final AtomicInteger next = new AtomicInteger();
	private final AtomicInteger remaining = new AtomicInteger();
	private volatile long generation;
	private List<? extends Deferred> job;
	private int jobSize;
	private int chunk;
	private volatile Throwable failure;

	private SceneUpdateWorkers(final int count) {
		this.workers = new Worker[count];
		for (int i = 0; i < count; i++) {
			this.workers[i] = new Worker(i);
			this.workers[i].start();
		}
	}

	private static void configure() {
		configured = true;
		String setting = System.getProperty("warsmash.sceneupdate.threads");
		if (setting == null) {
			setting = readSettingFile();
		}
		int count = -1;
		if (setting != null) {
			try {
				count = Integer.parseInt(setting.trim());
			}
			catch (final NumberFormatException e) {
				count = 0;
			}
		}
		if (count < 0) {
			count = Math.min(MAX_WORKERS, usableCores() - 1);
		}
		instance = count > 0 ? new SceneUpdateWorkers(Math.min(count, 8)) : null;
	}

	private static String readSettingFile() {
		try {
			if (Gdx.files != null) {
				final FileHandle file = Gdx.files.local(SETTING_FILE);
				if (file.exists()) {
					return file.readString();
				}
			}
		}
		catch (final RuntimeException e) {
			// No local files (headless tools): the default.
		}
		return null;
	}

	/**
	 * The cores worth giving work: on a phone with big and little cores the little ones would take a share and be
	 * waited for, so only cores near the fastest one's frequency count. Without readable frequencies, half of them.
	 */
	private static int usableCores() {
		final int cores = Runtime.getRuntime().availableProcessors();
		final long[] max = new long[cores];
		long top = 0;
		for (int i = 0; i < cores; i++) {
			max[i] = readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
			top = Math.max(top, max[i]);
		}
		if (top <= 0) {
			return Math.max(1, cores / 2);
		}
		int fast = 0;
		for (int i = 0; i < cores; i++) {
			if (max[i] * 4 >= top * 3) {
				fast++;
			}
		}
		return Math.max(1, fast);
	}

	private static long readLong(final String path) {
		try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
			final String line = reader.readLine();
			return line == null ? 0 : Long.parseLong(line.trim());
		}
		catch (final Exception e) {
			return 0;
		}
	}

	/** The scratch values of the calling thread. */
	public static Heaps heaps() {
		final Thread thread = Thread.currentThread();
		return (thread instanceof Worker) ? ((Worker) thread).heaps : MAIN_HEAPS;
	}

	/** The number of worker threads, not counting the caller that takes a share too. */
	public static int workerCount() {
		if (!configured) {
			configure();
		}
		return instance == null ? 0 : instance.workers.length;
	}

	/**
	 * Runs the isolated part of every instance of the list, on the workers and the caller, then the shared part of
	 * each in the order of the list on the caller.
	 */
	public static void run(final List<? extends Deferred> list) {
		if (!configured) {
			configure();
		}
		final int size = list.size();
		final SceneUpdateWorkers pool = instance;
		if ((pool == null) || (size < MIN_PARALLEL) || ((size * nanosPerInstance) < MIN_PARALLEL_NANOS)) {
			final long start = System.nanoTime();
			for (int i = 0; i < size; i++) {
				list.get(i).updateIsolated();
			}
			measured(System.nanoTime() - start, size);
		}
		else {
			pool.runParallel(list, size);
		}
		for (int i = 0; i < size; i++) {
			list.get(i).updateShared();
		}
	}

	private static void measured(final long nanos, final int count) {
		if (count > 0) {
			final long sample = nanos / count;
			nanosPerInstance = nanosPerInstance == 0 ? sample : ((nanosPerInstance * 7) + sample) >> 3;
		}
	}

	private void runParallel(final List<? extends Deferred> list, final int size) {
		final Worker[] workers = this.workers;
		final int participants = workers.length + 1;
		this.job = list;
		this.jobSize = size;
		// Small chunks: a worker on a slower core must not hold the scene's thread up with a large last share.
		this.chunk = Math.max(1, Math.min(8, size / (participants * 8)));
		this.next.set(0);
		this.failure = null;
		this.remaining.set(workers.length);
		this.generation++;
		for (final Worker worker : workers) {
			if (worker.parked) {
				LockSupport.unpark(worker);
			}
		}
		final long start = System.nanoTime();
		final int mine = work();
		measured(System.nanoTime() - start, mine);
		// The workers' last chunks take microseconds; parking for them would cost more than waiting.
		int spins = 0;
		while (this.remaining.get() != 0) {
			if (++spins > 1000) {
				Thread.yield();
			}
		}
		this.job = null;
		final Throwable thrown = this.failure;
		if (thrown != null) {
			this.failure = null;
			if (thrown instanceof RuntimeException) {
				throw (RuntimeException) thrown;
			}
			if (thrown instanceof Error) {
				throw (Error) thrown;
			}
			throw new IllegalStateException(thrown);
		}
	}

	private int work() {
		final List<? extends Deferred> list = this.job;
		final int size = this.jobSize;
		final int step = this.chunk;
		int done = 0;
		while (true) {
			final int from = this.next.getAndAdd(step);
			if (from >= size) {
				return done;
			}
			final int to = Math.min(size, from + step);
			for (int i = from; i < to; i++) {
				try {
					list.get(i).updateIsolated();
				}
				catch (final Throwable t) {
					if (this.failure == null) {
						this.failure = t;
					}
				}
			}
			done += to - from;
		}
	}

	private final class Worker extends Thread {
		private final Heaps heaps = new Heaps();
		private volatile boolean parked;

		private Worker(final int index) {
			super("scene-update-" + index);
			setDaemon(true);
		}

		@Override
		public void run() {
			long seen = 0;
			while (true) {
				long current;
				while ((current = SceneUpdateWorkers.this.generation) == seen) {
					this.parked = true;
					// Checked again after saying so: a job given in between is either seen here or unparks this.
					if (SceneUpdateWorkers.this.generation == seen) {
						LockSupport.park(this);
					}
					this.parked = false;
				}
				seen = current;
				try {
					work();
				}
				finally {
					SceneUpdateWorkers.this.remaining.decrementAndGet();
				}
			}
		}
	}
}
