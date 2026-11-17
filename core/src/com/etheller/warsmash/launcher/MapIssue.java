package com.etheller.warsmash.launcher;

import java.util.Locale;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/**
 * Why a map listed by the launcher cannot be played by this engine, found before the game is started so that the
 * launcher can say so in a few words. Each value names the launcher string that tells it.
 */
public enum MapIssue {
	NONE(null, null),
	/** The map's script is Lua; the engine runs JASS only. */
	LUA("map_issue_lua", "map_tag_lua"),
	/** The map carries no script of either kind. */
	NO_SCRIPT("map_issue_no_script", "map_tag_no_script"),
	/** The archive is encrypted (Forsaken Kingdom's .w3xd files). */
	PROTECTED("map_issue_protected", "map_tag_protected"),
	/** A campaign names a map the game data does not have. */
	MISSING("map_issue_missing", "map_tag_missing");

	/** The string of the full sentence. */
	public final String message;
	/** The string of the few words that stand beside the map in a list. */
	public final String tag;

	MapIssue(final String message, final String tag) {
		this.message = message;
		this.tag = tag;
	}

	/** What the file name alone tells: encrypted map archives have their own extension. */
	public static MapIssue ofName(final String path) {
		return (path != null) && path.toLowerCase(Locale.ROOT).endsWith(".w3xd") ? PROTECTED : NONE;
	}

	/** The issue of an opened map's script. */
	public static MapIssue of(final War3Map map) {
		if (map.hasJassScript()) {
			return NONE;
		}
		return map.hasLuaScript() ? LUA : NO_SCRIPT;
	}

	/** The issue of a map named by a campaign list, without opening it more than the data needs. */
	public static MapIssue ofData(final DataSource data, final String path) {
		if ((path == null) || path.isEmpty()) {
			return MISSING;
		}
		// A mission entry that names a model is an in-engine cinematic, not a map; it is not checked here.
		if (CampaignMission.isModelEntry(path)) {
			return NONE;
		}
		if (!data.has(path)) {
			return MISSING;
		}
		return ofName(path);
	}
}
