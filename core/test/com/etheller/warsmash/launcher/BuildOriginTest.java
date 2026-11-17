package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class BuildOriginTest {
	@Test
	void theProjectsKeyShowsNoLabel() {
		assertEquals("", BuildOrigin.label(false, BuildOrigin.OFFICIAL_CERT_SHA256));
		assertEquals("", BuildOrigin.label(false, BuildOrigin.OFFICIAL_CERT_SHA256.toUpperCase(Locale.ROOT)));
	}

	@Test
	void anyOtherSignatureIsUnofficial() {
		assertEquals(BuildOrigin.UNOFFICIAL, BuildOrigin.label(false, "00" + BuildOrigin.OFFICIAL_CERT_SHA256.substring(2)));
		assertEquals(BuildOrigin.UNOFFICIAL, BuildOrigin.label(false, ""));
		assertEquals(BuildOrigin.UNOFFICIAL, BuildOrigin.label(false, null));
	}

	@Test
	void aDebugBuildNamesItselfWhatEverItIsSignedWith() {
		assertEquals(BuildOrigin.DEBUG, BuildOrigin.label(true, BuildOrigin.OFFICIAL_CERT_SHA256));
		assertEquals(BuildOrigin.DEBUG, BuildOrigin.label(true, ""));
	}
}
