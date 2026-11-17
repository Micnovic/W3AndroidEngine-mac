package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/** The reasons the launcher gives for a map the engine cannot play. */
class MapIssueTest {
	@TempDir
	File temp;

	@Test
	void encryptedArchivesAreToldByTheirExtension() {
		assertEquals(MapIssue.PROTECTED, MapIssue.ofName("Campaign\\ForsakenKingdom\\HumanRE01.w3xd"));
		assertEquals(MapIssue.NONE, MapIssue.ofName("Maps\\Custom\\a.w3x"));
		assertEquals(MapIssue.NONE, MapIssue.ofName(null));
	}

	@Test
	void aCampaignMapIsMissingWhenTheDataLacksIt() throws Exception {
		Files.createDirectories(new File(this.temp, "Campaign").toPath());
		Files.write(new File(this.temp, "Campaign/a.w3x").toPath(), new byte[] { 1 });
		Files.write(new File(this.temp, "Campaign/b.w3xd").toPath(), new byte[] { 1 });
		final FolderDataSource data = new FolderDataSource(this.temp.toPath());
		assertEquals(MapIssue.NONE, MapIssue.ofData(data, "Campaign\\a.w3x"));
		assertEquals(MapIssue.PROTECTED, MapIssue.ofData(data, "Campaign\\b.w3xd"));
		assertEquals(MapIssue.MISSING, MapIssue.ofData(data, "Campaign\\c.w3x"));
		assertEquals(MapIssue.MISSING, MapIssue.ofData(data, ""));
		assertEquals(MapIssue.NONE, MapIssue.ofData(data, "Doodads/Cinematic/X/X.mdl"));
		assertEquals(MapIssue.NONE, MapIssue.ofData(data, "Doodads\\Cinematic\\X\\X.MDX"));
	}

	@Test
	void aMissionEntryThatNamesAModelIsNotAMap() {
		assertTrue(CampaignMission.isModelEntry("Doodads/Cinematic/X/X.mdl"));
		assertTrue(CampaignMission.isModelEntry("Doodads\\Cinematic\\X\\X.MDX"));
		assertFalse(CampaignMission.isModelEntry("Campaign/Reforged/TFT/UndeadX08.w3x"));
		assertFalse(CampaignMission.isModelEntry(null));
	}

	@Test
	void theKeptListKeepsTheIssueOfAMap() {
		final File list = new File(this.temp, "maps.txt");
		MapListCache.write(list, "header", new MapListCache.Listing(
				Arrays.asList(new GameCatalog.MapEntry("Maps\\a.w3x", 2, "Lua", false, MapIssue.LUA),
						new GameCatalog.MapEntry("Maps\\b.w3x", 2, "Plain", true)),
				Collections.emptyList()));
		final MapListCache.Listing read = MapListCache.read(list, "header");
		assertEquals(MapIssue.LUA, read.maps.get(0).issue);
		assertEquals(MapIssue.NONE, read.maps.get(1).issue);
	}

	@Test
	void everyIssueHasItsTextsInTheLauncherStrings() throws Exception {
		final File resources = new File("../android/res/values/strings.xml");
		final LauncherStrings strings;
		try (FileInputStream in = new FileInputStream(resources)) {
			strings = new LauncherStrings(in);
		}
		for (final MapIssue issue : MapIssue.values()) {
			if (issue == MapIssue.NONE) {
				continue;
			}
			assertNotEquals(issue.message, strings.get(issue.message));
			assertNotEquals(issue.tag, strings.get(issue.tag));
		}
		assertEquals("This map uses a Lua script, which is not supported yet.", strings.get(MapIssue.LUA.message));
		assertEquals("This map is protected and cannot be opened.", strings.get(MapIssue.PROTECTED.message));
	}
}
