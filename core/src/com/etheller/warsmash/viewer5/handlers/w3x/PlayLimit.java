package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.ArrayList;
import java.util.List;

/**
 * Caps how many plays of one sound file sound at once, so that a crowd of units doing the same thing (a battle's
 * hits, a squad's footsteps) cannot take every voice of the sound device from other sounds.
 */
final class PlayLimit {
	private final List<Long> ends = new ArrayList<>();

	/**
	 * @param nowMillis    the time now
	 * @param lengthMillis how long the play lasts; 0 when not known, which is not limited or counted
	 * @param max          how many plays of the file may sound at once
	 * @return whether the play may start
	 */
	boolean tryStart(final long nowMillis, final long lengthMillis, final int max) {
		if (lengthMillis <= 0) {
			return true;
		}
		for (int i = this.ends.size() - 1; i >= 0; i--) {
			if (this.ends.get(i) <= nowMillis) {
				this.ends.remove(i);
			}
		}
		if (this.ends.size() >= max) {
			return false;
		}
		this.ends.add(nowMillis + lengthMillis);
		return true;
	}
}
