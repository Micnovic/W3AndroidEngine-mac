package com.etheller.warsmash.viewer5.handlers.w3x.ui.menu;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CampaignMission {
	private final String header;
	private final String missionName;
	private final String mapFilename;
	private final List<String> movies;

	public CampaignMission(final String header, final String missionName, final String mapFilename) {
		this(header, missionName, mapFilename, Collections.singletonList(mapFilename));
	}

	public CampaignMission(final String header, final String missionName, final String mapFilename,
			final List<String> movies) {
		this.header = header;
		this.missionName = missionName;
		this.mapFilename = mapFilename;
		this.movies = movies;
	}

	/**
	 * @return whether a mission entry names a model rather than a map: the campaign's in-engine cinematic (the fight of
	 *         Arthas and Illidan ends the Frozen Throne's Undead campaign this way)
	 */
	public static boolean isModelEntry(final String mapFilename) {
		if (mapFilename == null) {
			return false;
		}
		final String lower = mapFilename.toLowerCase(Locale.ROOT);
		return lower.endsWith(".mdl") || lower.endsWith(".mdx");
	}

	public boolean isModel() {
		return isModelEntry(this.mapFilename);
	}

	public String getHeader() {
		return this.header;
	}

	public String getMissionName() {
		return this.missionName;
	}

	public String getMapFilename() {
		return this.mapFilename;
	}

	/**
	 * @return for a cinematic entry, the movies it plays one after another (the introduction has the logos around
	 *         its movie); for a mission, its map file
	 */
	public List<String> getMovies() {
		return this.movies;
	}
}
