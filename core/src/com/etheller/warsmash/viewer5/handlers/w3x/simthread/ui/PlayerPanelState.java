package com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui;

import java.util.Arrays;

/**
 * The local player's numbers shown in the top bar and the research list. Writer: the simulation thread while the frame
 * is built. Reader: the render thread.
 */
public final class PlayerPanelState {
	public int playerIndex;
	public int gold;
	public int lumber;
	public int foodUsed;
	public int foodCap;
	/** 0 none, 1 low, 2 high. */
	public int upkeepLevel;
	public int goldIncomeTax;
	/** Game time in milliseconds the panel's clock shows. */
	public long gameTimeMillis;
	public int researchCount;
	public int[] researchIds = new int[16];
	public int[] researchLevels = new int[16];
	/**
	 * Other players that give the viewing player full shared control, in player order: the "Team Resources" rows.
	 * Players are told apart by index so a change of the set shows as a change of {@link #teamPlayerMask}.
	 */
	public int teamCount;
	public long teamPlayerMask;
	public int[] teamPlayerIndices = new int[8];
	public String[] teamNames = new String[8];
	/** Player color as an RGB hex number (no alpha), the one chat text uses. */
	public int[] teamColors = new int[8];
	public int[] teamGold = new int[8];
	public int[] teamLumber = new int[8];
	public int[] teamFoodUsed = new int[8];
	public int[] teamFoodCap = new int[8];
	public int[] teamUpkeepLevel = new int[8];

	public void addTeamPlayer(final int playerIndex, final String name, final int color, final int gold,
			final int lumber, final int foodUsed, final int foodCap, final int upkeepLevel) {
		if (teamCount == teamPlayerIndices.length) {
			final int size = teamCount * 2;
			teamPlayerIndices = Arrays.copyOf(teamPlayerIndices, size);
			teamNames = Arrays.copyOf(teamNames, size);
			teamColors = Arrays.copyOf(teamColors, size);
			teamGold = Arrays.copyOf(teamGold, size);
			teamLumber = Arrays.copyOf(teamLumber, size);
			teamFoodUsed = Arrays.copyOf(teamFoodUsed, size);
			teamFoodCap = Arrays.copyOf(teamFoodCap, size);
			teamUpkeepLevel = Arrays.copyOf(teamUpkeepLevel, size);
		}
		teamPlayerIndices[teamCount] = playerIndex;
		teamNames[teamCount] = name;
		teamColors[teamCount] = color;
		teamGold[teamCount] = gold;
		teamLumber[teamCount] = lumber;
		teamFoodUsed[teamCount] = foodUsed;
		teamFoodCap[teamCount] = foodCap;
		teamUpkeepLevel[teamCount] = upkeepLevel;
		teamCount++;
		teamPlayerMask |= 1L << (playerIndex & 63);
	}

	public void addResearch(final int upgradeId, final int level) {
		if (researchCount == researchIds.length) {
			researchIds = Arrays.copyOf(researchIds, researchCount * 2);
			researchLevels = Arrays.copyOf(researchLevels, researchCount * 2);
		}
		researchIds[researchCount] = upgradeId;
		researchLevels[researchCount] = level;
		researchCount++;
	}

	public void clear() {
		playerIndex = 0;
		gold = 0;
		lumber = 0;
		foodUsed = 0;
		foodCap = 0;
		upkeepLevel = 0;
		goldIncomeTax = 0;
		gameTimeMillis = 0;
		researchCount = 0;
		teamCount = 0;
		teamPlayerMask = 0;
	}

	public void copyFrom(final PlayerPanelState other) {
		playerIndex = other.playerIndex;
		gold = other.gold;
		lumber = other.lumber;
		foodUsed = other.foodUsed;
		foodCap = other.foodCap;
		upkeepLevel = other.upkeepLevel;
		goldIncomeTax = other.goldIncomeTax;
		gameTimeMillis = other.gameTimeMillis;
		researchCount = 0;
		for (int i = 0; i < other.researchCount; i++) {
			addResearch(other.researchIds[i], other.researchLevels[i]);
		}
		teamCount = 0;
		teamPlayerMask = 0;
		for (int i = 0; i < other.teamCount; i++) {
			addTeamPlayer(other.teamPlayerIndices[i], other.teamNames[i], other.teamColors[i], other.teamGold[i],
					other.teamLumber[i], other.teamFoodUsed[i], other.teamFoodCap[i], other.teamUpkeepLevel[i]);
		}
	}
}
