package com.etheller.warsmash.viewer5;

import java.util.List;

/**
 * Decides which sound gets a voice when a device has few. Dialogue is never cut off for another sound, loops leave
 * room for one-shot sounds, and a dialogue may take the place of a loop.
 */
public final class VoiceSlots {
	/** A sound holding a voice, in the order the voices were taken (oldest first). */
	public interface Holder {
		boolean isLooping();

		boolean isDialogue();
	}

	/** The outcome of asking for a voice. */
	public static final class Grant<T extends Holder> {
		public final boolean granted;
		/** The holder that must give its voice up first, null when a voice was free. */
		public final T victim;

		private Grant(final boolean granted, final T victim) {
			this.granted = granted;
			this.victim = victim;
		}
	}

	private VoiceSlots() {
	}

	/**
	 * @param active the holders, oldest first
	 * @param max the voices there are
	 * @param oneShotReserve the voices loops leave to other sounds
	 */
	public static <T extends Holder> Grant<T> request(final List<T> active, final int max, final int oneShotReserve,
			final boolean looping, final boolean dialogue) {
		if (looping) {
			int loops = 0;
			for (final T holder : active) {
				if (holder.isLooping()) {
					loops++;
				}
			}
			if (loops >= (max - oneShotReserve)) {
				return new Grant<>(false, null);
			}
		}
		if (active.size() < max) {
			return new Grant<>(true, null);
		}
		for (final T holder : active) {
			if (!holder.isLooping() && !holder.isDialogue()) {
				return new Grant<>(true, holder);
			}
		}
		if (dialogue && !looping) {
			for (final T holder : active) {
				if (holder.isLooping()) {
					return new Grant<>(true, holder);
				}
			}
		}
		return new Grant<>(false, null);
	}
}
