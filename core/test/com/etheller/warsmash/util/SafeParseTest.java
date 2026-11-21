package com.etheller.warsmash.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SafeParseTest {
	@Test
	void emptyAndPlaceholderFieldsReadAsTheFallback() {
		for (final String text : new String[] { null, "", "-", "_", "   ", "abc", "1,5", "0x10" }) {
			assertEquals(0, SafeParse.parseInt(text), String.valueOf(text));
			assertEquals(0L, SafeParse.parseLong(text), String.valueOf(text));
			assertEquals(0f, SafeParse.parseFloat(text), 0f, String.valueOf(text));
		}
		assertEquals(-1, SafeParse.parseInt("", -1));
		assertEquals(-1, SafeParse.parseInt("junk", -1));
		assertEquals(2.5f, SafeParse.parseFloat("-", 2.5f), 0f);
	}

	@Test
	void numbersAreReadWithSurroundingSpace() {
		assertEquals(7, SafeParse.parseInt(" 7 "));
		assertEquals(-3, SafeParse.parseInt("-3"));
		assertEquals(2, SafeParse.parseInt("2.9"));
		assertEquals(0.75f, SafeParse.parseFloat(" 0.75"), 0f);
		assertEquals(40000000000L, SafeParse.parseLong("40000000000"));
	}
}
