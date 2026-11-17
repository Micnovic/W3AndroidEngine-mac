package com.etheller.warsmash;

import com.etheller.warsmash.viewer5.FogSettings;

public interface SingleModelScreen {
	void setModel(String path, FogSettings fogSettings);

	void alternateModelToBattlenet();

	void unAlternateModelBackToNormal();

	/**
	 * Plays a campaign's model cinematic full screen in place of the menu, then counts the mission as won.
	 *
	 * @param path       the model
	 * @param campaign   the mission's campaign, by the number scripts know it by
	 * @param missionName the mission's name as the campaign screens show it
	 * @param difficulty easy, normal or hard
	 * @return false when this screen cannot play it or the model cannot be played
	 */
	default boolean playModelMission(final String path, final int campaign, final String missionName,
			final String difficulty) {
		return false;
	}
}
