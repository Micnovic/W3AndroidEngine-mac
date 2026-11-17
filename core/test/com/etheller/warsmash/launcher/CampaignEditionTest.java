package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.StringBundle;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;

/** The campaign list follows the edition for game data that list the campaigns of each edition apart. */
class CampaignEditionTest {
	@TempDir
	File temp;

	private int gameVersion;

	private static final String SKINS = "[Main]\r\nSkins=Orc,Human\r\n"
			+ "[Default]\r\nCampaignFile_V0=\"UI\\CampaignStrings.txt\"\r\n"
			+ "CampaignFile_V1=\"UI\\CampaignStrings_exp.txt\"\r\n[Orc]\r\n[Human]\r\n";
	private static final String ROC = "[Index]\r\nCampaignList=\"Human\",\"Orc\",\"Undead\"\r\n"
			+ "[Human]\r\nHeader=\"Human\"\r\nName=\"Alliance\"\r\nDefaultOpen=1\r\n"
			+ "Mission0=\"One\",\"A\",\"Campaign\\Human01.w3m\"\r\n"
			+ "[Orc]\r\nHeader=\"Orc\"\r\nName=\"Horde\"\r\nDefaultOpen=1\r\n"
			+ "Mission0=\"One\",\"B\",\"Campaign\\Orc01.w3m\"\r\n"
			+ "[Undead]\r\nHeader=\"Undead\"\r\nName=\"Scourge\"\r\nDefaultOpen=1\r\n"
			+ "Mission0=\"One\",\"C\",\"Campaign\\Undead01.w3m\"\r\n";
	private static final String TFT = "[Index]\r\nCampaignList=\"Naga\"\r\n"
			+ "[Naga]\r\nHeader=\"Naga\"\r\nName=\"Rise of the Naga\"\r\nDefaultOpen=1\r\n"
			+ "Mission0=\"One\",\"D\",\"Campaign\\FrozenThrone\\Naga01.w3x\"\r\n";

	@BeforeEach
	void remember() {
		this.gameVersion = WarsmashConstants.GAME_VERSION;
		WarsmashConstants.GAME_VERSION = 1;
	}

	@AfterEach
	void restore() {
		WarsmashConstants.GAME_VERSION = this.gameVersion;
	}

	private File data(final String... files) throws IOException {
		final File root = new File(this.temp, "data");
		for (int i = 0; i < files.length; i += 2) {
			final File file = new File(root, files[i]);
			file.getParentFile().mkdirs();
			Files.write(file.toPath(), files[i + 1].getBytes(StandardCharsets.UTF_8));
		}
		return root;
	}

	private GameCatalog catalog(final File root) throws IOException {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		ini.readTXT(new ByteArrayInputStream(("[DataSources]\nType00=Folder\nPath00=" + root.getPath() + "\n")
				.getBytes(StandardCharsets.UTF_8)), true);
		return new GameCatalog(ini, GameCatalog.dataKey(ini), new File(this.temp, "Save"), () -> "Player",
				new File(this.temp, "maps.txt"), Runnable::run, new FolderDataSource(root.toPath()));
	}

	/** The campaigns of an edition; the answer comes from the catalog's worker thread. */
	private static GameCatalog.Campaigns campaigns(final GameCatalog catalog, final String edition)
			throws InterruptedException {
		catalog.setEdition(edition);
		final AtomicReference<GameCatalog.Campaigns> answer = new AtomicReference<>();
		catalog.campaigns(answer::set);
		for (int i = 0; (i < 400) && (answer.get() == null); i++) {
			Thread.sleep(25);
		}
		assertNotNull(answer.get());
		return answer.get();
	}

	private GameCatalog.Campaigns campaigns(final File root, final String edition) throws Exception {
		final GameCatalog catalog = catalog(root);
		try {
			return campaigns(catalog, edition);
		}
		finally {
			catalog.close();
		}
	}

	private static String first(final GameCatalog.Campaigns campaigns) {
		return campaigns.catalog.getEntries().get(0).getSection();
	}

	private File both() throws IOException {
		return data("UI/war3skins.txt", SKINS, "UI/CampaignStrings.txt", ROC, "UI/CampaignStrings_exp.txt", TFT);
	}

	@Test
	void editionTellsTheGameVersion() {
		assertEquals(0, LauncherKeys.editionGameVersion(LauncherKeys.EDITION_ROC, 1));
		assertEquals(1, LauncherKeys.editionGameVersion(LauncherKeys.EDITION_TFT, 0));
		assertEquals(0, LauncherKeys.editionGameVersion(null, 0));
		assertEquals(1, LauncherKeys.editionGameVersion("other", 1));
	}

	@Test
	void reignOfChaosListsItsOwnCampaigns() throws Exception {
		final GameCatalog.Campaigns roc = campaigns(both(), LauncherKeys.EDITION_ROC);
		assertEquals(3, roc.catalog.getEntries().size());
		assertEquals("Human", first(roc));
		assertTrue(roc.catalog.isClassicList());
	}

	@Test
	void frozenThroneListsItsOwnCampaigns() throws Exception {
		final GameCatalog.Campaigns tft = campaigns(both(), LauncherKeys.EDITION_TFT);
		assertEquals(1, tft.catalog.getEntries().size());
		assertEquals("Naga", first(tft));
		assertFalse(tft.catalog.isClassicList());
	}

	@Test
	void editionNotToldKeepsTheGameVersion() throws Exception {
		final File root = both();
		assertEquals("Naga", first(campaigns(root, null)));
		WarsmashConstants.GAME_VERSION = 0;
		assertEquals("Human", first(campaigns(root, null)));
	}

	@Test
	void catalogFollowsTheEditionAsItChanges() throws Exception {
		final GameCatalog catalog = catalog(both());
		try {
			assertEquals("Human", first(campaigns(catalog, LauncherKeys.EDITION_ROC)));
			assertEquals("Naga", first(campaigns(catalog, LauncherKeys.EDITION_TFT)));
			assertEquals("Human", first(campaigns(catalog, LauncherKeys.EDITION_ROC)));
		}
		finally {
			catalog.close();
		}
	}

	@Test
	void dataWithOneEditionsListShowsItForBoth() throws Exception {
		final File root = data("UI/war3skins.txt", SKINS, "UI/CampaignStrings_exp.txt", TFT);
		assertEquals("Naga", first(campaigns(root, LauncherKeys.EDITION_ROC)));
		assertEquals("Naga", first(campaigns(root, LauncherKeys.EDITION_TFT)));
	}

	@Test
	void combinedListIsTheSameInBothEditions() throws Exception {
		final File root = data("UI/war3skins.txt", "[Main]\r\nSkins=Orc,Human\r\n[Default]\r\n[Orc]\r\n[Human]\r\n",
				"UI/CampaignInfoClassic.txt", ROC);
		final GameCatalog.Campaigns roc = campaigns(root, LauncherKeys.EDITION_ROC);
		final GameCatalog.Campaigns tft = campaigns(root, LauncherKeys.EDITION_TFT);
		assertTrue(roc.catalog.isUnified());
		assertTrue(tft.catalog.isUnified());
		assertEquals(3, roc.catalog.getEntries().size());
		assertEquals(3, tft.catalog.getEntries().size());
	}

	@Test
	void campaignFileTakesTheOtherVersionOnlyWhenTheDataLacksIt() throws IOException {
		final FolderDataSource data = new FolderDataSource(data("UI/CampaignStrings.txt", ROC).toPath());
		final IntFunction<String> field = v -> v == 0 ? "UI\\CampaignStrings.txt" : "UI\\CampaignStrings_exp.txt";
		assertEquals("UI\\CampaignStrings.txt", CampaignCatalog.campaignFile(data, field, 0));
		assertEquals("UI\\CampaignStrings.txt", CampaignCatalog.campaignFile(data, field, 1));
		final File empty = new File(this.temp, "empty");
		empty.mkdirs();
		assertEquals("UI\\CampaignStrings_exp.txt",
				CampaignCatalog.campaignFile(new FolderDataSource(empty.toPath()), field, 1));
	}
}
