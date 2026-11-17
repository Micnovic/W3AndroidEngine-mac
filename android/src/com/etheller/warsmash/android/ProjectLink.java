package com.etheller.warsmash.android;

import com.etheller.warsmash.launcher.ProjectInfo;

/**
 * The project's name and address as the native library holds them, so a copy that only edits the Java code still names
 * the project. Without the library the shared constants stand in.
 */
final class ProjectLink {
	private static final String URL_START = "https://";
	private static final String name;
	private static final String url;

	static {
		String foundName = ProjectInfo.NAME;
		String foundUrl = ProjectInfo.SOURCE_URL;
		try {
			System.loadLibrary("w3aproject");
			final String nativeName = nativeName();
			final String nativeUrl = nativeUrl();
			if ((nativeName != null) && !nativeName.isEmpty()) {
				foundName = nativeName;
			}
			if ((nativeUrl != null) && nativeUrl.startsWith(URL_START)) {
				foundUrl = nativeUrl;
			}
		}
		catch (final UnsatisfiedLinkError | RuntimeException e) {
			// The library is missing or does not match this class; the constants name the project all the same.
		}
		name = foundName;
		url = foundUrl;
	}

	private ProjectLink() {
	}

	private static native String nativeName();

	private static native String nativeUrl();

	static String name() {
		return name;
	}

	static String url() {
		return url;
	}

	/** The address without its scheme, as the About page shows it. */
	static String shortUrl() {
		return url.startsWith(URL_START) ? url.substring(URL_START.length()) : url;
	}
}
