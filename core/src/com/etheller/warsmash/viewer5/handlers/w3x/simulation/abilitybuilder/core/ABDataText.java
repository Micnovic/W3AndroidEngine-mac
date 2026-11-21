package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core;

import com.etheller.warsmash.util.SafeParse;

/**
 * Reads ability data fields kept as text. A field the object data does not define (a custom or older map ability)
 * is empty, and reads as zero like an unset field in the original.
 */
public final class ABDataText {
	private ABDataText() {
	}

	public static float parseFloat(final String text) {
		return SafeParse.parseFloat(text);
	}

	public static int parseInt(final String text) {
		return SafeParse.parseInt(text);
	}

	public static long parseLong(final String text) {
		return SafeParse.parseLong(text);
	}
}
