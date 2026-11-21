package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;

class GameLanguageTest {
	private static final List<String> MODULES = Arrays.asList("war3.w3mod", "war3.w3mod\\_deprecated.w3mod",
			"war3.w3mod\\_locales\\enus.w3mod");

	@Test
	void theChosenLanguageIsReadAfterTheConfiguredOneSoItWinsAndTheOtherStaysTheFallback() {
		assertEquals(Arrays.asList("war3.w3mod", "war3.w3mod\\_deprecated.w3mod", "war3.w3mod\\_locales\\enus.w3mod",
				"war3.w3mod\\_locales\\eses.w3mod"), GameLanguage.prefixes(MODULES, "esES"));
	}

	@Test
	void theOlderInstallsNameTheirLanguageLayerDifferently() {
		assertEquals(Arrays.asList("war3.mpq", "enus-war3local.mpq", "dede-war3local.mpq"),
				GameLanguage.prefixes(Arrays.asList("war3.mpq", "enus-war3local.mpq"), "deDE"));
	}

	@Test
	void noChoiceTheSameLanguageOrNoLanguageLayerLeavesThePrefixesAlone() {
		assertSame(MODULES, GameLanguage.prefixes(MODULES, ""));
		assertSame(MODULES, GameLanguage.prefixes(MODULES, "enUS"));
		final List<String> plain = Arrays.asList("war3.w3mod");
		assertSame(plain, GameLanguage.prefixes(plain, "esES"));
	}

	@Test
	void aFolderOfALanguageHasSiblingsOfTheOthers() {
		assertEquals("C:\\game\\_locales\\frfr.w3mod",
				GameLanguage.siblingFolder("C:/game/_locales/enus.w3mod", "frFR"));
		assertNull(GameLanguage.siblingFolder("C:\\game\\data", "frFR"));
	}

	@Test
	void codesAreReadInAnySpelling() {
		assertEquals("esES", GameLanguage.canonical("eses"));
		assertEquals("zhCN", GameLanguage.canonical(" ZHCN "));
		assertEquals("", GameLanguage.canonical("Spanish"));
		assertEquals(Arrays.asList("enUS", "deDE", "esES", "xxYY"),
				GameLanguage.sorted(Arrays.asList("xxYY", "esES", "enUS", "deDE")));
	}

	@Test
	void theConfigurationKeepsTheChoice() {
		final DataTable ini = new DataTable(null);
		assertEquals("", GameLanguage.configured(ini));
		GameLanguage.apply(ini, "itit");
		assertEquals("itIT", GameLanguage.configured(ini));
	}

	@Test
	void aFlatFolderKeepsItsOtherLanguagesInItsLocalesFolder() {
		assertEquals(new java.io.File(new java.io.File("C:\\game\\data", "_locales"), "eses.w3mod").getPath(),
				GameLanguage.childFolder("C:\\game\\data", "esES"));
		assertNull(GameLanguage.childFolder("C:\\game\\data", ""));
		assertNull(GameLanguage.childFolder("C:\\game\\data\\_locales\\enus.w3mod", "esES"));
	}

	@Test
	void theLanguageInUseIsTheChosenOneElseTheOneTheDataIsMadeFor() {
		final DataTable ini = new DataTable(null);
		final Element sources = new Element("DataSources", ini);
		sources.setField("Prefixes00", "war3.w3mod,war3.w3mod\\_locales\\eses.w3mod");
		ini.put("DataSources", sources);
		GameLanguage.useInGame(ini);
		assertEquals("esES", GameLanguage.inGame());
		GameLanguage.apply(ini, "frFR");
		GameLanguage.useInGame(ini);
		assertEquals("frFR", GameLanguage.inGame());
		GameLanguage.useInGame(new DataTable(null));
		assertEquals("enUS", GameLanguage.inGame());
	}
}
