package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.PlayerPanelState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CMultiboard;

/**
 * The "Team Resources" board: one row for every other player that gives the local player full shared control
 * (advanced control, with or without shared control or an alliance), in player order, with its name in its player
 * color, its gold, its lumber and its food in the color of its upkeep. The game draws it as a multiboard of its own
 * and shows one board at a time: the board comes up when the set of those players changes, a script board shown
 * later takes its place, and hiding that script board does not bring it back.
 */
final class MeleeUITeamResources {
	private static final int NAME_COLUMN = 0;
	private static final int GOLD_COLUMN = 1;
	private static final int LUMBER_COLUMN = 2;
	private static final int FOOD_COLUMN = 3;
	private static final int COLUMN_COUNT = 4;
	private static final float NAME_WIDTH = 0.1f;
	private static final float RESOURCE_WIDTH = 0.0505f;
	private static final int TITLE_COLOR = 0xF0F010FF;
	private static final int VALUE_COLOR = 0xFFFFFFFF;
	private static final int[] UPKEEP_COLORS = { 0x00FF00FF, 0xFFFF00FF, 0xFF0000FF };

	private final MeleeUI ui;
	private final CMultiboard board = new CMultiboard();
	private int rowCount;
	private long sharingPlayers;
	private boolean shown;
	private CMultiboard lastScriptBoard;
	private String[] icons;

	MeleeUITeamResources(final MeleeUI ui) {
		this.ui = ui;
		this.board.setTitleColor(TITLE_COLOR);
		this.board.setDisplayed(true);
	}

	boolean isBoard(final CMultiboard board) {
		return board == this.board;
	}

	/** The board to draw this frame, given the script board shown now (or null); null for none. */
	CMultiboard update(final CMultiboard scriptBoard) {
		final PlayerPanelState panel = this.ui.commands.frameState(false).panel;
		final long sharing = panel.teamPlayerMask;
		final int count = panel.teamCount;
		if (scriptBoard != this.lastScriptBoard) {
			this.lastScriptBoard = scriptBoard;
			if (scriptBoard != null) {
				this.shown = false;
			}
		}
		if (sharing != this.sharingPlayers) {
			this.sharingPlayers = sharing;
			this.shown = count > 0;
		}
		if (!this.shown || (count == 0)) {
			return scriptBoard;
		}
		if (this.icons == null) {
			final GameUI root = this.ui.rootFrame;
			this.icons = new String[] { null, skinFile(root, "GoldIcon"), skinFile(root, "LumberIcon"),
					skinFile(root, "SupplyIcon") };
			this.board.setTitle(root.getTemplates().getDecoratedString("TEAM_RESOURCES"));
		}
		if (count != this.rowCount) {
			this.rowCount = count;
			this.board.setColumnCount(COLUMN_COUNT);
			this.board.setRowCount(count);
			for (int row = 0; row < count; row++) {
				for (int column = 0; column < COLUMN_COUNT; column++) {
					final CMultiboard.Item item = this.board.getItem(row, column);
					item.width = column == NAME_COLUMN ? NAME_WIDTH : RESOURCE_WIDTH;
					item.showIcon = column != NAME_COLUMN;
					item.icon = this.icons[column] == null ? "" : this.icons[column];
					item.valueColor = VALUE_COLOR;
				}
			}
		}
		boolean changed = false;
		for (int row = 0; row < count; row++) {
			changed |= set(row, NAME_COLUMN, panel.teamNames[row], nameColor(panel.teamColors[row]));
			changed |= set(row, GOLD_COLUMN, Integer.toString(panel.teamGold[row]), VALUE_COLOR);
			changed |= set(row, LUMBER_COLUMN, Integer.toString(panel.teamLumber[row]), VALUE_COLOR);
			final int foodCap = panel.teamFoodCap[row];
			final String food = foodCap == 0 ? Integer.toString(panel.teamFoodUsed[row])
					: panel.teamFoodUsed[row] + "/" + foodCap;
			changed |= set(row, FOOD_COLUMN, food, foodColor(panel.teamUpkeepLevel[row]));
		}
		if (changed) {
			this.board.itemChanged();
		}
		return this.board;
	}

	private boolean set(final int row, final int column, final String value, final int color) {
		final CMultiboard.Item item = this.board.getItem(row, column);
		if (value.equals(item.value) && (item.valueColor == color)) {
			return false;
		}
		item.value = value;
		item.valueColor = color;
		return true;
	}

	private static int nameColor(final int playerColor) {
		return (Integer.parseInt(MeleeChat.playerTextHex(playerColor), 16) << 8) | 0xFF;
	}

	private static int foodColor(final int upkeepLevel) {
		return UPKEEP_COLORS[Math.min(upkeepLevel, UPKEEP_COLORS.length - 1)];
	}

	private static String skinFile(final GameUI root, final String key) {
		return root.hasSkinField(key) ? root.getSkinField(key) : "";
	}
}
