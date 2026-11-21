package com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui;

import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.UiFrameState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;

/**
 * Where the interface panels get the {@link UiFrameState} they draw. Reader: the render thread.
 * <p>
 * When frames are presented (the simulation runs on its own thread, or its frames are published), the presented
 * frame's state is handed in with {@link #present} and is what {@link #current} returns. Without presented frames the
 * steps run inside the render frame, and this class runs the {@link UiFrameWriter} itself on the render thread: each
 * {@link #refresh} writes a state from the simulation as it is at that moment, at most once for each simulation step
 * and each change of the inputs the render side gives the writer.
 */
public final class UiFrameSource {
	/**
	 * Implemented by what owns the game being played (the viewer): the one source the panels read, which whatever runs
	 * the steps writes into as well. Null until the interface data it needs exists.
	 */
	public interface Holder {
		UiFrameSource uiFrameSource();
	}

	private final UiFrameWriter writer;
	private final SimFrame own = new SimFrame();
	private final UiFrameState presentedCopy = new UiFrameState();
	private boolean presentedAny;
	private boolean written;
	private int lastTick = -1;
	private int lastInputVersion = -1;
	private int inputVersion;

	public UiFrameSource(final UiFrameWriter writer) {
		this.writer = writer;
	}

	public UiFrameWriter writer() {
		return this.writer;
	}

	/**
	 * Hands in the state of the frame being presented (copied, so the frame may be released at once); from then on
	 * the state is no longer written here.
	 */
	public void present(final UiFrameState state) {
		this.presentedCopy.copyFrom(state);
		this.presentedAny = true;
	}

	public boolean isPresented() {
		return this.presentedAny;
	}

	/** The render side changed something the writer reads (selection, sub menu, hover, groups): write again. */
	public void inputsChanged() {
		this.inputVersion++;
	}

	/**
	 * Brings the state up to date when it is written here; a no-op while frames are presented. Call it where the
	 * panels are about to read, after the render side has set the writer's inputs.
	 *
	 * @param force write even when neither a step nor an input changed since the last write
	 */
	public void refresh(final CSimulation game, final boolean force) {
		if (this.presentedAny) {
			return;
		}
		final int tick = game.getGameTurnTick();
		if (!force && this.written && (tick == this.lastTick) && (this.inputVersion == this.lastInputVersion)) {
			return;
		}
		this.written = true;
		this.lastTick = tick;
		this.lastInputVersion = this.inputVersion;
		this.own.tick = tick;
		this.own.gameTimeMillis = Math.round(tick * 1000.0 * WarsmashConstants.SIMULATION_STEP_TIME);
		this.own.ui.clear();
		this.writer.write(game, this.own);
	}

	/** The state to draw: the presented frame's, else the last one written here. */
	public UiFrameState current() {
		return this.presentedAny ? this.presentedCopy : this.own.ui;
	}
}
