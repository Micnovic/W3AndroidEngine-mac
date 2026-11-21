package com.etheller.warsmash.lan;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;

import com.etheller.warsmash.networking.GameTurnManager;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.ReplayRecording;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ChecksumSteps;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.SimulationChecksum;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandCodec;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandRecorder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderExecutor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;

import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.protocol.LanData.CmdEntry;
import net.warsmash.lan.protocol.LanData.ParticipantInfo;
import net.warsmash.lan.protocol.LanData.PresenceEntry;
import net.warsmash.lan.protocol.LanData.TurnData;
import net.warsmash.lan.protocol.LanProtocol;

/**
 * Lets the game make a step only inside a turn the host released. A turn is a fixed number of steps; the commands
 * every player issued for it are applied, in the host's order, just before its first step, through the same
 * recorder as a single player game's, so they are in the command log and the game's replay.
 * <p>
 * Called on the game thread only; the turns come from the client's queue.
 */
public final class LockstepTurnManager implements GameTurnManager {
	/** Where the turns come from and where the game's answers go: the client, or a fake in a test. */
	public interface Turns {
		/** The next turn the host released, or null. */
		TurnData poll();

		/** The game made every step of the turn. */
		void processed(int turn);

		void checksum(int tick, int crc);

		void leave();

		/** The game's simulation stands still (true) or runs again; the host sends turns without steps meanwhile. */
		default void simPaused(final boolean paused) {
		}

		/** Whether this player is being given the game so far to play through (a rejoin). */
		default boolean catchingUp() {
			return false;
		}

		/** The number of archived turns being played through. */
		default int catchUpTotal() {
			return 0;
		}

		/** The steps of the archived turns: how far the game has to be played through. */
		default long catchUpSteps() {
			return 0;
		}

		/** The number of the last turn received. */
		default int received() {
			return -1;
		}

		/** The archive is played through and the game is within a few turns of the host's. */
		default void caughtUp() {
		}
	}

	/** The game being stepped. */
	public interface Game {
		int tick();

		int checksum();

		boolean ended();

		/** Where the commands of the player of that slot are applied; null for a slot without a player. */
		CPlayerUnitOrderListener player(int slot);

		/** The slot a participant plays, or -1. */
		int slotOf(int participant);

		/** Whether a player's pause or the map's script has stopped the simulation. */
		default boolean simPaused() {
			return false;
		}

		/** How the game's checksum is made as it steps; null for {@link #checksum()} all at once at each interval. */
		default ChecksumSteps checksumSteps(final int interval) {
			return null;
		}
	}

	private static final int MAX_TURNS_PER_PAUSED_FRAME = 64;
	/** A player being given the game so far draws nothing, so a long pause in it is played through in few frames. */
	private static final int MAX_TURNS_PER_CATCH_UP_FRAME = 4096;
	/** Steps a game plays on its own after it lost its host: two seconds of game time. */
	private static final int OFFLINE_GRACE_STEPS = 40;

	private final Turns turns;
	private final Game game;
	private final int checksumInterval;
	private final ChecksumSteps checksumSteps;
	private ReplayRecording recording;
	private int allowedUntil = -1;
	private int turnsApplied;
	private int checksumTick = -1;
	private int checksumValue;
	private boolean leaving;
	private boolean pauseReported;
	private int offlineSteps;
	private Runnable pausedListener;
	private int lastTurnNumber = -1;
	private LanPlay play;
	private Runnable restarter;
	private boolean restarting;
	private boolean wasCatchingUp;
	private IntConsumer tickListener;
	private CSimulation simulation;
	private War3MapViewer viewer;

	public LockstepTurnManager(final Turns turns, final Game game, final int checksumInterval) {
		this.turns = turns;
		this.game = game;
		this.checksumInterval = checksumInterval;
		final ChecksumSteps own = game.checksumSteps(checksumInterval);
		this.checksumSteps = own != null ? own : new ChecksumSteps() {
			@Override
			public boolean afterStep(final CSimulation unused) {
				return (LockstepTurnManager.this.game.tick() % LockstepTurnManager.this.checksumInterval) == 0;
			}

			@Override
			public int startTick() {
				return LockstepTurnManager.this.game.tick();
			}

			@Override
			public int value() {
				return checksum();
			}
		};
	}

	/** The manager of a LAN game that is played in the viewer. */
	public LockstepTurnManager(final War3MapViewer viewer, final LanClient client, final LanGameStart start) {
		this(new ClientTurns(client), new ViewerGame(viewer, start), start.checksumInterval());
		this.simulation = viewer.simulation;
		this.viewer = viewer;
		this.play = start.play();
	}

	/** Whether the game has ended for this player. */
	public boolean gameEnded() {
		return this.game.ended();
	}

	public LanPlay play() {
		return this.play;
	}

	/** Called (once, on the game thread) when the host says this game is not the others' game and is to be dropped. */
	public void setRestarter(final Runnable restarter) {
		this.restarter = restarter;
	}

	/**
	 * Whether the game is being dropped to be played again from its first turn. From the request on no turn is
	 * applied (they belong to the new game) and the player is not told to have left.
	 */
	private boolean restartRequested() {
		if (this.restarting) {
			return true;
		}
		if (this.play != null && this.restarter != null && this.play.takeRestart()) {
			this.restarting = true;
			this.leaving = true;
			if (this.viewer != null) {
				// it leaves the game's screen, which the render thread does, outside the step
				this.viewer.onRenderThreadLater(this.restarter);
			}
			else {
				this.restarter.run();
			}
			return true;
		}
		return false;
	}

	/** Where the waiting window, the presence of the players and the host's loss are kept; null in a test. */
	public void setPlay(final LanPlay play) {
		this.play = play;
	}

	/**
	 * Plays every turn that is waiting without drawing, until none is left; after the app was away the turns piled up
	 * and the frame pace alone would never catch up. Game thread.
	 */
	public void catchUp() {
		if ((this.viewer != null) && !this.viewer.isGameEnded()) {
			this.viewer.fastForwardTo(Integer.MAX_VALUE, null);
		}
	}

	/** The recording of the game, which is made with {@link #checksumFunction()}; null for none. */
	public void setRecording(final ReplayRecording recording) {
		this.recording = recording;
	}

	/**
	 * How the game's checksum is made. The recording asks for the same ones the host is sent, so they are made once.
	 */
	public ChecksumSteps checksumSteps() {
		return this.checksumSteps;
	}

	/** The game's checksum of this step, made once for the host and a recording. */
	public ToIntFunction<CSimulation> checksumFunction() {
		return simulation -> checksum();
	}

	private int checksum() {
		final int tick = this.game.tick();
		if (tick != this.checksumTick) {
			this.checksumValue = this.game.checksum();
			this.checksumTick = tick;
		}
		return this.checksumValue;
	}

	/** Told the step count after each step; tests use it to start their faults at a step. */
	public void setTickListener(final IntConsumer listener) {
		this.tickListener = listener;
	}

	/** The simulation of a manager made for a viewer. */
	public CSimulation simulation() {
		return this.simulation;
	}

	public int turnsApplied() {
		return this.turnsApplied;
	}

	@Override
	public int getLatestCompletedTurn() {
		final int tick = this.game.tick();
		if (restartRequested()) {
			return this.allowedUntil;
		}
		armCatchUp();
		while (tick > this.allowedUntil) {
			final TurnData turn = this.turns.poll();
			if (turn == null) {
				checkCaughtUp();
				if (this.play != null) {
					this.play.starved();
					checkHostLoss();
				}
				if (this.offlineSteps > 0) {
					// The host is gone: the game plays on alone for a moment, so its rules can react to who left.
					this.offlineSteps--;
					this.allowedUntil = tick;
				}
				return this.allowedUntil;
			}
			applyTurn(turn);
		}
		return this.allowedUntil;
	}

	/**
	 * Takes the turns the host sends while the game stands still. They hold no steps, only the commands (and the
	 * presence changes) that end the stop; a turn that does hold steps, sent before the host knew of the stop, is
	 * applied too, and its steps run after the stop. Every machine does the same in the same order.
	 */
	@Override
	public void whilePaused() {
		if (restartRequested()) {
			return;
		}
		checkCaughtUp();
		if (this.play != null) {
			this.play.idle();
			checkHostLoss();
		}
		if (this.pausedListener != null) {
			this.pausedListener.run();
		}
		final int most = this.turns.catchingUp() ? MAX_TURNS_PER_CATCH_UP_FRAME : MAX_TURNS_PER_PAUSED_FRAME;
		for (int i = 0; (i < most) && this.game.simPaused(); i++) {
			final TurnData turn = this.turns.poll();
			if (turn == null) {
				break;
			}
			applyTurn(turn);
		}
		reportPause();
	}

	/**
	 * A player given the game so far plays it through without drawing, as a replay skips ahead, until it is within a
	 * few turns of the host's game; then the host is told, and the game goes on drawn at its own pace.
	 */
	private void armCatchUp() {
		if (this.viewer != null && this.turns.catchingUp() && !this.viewer.isFastForwarding()
				&& !this.game.simPaused()) {
			this.viewer.fastForwardTo(Integer.MAX_VALUE, null);
		}
	}

	private void checkCaughtUp() {
		final boolean catching = this.turns.catchingUp();
		if (!catching) {
			if (this.wasCatchingUp) {
				this.wasCatchingUp = false;
				if (this.play != null) {
					this.play.setCatchUp(false, 0, 0);
				}
				// The pauses of the past were not reported; the game's present state is, now that it is the present.
				this.pauseReported = false;
				reportPause();
			}
			return;
		}
		this.wasCatchingUp = true;
		final int tick = this.game.tick();
		if (this.play != null && LanRejoin.progressDue(tick)) {
			this.play.setCatchUp(true, tick, this.turns.catchUpSteps());
		}
		if (LanRejoin.caughtUp(this.lastTurnNumber, this.turns.catchUpTotal(), this.turns.received(), tick,
				this.allowedUntil)) {
			if (this.lastTurnNumber >= 0) {
				this.turns.processed(this.lastTurnNumber);
			}
			this.turns.caughtUp();
		}
	}

	private void applyTurn(final TurnData turn) {
		apply(turn);
		// A turn behind unfinished steps adds its own to them.
		this.allowedUntil = Math.max(this.allowedUntil, this.game.tick() - 1) + turn.steps;
		this.turnsApplied++;
		this.lastTurnNumber = turn.n;
		if (turn.steps == 0) {
			this.turns.processed(turn.n);
		}
		if (this.play != null) {
			this.play.fed();
		}
		reportPause();
	}

	/** Called each frame the game stands still; tests use it to end a pause after a while. */
	public void setPausedListener(final Runnable listener) {
		this.pausedListener = listener;
	}

	/**
	 * The host's connection is gone: its player leaves the game here, as if the turns had said so. When the host was
	 * the only opponent the map's rules end the game with the win.
	 */
	private void checkHostLoss() {
		final int host = this.play.takeHostLoss();
		if (host >= 0) {
			this.play.onPresence(host, LanProtocol.PRESENCE_LEFT);
			leave(host);
			this.offlineSteps = OFFLINE_GRACE_STEPS;
		}
	}

	private void leave(final int participant) {
		final int slot = this.game.slotOf(participant);
		final CPlayerUnitOrderListener applier = slot < 0 ? null : this.game.player(slot);
		if (applier != null) {
			applier.leaveGame();
		}
	}

	private void reportPause() {
		if (this.turns.catchingUp()) {
			return;
		}
		final boolean paused = this.game.simPaused();
		if (paused != this.pauseReported) {
			this.pauseReported = paused;
			this.turns.simPaused(paused);
		}
	}

	@Override
	public void turnCompleted(final int gameTurnTick) {
		checkCaughtUp();
		if (this.recording != null) {
			this.recording.afterStep();
		}
		if (this.checksumSteps.afterStep(this.simulation)) {
			this.turns.checksum(this.checksumSteps.startTick(), this.checksumSteps.value());
		}
		if (gameTurnTick == (this.allowedUntil + 1)) {
			this.turns.processed(this.lastTurnNumber);
		}
		reportPause();
		if (this.tickListener != null) {
			this.tickListener.accept(gameTurnTick);
		}
		if (this.game.ended() && !this.leaving) {
			this.leaving = true;
			this.turns.leave();
		}
	}

	@Override
	public void framesSkipped(final float skippedCount) {
	}

	private void apply(final TurnData turn) {
		for (final CmdEntry command : turn.cmds) {
			final CPlayerUnitOrderListener applier = this.game.player(command.slot);
			if (applier == null) {
				continue;
			}
			try {
				CPlayerCommandCodec.decode(command.bytes, applier);
			}
			catch (final IllegalArgumentException e) {
				Log.error("LAN: broken command of slot " + command.slot + " in turn " + turn.n + ": " + e.getMessage());
			}
		}
		for (final PresenceEntry change : turn.presence) {
			if (this.play != null) {
				this.play.onPresence(change.participant, change.state);
			}
			if (change.state == LanProtocol.PRESENCE_LEFT) {
				leave(change.participant);
			}
		}
	}

	private static final class ClientTurns implements Turns {
		private final LanClient client;

		ClientTurns(final LanClient client) {
			this.client = client;
			// A game that took the place of a dropped one gets the turns from here on.
			client.resumeTurns();
		}

		@Override
		public TurnData poll() {
			return this.client.pollTurn();
		}

		@Override
		public void processed(final int turn) {
			// While the game is played through, every few turns tell the host enough; it does not wait for this player.
			if (this.client.isCatchingUp() && (turn & 15) != 0 && turn < this.client.lastTurn()) {
				return;
			}
			this.client.turnProcessed(turn);
		}

		@Override
		public boolean catchingUp() {
			return this.client.isCatchingUp();
		}

		@Override
		public int catchUpTotal() {
			return this.client.catchUpTotal();
		}

		@Override
		public long catchUpSteps() {
			return this.client.catchUpSteps();
		}

		@Override
		public int received() {
			return this.client.lastTurn();
		}

		@Override
		public void caughtUp() {
			this.client.caughtUp();
		}

		@Override
		public void checksum(final int tick, final int crc) {
			this.client.checksum(tick, crc);
		}

		@Override
		public void leave() {
			this.client.leave(LanProtocol.LEAVE_NORMAL);
		}

		@Override
		public void simPaused(final boolean paused) {
			this.client.simPaused(paused);
		}
	}

	/** The game of a map viewer; the slot of a player is the player of the same number. */
	private static final class ViewerGame implements Game {
		private final War3MapViewer viewer;
		private final CSimulation simulation;
		private final LanGameStart start;
		private final Map<Integer, CPlayerUnitOrderListener> appliers = new HashMap<Integer, CPlayerUnitOrderListener>();

		ViewerGame(final War3MapViewer viewer, final LanGameStart start) {
			this.viewer = viewer;
			this.simulation = viewer.simulation;
			this.start = start;
		}

		@Override
		public int tick() {
			return this.simulation.getGameTurnTick();
		}

		@Override
		public int checksum() {
			return SimulationChecksum.compute(this.simulation);
		}

		@Override
		public ChecksumSteps checksumSteps(final int interval) {
			return SimulationChecksum.sliced(interval);
		}

		@Override
		public boolean ended() {
			return this.viewer.isGameEnded();
		}

		@Override
		public boolean simPaused() {
			return this.viewer.isGamePaused(War3MapViewer.PauseSource.PLAYER)
					|| this.viewer.isGamePaused(War3MapViewer.PauseSource.SCRIPT);
		}

		@Override
		public CPlayerUnitOrderListener player(final int named) {
			// What a referee gives is done from the seat the watchers watch from.
			final int slot = named >= LanProtocol.WATCHER_SLOT_BASE ? this.start.watchSlot() : named;
			if ((slot < 0) || (slot >= this.simulation.getGameData().getMaxPlayers())) {
				return null;
			}
			CPlayerUnitOrderListener applier = this.appliers.get(slot);
			if (applier == null) {
				applier = new CPlayerCommandRecorder(this.simulation, slot,
						new CPlayerUnitOrderExecutor(this.simulation, slot));
				this.appliers.put(slot, applier);
			}
			return applier;
		}

		@Override
		public int slotOf(final int participant) {
			final ParticipantInfo who = this.start.setup().participant(participant);
			return who == null || who.slot == LanProtocol.NO_SLOT ? -1 : who.slot;
		}
	}
}
