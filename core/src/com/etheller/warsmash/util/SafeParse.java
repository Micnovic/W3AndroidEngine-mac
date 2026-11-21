package com.etheller.warsmash.util;

/**
 * Reads numbers out of object data text. Custom maps leave fields empty or fill them with placeholders or garbage;
 * those read as zero like an unset field in the editor, and never throw.
 */
public final class SafeParse {
	private SafeParse() {
	}

	private static boolean blank(final String text) {
		return (text == null) || text.isEmpty() || "-".equals(text) || "_".equals(text);
	}

	public static float parseFloat(final String text) {
		return parseFloat(text, 0f);
	}

	public static float parseFloat(final String text, final float fallback) {
		if (blank(text)) {
			return fallback;
		}
		try {
			return Float.parseFloat(text.trim());
		}
		catch (final NumberFormatException e) {
			return fallback;
		}
	}

	public static int parseInt(final String text) {
		return parseInt(text, 0);
	}

	public static int parseInt(final String text, final int fallback) {
		if (blank(text)) {
			return fallback;
		}
		final String trimmed = text.trim();
		try {
			return Integer.parseInt(trimmed);
		}
		catch (final NumberFormatException e) {
			try {
				return (int) Double.parseDouble(trimmed);
			}
			catch (final NumberFormatException e2) {
				return fallback;
			}
		}
	}

	public static long parseLong(final String text) {
		if (blank(text)) {
			return 0L;
		}
		final String trimmed = text.trim();
		try {
			return Long.parseLong(trimmed);
		}
		catch (final NumberFormatException e) {
			try {
				return (long) Double.parseDouble(trimmed);
			}
			catch (final NumberFormatException e2) {
				return 0L;
			}
		}
	}
}
