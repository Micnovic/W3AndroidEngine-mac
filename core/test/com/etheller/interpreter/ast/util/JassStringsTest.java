package com.etheller.interpreter.ast.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Values measured in the original game (1.29): string natives work on UTF-8 bytes.
 */
class JassStringsTest {
	@Test
	void lengthCountsBytes() {
		assertEquals(2, JassStrings.length("\u0105"));
		assertEquals(4, JassStrings.length("a\u0105b"));
		assertEquals(3, JassStrings.length("\u20AC"));
		assertEquals(6, JassStrings.length("\u65E5\u672C"));
		assertEquals(0, JassStrings.length(null));
	}

	@Test
	void substringUsesByteOffsets() {
		final String s = "a\u0105b";
		assertEquals("\u0105", JassStrings.substring(s, 1, 3));
		assertEquals("b", JassStrings.substring(s, 3, 4));
		assertEquals(1, JassStrings.length(JassStrings.substring(s, 1, 2)));
		assertEquals("abc", JassStrings.substring("abc", -2, 99));
	}

	@Test
	void cutHalvesJoinBack() {
		for (final String c : new String[] { "\u0105", "\u20AC", "\u65E5", "\uD83D\uDE00" }) {
			final int n = JassStrings.length(c);
			for (int cut = 1; cut < n; cut++) {
				final String joined = JassStrings.concat(JassStrings.substring(c, 0, cut),
						JassStrings.substring(c, cut, n));
				assertEquals(c, joined);
			}
		}
	}

	@Test
	void caseChangesAsciiOnly() {
		assertEquals("A\u0105B", JassStrings.changeCase("a\u0105b", true));
		assertEquals("a\u0104b", JassStrings.changeCase("A\u0104B", false));
	}
}
