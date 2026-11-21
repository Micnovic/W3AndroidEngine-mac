package com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui;

import java.util.List;
import java.util.Map;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CGameplayConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

/**
 * Fills the player panel (resources, food, upkeep, researched upgrades) from the simulation. Writer: the thread that
 * runs simulation steps. Only reads the simulation.
 * <p>
 * {@link PlayerPanelState#goldIncomeTax} is the share of gold income the upkeep level takes, in whole percent (the
 * upkeep tip shows 100 minus it). The research list is sorted by upgrade id so the frame does not depend on map
 * order.
 */
public final class PlayerPanelWriter {
	private int[] ids = new int[16];
	private int[] levels = new int[16];

	public void write(final CSimulation game, final CPlayer player, final long gameTimeMillis,
			final PlayerPanelState out) {
		out.clear();
		out.playerIndex = player.getId();
		out.gold = player.getGold();
		out.lumber = player.getLumber();
		out.foodUsed = player.getFoodUsed();
		out.foodCap = player.getFoodCap();
		final CGameplayConstants constants = game.getGameplayConstants();
		final int level = constants.getUpkeepLevel(out.foodUsed);
		out.upkeepLevel = level;
		final float[] goldTax = constants.getUpkeepGoldTax();
		out.goldIncomeTax = goldTax.length == 0 ? 0 : Math.round(100 * goldTax[Math.min(level, goldTax.length - 1)]);
		out.gameTimeMillis = gameTimeMillis;
		int count = 0;
		for (final Map.Entry<War3ID, Integer> entry : player.getTechtreeUnlockedLevels().entrySet()) {
			final int researched = entry.getValue();
			if (researched <= 0) {
				continue;
			}
			if (count == this.ids.length) {
				this.ids = java.util.Arrays.copyOf(this.ids, count * 2);
				this.levels = java.util.Arrays.copyOf(this.levels, count * 2);
			}
			final int id = entry.getKey().getValue();
			int at = count;
			while ((at > 0) && (this.ids[at - 1] > id)) {
				this.ids[at] = this.ids[at - 1];
				this.levels[at] = this.levels[at - 1];
				at--;
			}
			this.ids[at] = id;
			this.levels[at] = researched;
			count++;
		}
		for (int i = 0; i < count; i++) {
			out.addResearch(this.ids[i], this.levels[i]);
		}
		writeTeam(game, player.getId(), out);
	}

	/** The rows of the team resources board: the other players that give this one full shared control. */
	private void writeTeam(final CSimulation game, final int viewingIndex, final PlayerPanelState out) {
		if (viewingIndex < 0) {
			return;
		}
		final List<CPlayer> players = game.getPlayers();
		final CGameplayConstants constants = game.getGameplayConstants();
		for (int i = 0; i < players.size(); i++) {
			final CPlayer other = players.get(i);
			if ((i == viewingIndex) || !other.hasAlliance(viewingIndex, CAllianceType.SHARED_ADVANCED_CONTROL)) {
				continue;
			}
			out.addTeamPlayer(i, other.getName(), other.getColor(), other.getGold(), other.getLumber(),
					other.getFoodUsed(), other.getFoodCap(), constants.getUpkeepLevel(other.getFoodUsed()));
		}
	}
}
