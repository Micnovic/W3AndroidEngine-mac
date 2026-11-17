package com.etheller.warsmash.launcher;

import com.etheller.warsmash.BuildInfo;

/**
 * Whether an Android build carries the project's own signature. The certificate's fingerprint is public and sits in
 * code on purpose: a copy signed with any other key says so on the launcher's home screen and in About, however it was
 * built.
 */
public final class BuildOrigin {
	/** The SHA-256 of the certificate the project's releases are signed with. */
	public static final String OFFICIAL_CERT_SHA256 = "c1133934fbf93ac25abd6f3a2ee144a577c59386435259991a33a2da7be273e2";
	public static final String UNOFFICIAL = "Not an official release";
	/** The longer notice Home shows under the menu, with where the official build is. */
	public static final String UNOFFICIAL_NOTICE = UNOFFICIAL + " – get it from " + ProjectInfo.SOURCE_NAME;
	/** A debug build is always signed with a local debug key, so a mismatch would say nothing; it names the kind of build instead. */
	public static final String DEBUG = "Debug build";

	private BuildOrigin() {
	}

	/** The label to show, or an empty text for a build signed with the project's key. */
	public static String label(final boolean debuggable, final String certSha256) {
		if (debuggable) {
			return DEBUG;
		}
		final String signer = BuildInfo.normalizeFingerprint(certSha256);
		return !signer.isEmpty() && signer.equals(OFFICIAL_CERT_SHA256) ? "" : UNOFFICIAL;
	}
}
