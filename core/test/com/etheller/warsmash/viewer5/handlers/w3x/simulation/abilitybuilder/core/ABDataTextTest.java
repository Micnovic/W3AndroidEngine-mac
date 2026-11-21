package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ABDataTextTest {
	@Test
	void unsetFieldsReadAsZero() {
		assertEquals(0, ABDataText.parseInt(""));
		assertEquals(0, ABDataText.parseInt(null));
		assertEquals(0, ABDataText.parseInt("-"));
		assertEquals(0L, ABDataText.parseLong(""));
		assertEquals(0f, ABDataText.parseFloat(""), 0f);
		assertEquals(0f, ABDataText.parseFloat("_"), 0f);
	}

	@Test
	void numbersAreRead() {
		assertEquals(1, ABDataText.parseInt("1"));
		assertEquals(-3, ABDataText.parseInt("-3"));
		assertEquals(2, ABDataText.parseInt("2.0"));
		assertEquals(40000000000L, ABDataText.parseLong("40000000000"));
		assertEquals(-0.25f, ABDataText.parseFloat("-0.25"), 0f);
	}
}
