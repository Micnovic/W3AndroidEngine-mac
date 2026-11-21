package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

/** The map's leave rules hear of a player who leaves while another human is still playing, and of no one else. */
class CPlayerLeaveTest {
	private static final class CountingEvent extends CPlayerEvent {
		private final List<Integer> fired;
		private final int playerIndex;

		CountingEvent(final List<Integer> fired, final int playerIndex) {
			super(null, null, null, JassGameEventsWar3.EVENT_PLAYER_LEAVE, null);
			this.fired = fired;
			this.playerIndex = playerIndex;
		}

		@Override
		public void fire(final CPlayer player, final TriggerExecutionScope scope) {
			this.fired.add(this.playerIndex);
		}
	}

	private static CSimulation gameOfHumans(final int humans, final List<Integer> fired) {
		final CSimulation game = TestObjects.allocate(CSimulation.class);
		final List<CPlayer> players = new ArrayList<>();
		for (int i = 0; i < humans + 1; i++) {
			final CPlayer player = TestObjects.allocate(CPlayer.class);
			final EnumMap<JassGameEventsWar3, List<CPlayerEvent>> events = new EnumMap<>(JassGameEventsWar3.class);
			final List<CPlayerEvent> list = new ArrayList<>();
			list.add(new CountingEvent(fired, i));
			events.put(JassGameEventsWar3.EVENT_PLAYER_LEAVE, list);
			TestObjects.set(player, "eventTypeToEvents", events);
			// the last player is a computer: it never counts as another human
			player.setController(i < humans ? CMapControl.USER : CMapControl.COMPUTER);
			player.setSlotState(CPlayerSlotState.PLAYING);
			players.add(player);
		}
		TestObjects.set(game, "players", players);
		return game;
	}

	@Test
	void inAThreeHumanGameTheFirstTwoToLeaveAreHeardOfAndTheLastIsNot() {
		final List<Integer> fired = new ArrayList<>();
		final CSimulation game = gameOfHumans(3, fired);
		new CPlayerUnitOrderExecutor(game, 0).leaveGame();
		new CPlayerUnitOrderExecutor(game, 2).leaveGame();
		assertEquals(List.of(0, 2), fired);
		new CPlayerUnitOrderExecutor(game, 1).leaveGame();
		assertEquals(List.of(0, 2), fired);
		assertEquals(CPlayerGameResult.DEFEAT, game.getPlayer(1).getRemovedResult());
	}

	@Test
	void aSecondLeaveOfTheSamePlayerDoesNothing() {
		final List<Integer> fired = new ArrayList<>();
		final CSimulation game = gameOfHumans(2, fired);
		new CPlayerUnitOrderExecutor(game, 0).leaveGame();
		new CPlayerUnitOrderExecutor(game, 0).leaveGame();
		assertEquals(List.of(0), fired);
	}
}
