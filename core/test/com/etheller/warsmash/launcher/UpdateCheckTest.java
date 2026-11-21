package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Version comparison and the release lookup's reading of GitHub's answer. */
class UpdateCheckTest {
	private static final String RELEASE = "{\"tag_name\":\"v0.3.6\",\"html_url\":\"https://github.com/x/y/releases/tag/v0.3.6\","
			+ "\"draft\":false,\"prerelease\":false,\"assets\":[{\"name\":\"notes.txt\",\"size\":5},"
			+ "{\"name\":\"Warsmash-v0.3.6.apk\",\"size\":6697397,"
			+ "\"browser_download_url\":\"https://github.com/x/y/releases/download/v0.3.6/Warsmash-v0.3.6.apk\"}]}";

	@Test
	void parsesVersions() {
		assertArrayEquals(new int[] { 0, 3, 6 }, UpdateCheck.parse("v0.3.6"));
		assertArrayEquals(new int[] { 0, 3, 6 }, UpdateCheck.parse("0.3.6-debug"));
		assertArrayEquals(new int[] { 0, 3, 0 }, UpdateCheck.parse("0.3"));
		assertArrayEquals(new int[] { 0, 10, 2 }, UpdateCheck.parse(" V0.10.2+build "));
		assertNull(UpdateCheck.parse("dev"));
		assertNull(UpdateCheck.parse(null));
	}

	@Test
	void comparesNumerically() {
		assertTrue(UpdateCheck.compare("v0.3.10", "0.3.9") > 0);
		assertTrue(UpdateCheck.compare("v0.3.6", "0.3.8-debug") < 0);
		assertTrue(UpdateCheck.compare("v0.4.0", "0.3.99") > 0);
		assertEquals(0, UpdateCheck.compare("v0.3.6", "0.3.6-release"));
		assertEquals(0, UpdateCheck.compare("v0.3.6", "dev"));
	}

	@Test
	void readsRelease() {
		final UpdateCheck.Release release = UpdateCheck.parseRelease(RELEASE);
		assertNotNull(release);
		assertEquals("v0.3.6", release.tag);
		assertEquals("Warsmash-v0.3.6.apk", release.apkName);
		assertEquals(6697397, release.apkSize);
		assertTrue(release.hasApk());
		assertNull(UpdateCheck.parseRelease(RELEASE.replace("\"draft\":false", "\"draft\":true")));
		assertNull(UpdateCheck.parseRelease("{\"message\":\"rate limit\"}"));
		assertFalse(UpdateCheck.parseRelease(RELEASE.replace(".apk", ".zip")).hasApk());
	}

	@Test
	void offersOnlyNewerUnskipped() {
		final UpdateCheck.Release release = UpdateCheck.parseRelease(RELEASE);
		assertTrue(UpdateCheck.offers("0.3.5", release, null));
		assertTrue(UpdateCheck.offers("0.3.5", release, "v0.3.4"));
		assertFalse(UpdateCheck.offers("0.3.5", release, "v0.3.6"));
		assertFalse(UpdateCheck.offers("0.3.6", release, null));
		assertFalse(UpdateCheck.offers("0.3.8", release, null));
		assertFalse(UpdateCheck.offers("0.3.5", null, null));
	}

	@Test
	void versionCodeIsMinorTimesHundredPlusPatch() {
		assertEquals(311, UpdateCheck.versionCode("v0.3.11"));
		assertEquals(306, UpdateCheck.versionCode("0.3.6-debug"));
		assertEquals(400, UpdateCheck.versionCode("0.4"));
		assertEquals(10000, UpdateCheck.versionCode("1.0.0"));
		assertEquals(0, UpdateCheck.versionCode("dev"));
		assertEquals(0, UpdateCheck.versionCode(null));
		assertTrue(UpdateCheck.versionCode("0.3.10") > UpdateCheck.versionCode("0.3.9"));
		assertTrue(UpdateCheck.versionCode("0.4.0") > UpdateCheck.versionCode("0.3.99"));
	}

	@Test
	void officialBuildIsOfferedOnlyAHigherVersionCode() {
		final UpdateCheck.Release release = UpdateCheck.parseRelease(RELEASE);
		assertTrue(UpdateCheck.offers(305, release, null, true));
		assertFalse(UpdateCheck.offers(306, release, null, true));
		assertFalse(UpdateCheck.offers(311, release, null, true));
		assertFalse(UpdateCheck.offers(0, release, null, true));
		assertFalse(UpdateCheck.offers(305, null, null, true));
		assertFalse(UpdateCheck.offers(305, release, "v0.3.6", true));
		assertTrue(UpdateCheck.offers(305, release, "v0.3.5", true));
		assertTrue(UpdateCheck.offers("0.3.5", release, null, true));
		assertFalse(UpdateCheck.offers("0.3.6", release, null, true));
	}

	@Test
	void olderReleaseIsNeverOfferedWhateverTheSigningKey() {
		final UpdateCheck.Release older = UpdateCheck.parseRelease(RELEASE.replace("v0.3.6", "v0.3.13"));
		assertEquals("v0.3.13", older.tag);
		for (final boolean official : new boolean[] { true, false }) {
			assertFalse(UpdateCheck.offers("0.3.26", older, null, official));
			assertFalse(UpdateCheck.offers("0.3.48", older, null, official));
			assertFalse(UpdateCheck.offers(UpdateCheck.versionCode("0.3.48"), older, null, official));
		}
		assertTrue(UpdateCheck.compare("0.3.48", "0.3.13") > 0);
		assertTrue(UpdateCheck.compare("0.3.13", "0.3.9") > 0);
		assertTrue(UpdateCheck.offers("0.3.9", older, null, true));
	}

	@Test
	void buildOfTheReleasesOwnVersionIsNeverOfferedItself() {
		final UpdateCheck.Release release = UpdateCheck.parseRelease(RELEASE);
		for (final boolean official : new boolean[] { true, false }) {
			assertFalse(UpdateCheck.offers("0.3.6", release, null, official));
			assertFalse(UpdateCheck.offers("0.3.6-debug", release, null, official));
			assertFalse(UpdateCheck.offers(306, release, "", official));
		}
	}

	@Test
	void unofficialBuildIsOfferedOnlyAHigherReleaseWithAnApk() {
		final UpdateCheck.Release release = UpdateCheck.parseRelease(RELEASE);
		assertTrue(UpdateCheck.offers("0.3.5", release, null, false));
		assertTrue(UpdateCheck.offers(305, release, "", false));
		assertTrue(UpdateCheck.offers(0, release, null, false));
		assertFalse(UpdateCheck.offers("0.9.0", release, null, false));
		assertFalse(UpdateCheck.offers(307, release, null, false));
		assertFalse(UpdateCheck.offers("0.3.5", release, "v0.3.6", false));
		assertFalse(UpdateCheck.offers("0.3.5", null, null, false));
		assertFalse(UpdateCheck.offers("0.3.5", UpdateCheck.parseRelease(RELEASE.replace(".apk", ".zip")), null, false));
	}

	@Test
	void parsesHotfixSuffix() {
		assertArrayEquals(new int[] { 0, 3, 72 }, UpdateCheck.parse("v0.3.72-hotfix2"));
		assertEquals(372, UpdateCheck.versionCode("v0.3.72-hotfix2"));
		assertEquals(2, UpdateCheck.hotfix("v0.3.72-hotfix2"));
		assertEquals(1, UpdateCheck.hotfix("0.3.72-hotfix"));
		assertEquals(1, UpdateCheck.hotfix("0.3.72-hotfix1"));
		assertEquals(12, UpdateCheck.hotfix("0.3.72-HotFix12"));
		assertEquals(0, UpdateCheck.hotfix("0.3.72"));
		assertEquals(0, UpdateCheck.hotfix("0.3.72-rc1"));
		assertEquals(0, UpdateCheck.hotfix("0.3.72-beta"));
		assertEquals(0, UpdateCheck.hotfix("0.3.72-hotfixx"));
		assertEquals(0, UpdateCheck.hotfix("dev"));
		assertEquals(0, UpdateCheck.hotfix(null));
	}

	@Test
	void hotfixRanksAboveItsBaseAndBelowTheNextVersion() {
		assertTrue(UpdateCheck.compare("0.3.72-hotfix1", "0.3.72") > 0);
		assertTrue(UpdateCheck.compare("0.3.72", "0.3.72-hotfix1") < 0);
		assertTrue(UpdateCheck.compare("v0.3.72-hotfix2", "0.3.72-hotfix1") > 0);
		assertTrue(UpdateCheck.compare("0.3.72-hotfix", "0.3.72") > 0);
		assertEquals(0, UpdateCheck.compare("0.3.72-hotfix", "0.3.72-hotfix1"));
		assertTrue(UpdateCheck.compare("0.3.73", "0.3.72-hotfix2") > 0);
		assertTrue(UpdateCheck.compare("0.3.72-hotfix9", "0.3.73") < 0);
		assertEquals(0, UpdateCheck.compare("0.3.72-rc1", "0.3.72"));
	}

	@Test
	void hotfixIsOfferedToItsBaseOnly() {
		final UpdateCheck.Release hotfix = UpdateCheck.parseRelease(RELEASE.replace("v0.3.6", "v0.3.72-hotfix2"));
		assertEquals("v0.3.72-hotfix2", hotfix.tag);
		for (final boolean official : new boolean[] { true, false }) {
			assertTrue(UpdateCheck.offers("0.3.71", hotfix, null, official));
			assertTrue(UpdateCheck.offers("0.3.72", hotfix, null, official));
			assertTrue(UpdateCheck.offers("0.3.72-hotfix1", hotfix, null, official));
			assertFalse(UpdateCheck.offers("0.3.72-hotfix2", hotfix, null, official));
			assertFalse(UpdateCheck.offers("0.3.72-hotfix3", hotfix, null, official));
			assertFalse(UpdateCheck.offers("0.3.73", hotfix, null, official));
			assertTrue(UpdateCheck.offers("0.3.72", 372, hotfix, null, official));
			assertFalse(UpdateCheck.offers("0.3.72-hotfix2", 372, hotfix, null, official));
			assertFalse(UpdateCheck.offers("0.3.72-hotfix2-debug", 372, hotfix, null, official));
		}
		assertTrue(UpdateCheck.offers("0.3.72", hotfix, null));
		assertFalse(UpdateCheck.offers("0.3.72", hotfix, "v0.3.72-hotfix2"));
		assertTrue(UpdateCheck.offers("0.3.72", hotfix, "v0.3.72"));
		assertTrue(UpdateCheck.offers("dev", 371, hotfix, null, true));
		assertFalse(UpdateCheck.offers("dev", 0, hotfix, null, true));
		assertFalse(UpdateCheck.offers("dev", 372, UpdateCheck.parseRelease(RELEASE.replace("v0.3.6", "v0.3.72")), null,
				true));
	}

	@Test
	void nextVersionIsOfferedToAHotfixBuild() {
		final UpdateCheck.Release next = UpdateCheck.parseRelease(RELEASE.replace("v0.3.6", "v0.3.73"));
		assertTrue(UpdateCheck.offers("0.3.72-hotfix2", next, null, true));
		assertTrue(UpdateCheck.offers("0.3.72-hotfix2", next, null, false));
		final UpdateCheck.Release plain = UpdateCheck.parseRelease(RELEASE.replace("v0.3.6", "v0.3.72"));
		assertFalse(UpdateCheck.offers("0.3.72-hotfix2", plain, null, true));
		assertFalse(UpdateCheck.offers("0.3.72-hotfix2", plain, null, false));
	}

	@Test
	void unknownSuffixIsNotOfferedOverAPlainRelease() {
		final UpdateCheck.Release plain = UpdateCheck.parseRelease(RELEASE.replace("v0.3.6", "v0.3.72"));
		for (final String suffix : new String[] { "-rc1", "-beta", "-debug", "-hotfixx" }) {
			assertFalse(UpdateCheck.offers("0.3.72" + suffix, plain, null, true));
			assertFalse(UpdateCheck.offers("0.3.72" + suffix, plain, null, false));
		}
		final UpdateCheck.Release rc = UpdateCheck.parseRelease(RELEASE.replace("v0.3.6", "v0.3.72-rc1"));
		assertFalse(UpdateCheck.offers("0.3.72", rc, null, true));
	}

	@Test
	void aFakeReleaseComesFromTheSystemProperty() {
		System.setProperty(UpdateCheck.FAKE_PROPERTY, "v9.9.99");
		try {
			final UpdateCheck.Release release = UpdateCheck.fetchLatest(1);
			assertNotNull(release);
			assertEquals("v9.9.99", release.tag);
			assertFalse(release.hasApk());
			assertTrue(UpdateCheck.offers("0.3.11", release, null));
			System.setProperty(UpdateCheck.FAKE_PROPERTY, "not a version");
			assertNull(UpdateCheck.fetchLatest(1));
		}
		finally {
			System.clearProperty(UpdateCheck.FAKE_PROPERTY);
		}
	}

	@Test
	void theTagComesFromTheRedirectOfTheLatestPage() {
		final UpdateCheck.Release release = UpdateCheck
				.releaseFromLocation("https://github.com/nikoltu/W3AndroidEngine/releases/tag/v0.4.12");
		assertNotNull(release);
		assertEquals("v0.4.12", release.tag);
		assertEquals("https://github.com/nikoltu/W3AndroidEngine/releases/tag/v0.4.12", release.pageUrl);
		assertFalse(release.hasApk());
		assertFalse(release.isUnknown());
		assertEquals("v0.4.12-hotfix2",
				UpdateCheck.releaseFromLocation("/nikoltu/W3AndroidEngine/releases/tag/v0.4.12-hotfix2?x=1#y").tag);
		assertEquals("v0.4.12", UpdateCheck.releaseFromLocation("https://github.com/a/b/releases/tag/v0.4.12/").tag);
		assertNull(UpdateCheck.releaseFromLocation(null));
		assertNull(UpdateCheck.releaseFromLocation("https://github.com/a/b/releases"));
		assertNull(UpdateCheck.releaseFromLocation("https://github.com/a/b/releases/tag/"));
		assertNull(UpdateCheck.releaseFromLocation("https://github.com/a/b/releases/tag/nightly"));
		assertNull(UpdateCheck.releaseFromLocation("https://github.com/login?return_to=/a/b/releases/latest"));
	}

	@Test
	void onlyALimitAnswerIsARateLimit() {
		assertTrue(UpdateCheck.isRateLimited(429, null, null));
		assertTrue(UpdateCheck.isRateLimited(403, "0", null));
		assertTrue(UpdateCheck.isRateLimited(403, null, "{\"message\":\"API rate limit exceeded for 1.2.3.4.\"}"));
		assertFalse(UpdateCheck.isRateLimited(403, "12", "{\"message\":\"Forbidden\"}"));
		assertFalse(UpdateCheck.isRateLimited(403, null, null));
		assertFalse(UpdateCheck.isRateLimited(404, "0", "rate limit"));
		assertFalse(UpdateCheck.isRateLimited(500, null, null));
		assertFalse(UpdateCheck.isRateLimited(200, "0", null));
	}

	@Test
	void aReleaseOfUnknownVersionIsOfferedOncePerSession() {
		UpdateCheck.resetSession();
		try {
			final UpdateCheck.Release unknown = UpdateCheck.unknownRelease();
			assertTrue(unknown.isUnknown());
			assertFalse(unknown.hasApk());
			assertEquals("https://github.com/nikoltu/W3AndroidEngine/releases", unknown.pageUrl);
			assertTrue(UpdateCheck.offers("0.3.11", unknown, null, true));
			assertTrue(UpdateCheck.offers("0.3.11", unknown, null, false));
			assertTrue(UpdateCheck.offers("", unknown, "v0.3.11", true));
			assertTrue(UpdateCheck.offers("0.3.11", 311, unknown, "", true));
			UpdateCheck.dismissUnknown();
			assertFalse(UpdateCheck.offers("0.3.11", unknown, null, true));
			assertFalse(UpdateCheck.offers("0.3.11", unknown, null, false));
			assertFalse(UpdateCheck.offers("0.3.11", 311, unknown, "", false));
		}
		finally {
			UpdateCheck.resetSession();
		}
	}

	@Test
	void theFakeRateLimitGivesTheUnknownOrTheNamedRelease() {
		UpdateCheck.resetSession();
		try {
			System.setProperty(UpdateCheck.FAKE_PROPERTY, "ratelimit");
			assertTrue(UpdateCheck.fetchLatest(1).isUnknown());
			System.setProperty(UpdateCheck.FAKE_PROPERTY, "ratelimit:bad");
			assertTrue(UpdateCheck.fetchLatest(1).isUnknown());
			System.setProperty(UpdateCheck.FAKE_PROPERTY, "ratelimit:v0.4.99");
			final UpdateCheck.Release named = UpdateCheck.fetchLatest(1);
			assertFalse(named.isUnknown());
			assertEquals("v0.4.99", named.tag);
			assertTrue(UpdateCheck.offers("0.4.12", named, null, true));
			assertTrue(UpdateCheck.offers("0.4.12", named, null, false));
			assertFalse(UpdateCheck.offers("0.4.99", named, null, false));
			assertFalse(UpdateCheck.offers("0.4.99", named, null, true));
			assertFalse(UpdateCheck.offers("0.4.12", named, "v0.4.99", true));
		}
		finally {
			System.clearProperty(UpdateCheck.FAKE_PROPERTY);
			UpdateCheck.resetSession();
		}
	}
}
