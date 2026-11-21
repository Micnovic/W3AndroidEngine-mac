package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.function.ToIntFunction;

/**
 * How a game's checksum is made as it steps, for a replay and a LAN game. {@link #afterStep} is called after every
 * step; it says whether a checksum was finished in that step. The checksum is named by the step it was started at,
 * a multiple of the interval, and may be finished some steps later: a whole checksum of a big game costs a long step,
 * which a checksum spread over several steps does not. Calling it again in the same step changes nothing, so a
 * replay and a LAN game can share one.
 */
public interface ChecksumSteps {
	/** @return whether a checksum was finished by the step the game is at */
	boolean afterStep(CSimulation game);

	/** The step the finished checksum was started at. */
	int startTick();

	/** The finished checksum. */
	int value();

	/** A checksum made all at once at each multiple of the interval, by the given function. */
	static ChecksumSteps whole(final int interval, final ToIntFunction<CSimulation> checksum) {
		return new ChecksumSteps() {
			private int lastTick = -1;
			private boolean finished;
			private int value;

			@Override
			public boolean afterStep(final CSimulation game) {
				final int tick = game.getGameTurnTick();
				if (tick != this.lastTick) {
					this.lastTick = tick;
					this.finished = (tick % interval) == 0;
					if (this.finished) {
						this.value = checksum.applyAsInt(game);
					}
				}
				return this.finished;
			}

			@Override
			public int startTick() {
				return this.lastTick;
			}

			@Override
			public int value() {
				return this.value;
			}
		};
	}
}
