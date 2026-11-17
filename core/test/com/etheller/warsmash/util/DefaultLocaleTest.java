package com.etheller.warsmash.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.mdx.ReplaceableIds;

/**
 * File paths and data keys must not change with the phone's language: Arabic writes digits as Arabic-Indic ones,
 * Turkish lower-cases I to a dotless i.
 */
class DefaultLocaleTest {
	private Locale saved;

	@BeforeEach
	void remember() {
		this.saved = Locale.getDefault();
	}

	@AfterEach
	void restore() {
		Locale.setDefault(this.saved);
	}

	private static void check() {
		assertEquals("00", ReplaceableIds.getIdString(0));
		assertEquals("12", ReplaceableIds.getIdString(12));
		final IniFile ini = new IniFile("[Info]\nIdentifier=1\n");
		assertNotNull(ini.getSection("INFO"));
		assertEquals("1", ini.getSection("INFO").get("identifier"));
	}

	@Test
	void arabicDigitsStayAscii() {
		Locale.setDefault(new Locale("ar", "EG"));
		check();
	}

	@Test
	void turkishCaseStaysAscii() {
		Locale.setDefault(new Locale("tr", "TR"));
		check();
	}
}
