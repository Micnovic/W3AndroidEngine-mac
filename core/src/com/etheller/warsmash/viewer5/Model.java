package com.etheller.warsmash.viewer5;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.etheller.warsmash.viewer5.handlers.ModelHandler;

public abstract class Model<HANDLER extends ModelHandler> extends HandlerResource<HANDLER> {
	public final Bounds bounds;
	public List<ModelInstance> preloadedInstances;
	private boolean pending;
	/** Where the file of a pending model is read from, until a helper is started on it. */
	String lazySource;
	/** Whether the build of a pending model is done and its waiting instances are being loaded. */
	boolean built;
	int waitingLoaded;
	/** Whether a step of the model's build is running now. */
	boolean stepping;
	long buildNanos;

	public Model(final HANDLER handler, final ModelViewer viewer, final String extension, final PathSolver pathSolver,
			final String fetchUrl) {
		super(viewer, extension, pathSolver, fetchUrl, handler);
		this.bounds = new Bounds();
		this.preloadedInstances = new ArrayList<>();
	}

	protected abstract ModelInstance createInstance(int type);

	public ModelInstance addInstance() {
		return addInstance(0);
	}

	/** Finishes a model whose file is still being read first, see {@link #addWaitingInstance()}. */
	public ModelInstance addInstance(final int type) {
		finishPending();
		return addUnfinishedInstance(type);
	}

	/**
	 * An instance that may be made before the model's file is read: until then it shows nothing, and it fills in when
	 * the model is built (see {@link ModelViewer#setDeferModelLoads}). Only an owner that keeps no part of the model's
	 * contents from before then may use it.
	 */
	public ModelInstance addWaitingInstance() {
		return addUnfinishedInstance(0);
	}

	private ModelInstance addUnfinishedInstance(final int type) {
		final ModelInstance instance = createInstance(type);

		if (this.ok) {
			instance.load();
		}
		else {
			this.preloadedInstances.add(instance);
			if (this.pending) {
				// also one asked for by what the model's own build makes, which cannot wait for the build: it loads
				// with the others, and no scene may show it before
				instance.setWaiting(true);
			}
		}

		return instance;
	}

	/**
	 * Whether the model's file is being read and parsed on a helper thread, or the model is built a few units at a time
	 * or has instances still to load: {@link #finishPending()} completes all of that.
	 */
	public boolean isPending() {
		return this.pending;
	}

	/** Builds a pending model now, as far as it is not built, waiting for its helper if need be; nothing for any other model. */
	public final void finishPending() {
		if (this.pending) {
			this.viewer.finishPendingModel(this);
		}
	}

	void setPending(final boolean pending) {
		this.pending = pending;
	}

	/**
	 * Starts reading and parsing the file on a helper thread instead of loading it now.
	 *
	 * @return false when the model is to be loaded from the stream now as usual; the stream is then untouched
	 */
	protected boolean prepareLater(final InputStream src, final ExecutorService executor) {
		return false;
	}

	/** Whether the helper of a pending model is done, so that building it does not wait. */
	protected boolean isPrepared() {
		return true;
	}

	/**
	 * Builds the model from what its helper made, on the game thread, a unit of work at a time until the time is up
	 * (at least one unit); sets ok as loading the file would when it is done.
	 *
	 * @return true when the model is built or failed, false when the next call goes on
	 */
	protected boolean buildPrepared(final long endNanos) {
		return true;
	}

	/**
	 * A model that is still being built and that this one's instances need as they load, or null. A pending model's
	 * waiting instances are loaded once it has none.
	 */
	protected Model<?> firstPendingRequired() {
		return null;
	}

	/** Whether this model's build, or the loading of its waiting instances, is running now. */
	protected final boolean isStepping() {
		return this.stepping;
	}

	/** Frees what was made ahead for a model that is not built after all. */
	protected void discardPrepared() {
	}
}
