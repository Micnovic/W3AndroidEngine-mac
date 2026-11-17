package com.etheller.warsmash.lan;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplay;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.PointAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.MenuUI;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.protocol.LanData.SlotInfo;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.transport.LanFaultInjector;

/**
 * A LAN game with no launcher, started from system properties, for tests (two headless engines on one machine):
 * <ul>
 * <li>-Dwarsmash.lan.host=&lt;port&gt; (empty for the default port) hosts the map given with -loadfile and joins its
 * own game; the computer slots of -computer ("slot:race,...") are the game's computers;</li>
 * <li>-Dwarsmash.lan.join=&lt;address:port&gt; joins that host (the map is -loadfile, the same one); "search" or
 * "search:&lt;game name&gt;" joins the first game found by broadcast instead;</li>
 * <li>-Dwarsmash.lan.announce=1: a host announces its game by broadcast;</li>
 * <li>-Dwarsmash.lan.steps=&lt;n&gt;: from that step on the program closes the LAN session and exits
 * (the Android test entry, which has no -steps; see {@link LanProperties});</li>
 * <li>-Dwarsmash.lan.auto=1 takes a seat, says ready, and as the host starts the game when
 * -Dwarsmash.lan.players (default 2) humans are ready;</li>
 * <li>-Dwarsmash.lan.name, .race (0 random, 1..4), .team, .color: the player's own lobby settings;</li>
 * <li>-Dwarsmash.lan.turnms (host: the length of a turn, default 100), .synclimit, .countdown (ms), .exitdelay (ms the
 * program waits before it ends, so the others get the turns already sent);</li>
 * <li>-Dwarsmash.lan.fault=delay:150,jitter:50,stall:5000@tick1200,cut@tick2400: network faults of this machine's
 * network thread (the host's when it hosts); the ones at a tick start when the game reaches it;</li>
 * <li>-Dwarsmash.lan.feed=&lt;replay&gt;[#player]: the commands of that player of the replay (default the local one)
 * are this player's input, each sent when the game reaches its step;</li>
 * <li>-Dwarsmash.lan.bot=&lt;steps&gt;: every that many steps a unit of the player is sent to a random place;</li>
 * <li>-Dwarsmash.lan.pauses=&lt;step,step,...&gt;: the player pauses the game at these steps and ends each pause after
 * -Dwarsmash.lan.pausems (default 800) of waiting;</li>
 * <li>-Dwarsmash.lan.leave=&lt;step&gt;: the player leaves the game at that step; .crash=&lt;step&gt;: the program dies
 * at that step without a word;</li>
 * <li>-Dwarsmash.lan.keyfile=&lt;file&gt;: the key to the game is kept in that file; -Dwarsmash.lan.rejoin=&lt;file&gt; with
 * .join comes back to the running game with the key in that file (the program was started over);</li>
 * <li>-Dwarsmash.lan.chat=&lt;step:text&gt;: the player says the text to everyone at that step;</li>
 * <li>-Dwarsmash.lan.lobbyquit=&lt;n&gt;: the host leaves the lobby (and the program ends) as soon as n humans are in
 * it, instead of starting the game; -Dwarsmash.lan.retire=1: a player that stops hands the game on the way a leaving
 * host does (see {@link LanSession#retire}) instead of closing at once;</li>
 * <li>-Dwarsmash.lan.desync=&lt;step&gt;: at that step this player's game is changed (one gold more) so that its sums
 * differ from the others'; once in this program's life, or in every game it plays again with
 * -Dwarsmash.lan.desync.repeat=1 (a difference the replay reproduces: the player is given up);</li>
 * <li>-Dwarsmash.lan.absentms, .continuems, .claimms, .lagms, .hostgonems, .migratems: the protocol's time limits,
 * shortened.</li>
 * </ul>
 */
public final class LanHeadless {
	private static LanHeadless active;

	private MenuUI menu;
	private final String mapFile;
	private final String computerSlots;
	private volatile boolean hosting;
	private final boolean auto;
	private final int lobbyQuit;
	private boolean lobbyQuitDone;
	private final int humans;
	private final DataSource data;
	private final LanSession session;
	private LanGameStart start;
	private volatile boolean failed;
	private volatile boolean finishing;
	private boolean slotRequested;
	private boolean computersSet;
	private boolean startRequested;
	private int expectedComputers;
	private final List<long[]> faultSchedule = new ArrayList<long[]>();
	private int nextFault;
	private List<CReplay.Command> feed;
	private int feedNext;
	private int feedPlayer = -1;
	private int botInterval;
	private Random botRandom;
	private int ticks;
	private int stopAt;
	private boolean stopping;
	private int[] pauseSteps = new int[0];
	private int nextPause;
	private long pausedSince;
	private boolean unpauseSent;
	private int leaveAt = -1;
	private int desyncAt = -1;
	private LanGameStart desyncGame;
	private static boolean desyncDone;
	private volatile boolean restarting;
	private int crashAt = -1;
	private int chatAt = -1;
	private String chatText = "";
	private boolean resultLogged;
	private volatile long catchUpSince;

	public static boolean requested() {
		return (System.getProperty("warsmash.lan.host") != null) || (System.getProperty("warsmash.lan.join") != null);
	}

	/** Whether a LAN game is being played (or was refused) by this program. */
	public static boolean isActive() {
		return active != null;
	}

	public static boolean hasFailed() {
		return (active != null) && active.failed;
	}

	/** Whether the game on the screen was dropped to be played again and the new one has not appeared yet. */
	public static boolean isRestarting() {
		return (active != null) && active.restarting;
	}

	/** The new game after a restart has appeared. */
	public static void restarted() {
		if (active != null) {
			active.restarting = false;
		}
	}

	/** The step the local game has reached, or -1 before it started. */
	public static int ticks() {
		return active == null ? -1 : active.ticks;
	}

	/**
	 * The engine started over inside this process (the graphics were lost): the running LAN game is rejoined from its
	 * first turn instead of a second session being made.
	 */
	public static void resume(final MenuUI menu) {
		final LanHeadless run = active;
		if (run == null) {
			return;
		}
		run.menu = menu;
		run.start = null;
		run.ticks = 0;
		run.session.client().rejoinFromStart();
	}

	public static void begin(final MenuUI menu, final String mapFile, final String computerSlots) {
		try {
			active = new LanHeadless(menu, mapFile, computerSlots);
			active.connect();
		}
		catch (final IOException | RuntimeException e) {
			Log.error("LAN: cannot begin: " + e);
			Log.error(e);
			if (active != null) {
				active.fail("the LAN game cannot begin");
			}
		}
	}

	/** Ends the network side after the game; waits a moment so the turns sent reach the other players. */
	public static void finish() {
		if (active == null) {
			return;
		}
		active.finishing = true;
		final long delay = Long.getLong("warsmash.lan.exitdelay", 1500L);
		try {
			Thread.sleep(delay);
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		LanSessions.endNow(active.session);
	}

	private LanHeadless(final MenuUI menu, final String mapFile, final String computerSlots) {
		this.menu = menu;
		this.mapFile = mapFile;
		this.computerSlots = computerSlots;
		this.hosting = System.getProperty("warsmash.lan.host") != null;
		this.lobbyQuit = Integer.getInteger("warsmash.lan.lobbyquit", 0);
		this.auto = Boolean.parseBoolean(System.getProperty("warsmash.lan.auto", "false"))
				|| "1".equals(System.getProperty("warsmash.lan.auto"));
		this.humans = Integer.getInteger("warsmash.lan.players", 2);
		this.data = menu.lanDataSource();
		final net.warsmash.lan.protocol.LanTimings timings = new net.warsmash.lan.protocol.LanTimings();
		timings.absentAfterMs = Integer.getInteger("warsmash.lan.absentms", timings.absentAfterMs);
		timings.continueAfterMs = Integer.getInteger("warsmash.lan.continuems", timings.continueAfterMs);
		timings.claimAfterMs = Integer.getInteger("warsmash.lan.claimms", timings.claimAfterMs);
		timings.lagAfterMs = Integer.getInteger("warsmash.lan.lagms", timings.lagAfterMs);
		timings.hostGoneMs = Integer.getInteger("warsmash.lan.hostgonems", timings.hostGoneMs);
		timings.migrateWaitMs = Integer.getInteger("warsmash.lan.migratems", timings.migrateWaitMs);
		timings.loadTimeoutMs = Integer.getInteger("warsmash.lan.loadms", timings.loadTimeoutMs);
		this.session = new LanSession(timings, LanSessions.network());
		this.stopAt = Integer.getInteger("warsmash.lan.steps", -1);
		final String pauses = System.getProperty("warsmash.lan.pauses", "");
		if (!pauses.isEmpty()) {
			final String[] parts = pauses.split(",");
			this.pauseSteps = new int[parts.length];
			for (int i = 0; i < parts.length; i++) {
				this.pauseSteps[i] = Integer.parseInt(parts[i].trim());
			}
		}
		this.leaveAt = Integer.getInteger("warsmash.lan.leave", -1);
		this.crashAt = Integer.getInteger("warsmash.lan.crash", -1);
		this.desyncAt = Integer.getInteger("warsmash.lan.desync", -1);
		final String chat = System.getProperty("warsmash.lan.chat", "");
		if (chat.indexOf(':') > 0) {
			this.chatAt = Integer.parseInt(chat.substring(0, chat.indexOf(':')));
			this.chatText = chat.substring(chat.indexOf(':') + 1);
		}
	}

	private void connect() throws IOException {
		LanSessions.begin(this.session);
		final String keyFile = System.getProperty("warsmash.lan.keyfile");
		if ((keyFile != null) && !keyFile.isEmpty()) {
			this.session.setRejoinStore(new FileKey(new File(keyFile)));
		}
		final String fault = System.getProperty("warsmash.lan.fault");
		if ((fault != null) && !fault.isEmpty()) {
			this.session.setFaults(parseFaults(fault));
		}
		final String feedSpec = System.getProperty("warsmash.lan.feed");
		if ((feedSpec != null) && !feedSpec.isEmpty()) {
			loadFeed(feedSpec);
		}
		this.botInterval = Integer.getInteger("warsmash.lan.bot", 0);
		this.session.addListener(new Events());
		final int port;
		if (this.hosting) {
			final LanGameStart.MapDetails details = LanGameStart.mapDetails(this.data, this.mapFile);
			final LanHost.Config hc = baseHostConfig();
			hc.options.gameName = "Headless";
			hc.options.observers = LanProtocol.OBSERVERS_REFEREES;
			hc.map = details.info;
			hc.maxSlots = details.playerSlots;
			final String hostPort = System.getProperty("warsmash.lan.host");
			hc.port = hostPort.isEmpty() ? LanProtocol.DEFAULT_PORT : Integer.parseInt(hostPort);
			this.session.setAnnounce("1".equals(System.getProperty("warsmash.lan.announce")));
			port = this.session.openHost(hc);
			Log.warn("LAN: hosting on port " + port);
			if (this.computerSlots != null) {
				this.expectedComputers = this.computerSlots.split(",").length;
			}
		}
		else {
			this.session.setHostTemplate(baseHostConfig());
			final String join = System.getProperty("warsmash.lan.join");
			if (join.startsWith("search")) {
				searchAndJoin(join.length() > 7 ? join.substring(7) : "");
				return;
			}
			final int colon = join.lastIndexOf(':');
			port = colon < 0 ? LanProtocol.DEFAULT_PORT : Integer.parseInt(join.substring(colon + 1));
			final String address = colon < 0 ? join : join.substring(0, colon);
			final LanClient.Config config = clientConfig();
			final String rejoin = System.getProperty("warsmash.lan.rejoin");
			if ((rejoin != null) && !rejoin.isEmpty()) {
				config.rejoinKey = net.warsmash.lan.RejoinKey.load(new File(rejoin));
				if (config.rejoinKey == null) {
					fail("no key to rejoin with in " + rejoin);
					return;
				}
				Log.warn("LAN: rejoining game " + Long.toHexString(config.rejoinKey.gameId));
			}
			this.session.connect(config, new InetSocketAddress(address, port));
			return;
		}
		this.session.connect(clientConfig(), new InetSocketAddress("127.0.0.1", port));
	}

	/** What a host of this run is like; the one a guest makes when it takes the game over has the same. */
	private LanHost.Config baseHostConfig() throws IOException {
		final LanHost.Config hc = new LanHost.Config();
		hc.engineVersion = LanGameStart.localVersion();
		hc.buildCommit = LanGameStart.localCommit();
		hc.officialBuild = LanGameStart.localOfficial();
		hc.dataChecksum = LanGameStart.dataChecksum(this.data);
		hc.turnIntervalMs = Integer.getInteger("warsmash.lan.turnms", 100);
		hc.syncLimit = Integer.getInteger("warsmash.lan.synclimit", 10);
		hc.countdownMs = Integer.getInteger("warsmash.lan.countdown", this.auto ? 0 : 5000);
		hc.commandValidator = new LanCommandValidator();
		hc.header = new java.util.function.Supplier<byte[]>() {
			@Override
			public byte[] get() {
				return LanGameStart.header(System.currentTimeMillis());
			}
		};
		return hc;
	}

	/** Joins the first game in the lobby (named so, when a name is given) that an announcement reaches. */
	private void searchAndJoin(final String gameName) throws IOException {
		final java.util.concurrent.atomic.AtomicBoolean found = new java.util.concurrent.atomic.AtomicBoolean();
		this.session.search(new net.warsmash.lan.transport.LanDiscovery.Listener() {
			@Override
			public void onGameFound(final net.warsmash.lan.transport.LanDiscovery.Found game) {
				if (game.info.state != LanProtocol.STATE_LOBBY
						|| (!gameName.isEmpty() && !gameName.equals(game.info.gameName))
						|| !found.compareAndSet(false, true)) {
					return;
				}
				Log.warn("LAN: found \"" + game.info.gameName + "\" at " + game.address.getHostAddress() + ":"
						+ game.info.tcpPort);
				new Thread("lan-join") {
					@Override
					public void run() {
						try {
							LanHeadless.this.session.stopSearch();
							LanHeadless.this.session.connect(clientConfig(),
									new InetSocketAddress(game.address, game.info.tcpPort));
						}
						catch (final IOException e) {
							fail("cannot join the game found: " + e);
						}
					}
				}.start();
			}

			@Override
			public void onGameLost(final long gameId) {
			}
		});
	}

	private LanClient.Config clientConfig() throws IOException {
		final LanClient.Config c = new LanClient.Config();
		c.engineVersion = LanGameStart.localVersion();
		c.buildCommit = LanGameStart.localCommit();
		c.officialBuild = LanGameStart.localOfficial();
		c.dataChecksum = LanGameStart.dataChecksum(this.data);
		c.playerName = System.getProperty("warsmash.lan.name", this.hosting ? "Host" : "Guest");
		final String role = System.getProperty("warsmash.lan.role", "");
		c.wantedRole = "referee".equals(role) ? LanProtocol.ROLE_REFEREE
				: "observer".equals(role) ? LanProtocol.ROLE_OBSERVER : LanProtocol.ROLE_PLAYER;
		return c;
	}

	private void fail(final String why) {
		Log.error("LAN: " + why);
		this.failed = true;
		com.badlogic.gdx.Gdx.app.exit();
	}

	// ------------------------------------------------------------------ events

	private final class Events extends LanClient.Listener {
		@Override
		public void onWelcome(final LanMessages.Welcome welcome) {
			Log.warn("LAN: welcome as participant " + welcome.participantId);
		}

		@Override
		public void onReject(final LanMessages.Reject reject) {
			fail("rejected: " + reject.text);
		}

		@Override
		public void onKicked(final String reason) {
			fail("kicked: " + reason);
		}

		@Override
		public void onLobby(final LanMessages.LobbyState lobby) {
			lobbyChanged(lobby);
		}

		@Override
		public void onStart(final LanGameSetup setup) {
			started(setup);
		}

		@Override
		public void onRejoin(final LanGameSetup setup, final int from, final int total) {
			Log.warn("LAN: rejoin, " + total + " turns to play through");
			LanHeadless.this.catchUpSince = System.nanoTime();
			if (LanHeadless.this.start == null) {
				started(setup);
			}
		}

		@Override
		public void onCaughtUp() {
			Log.warn("LAN: caught up at step " + LanHeadless.this.ticks);
			if (LanHeadless.this.catchUpSince != 0) {
				Log.warn("LAN: caught up " + LanHeadless.this.ticks + " steps in "
						+ ((System.nanoTime() - LanHeadless.this.catchUpSince) / 1000000L) + " ms");
				LanHeadless.this.catchUpSince = 0;
			}
		}

		@Override
		public void onResync(final int kind, final int tick) {
			if (kind == LanProtocol.RESYNC_GIVE_UP) {
				Log.error("LAN: cannot stay in sync with this game (step " + tick + ")");
				fail("cannot stay in sync with this game");
			}
			else {
				Log.warn("LAN: out of sync (step " + tick + "), the game is played again");
			}
		}

		@Override
		public void onReconnecting() {
			Log.warn("LAN: the link broke, reconnecting");
		}

		@Override
		public void onReconnected() {
			Log.warn("LAN: reconnected");
		}

		@Override
		public void onMigrating() {
			Log.warn("LAN: the host is gone, looking for a new one");
		}

		@Override
		public void onHostChanged(final int newHost, final int firstTurn) {
			Log.warn("LAN: participant " + newHost + " is the host now, turns go on from " + firstTurn);
		}

		@Override
		public void onPromoted(final LanHost host) {
			Log.warn("LAN: this player took the game over as its host");
			LanHeadless.this.hosting = true;
			LanHeadless.this.expectedComputers = -1;
		}

		@Override
		public void onGo() {
			Log.warn("LAN: go");
			if (LanHeadless.this.start != null) {
				LanHeadless.this.start.onGo();
			}
		}

		@Override
		public void onDesync(final LanMessages.Desync desync) {
			Log.error("LAN: DESYNC at step " + desync.tick);
		}

		@Override
		public void onLag(final List<net.warsmash.lan.protocol.LanData.LagEntry> entries) {
			Log.warn("LAN: waiting for " + entries.size() + " players");
		}

		@Override
		public void onGameEnd(final int reason) {
			Log.warn("LAN: game ended, reason " + reason);
		}

		@Override
		public void onDisconnected(final String reason) {
			if (!LanHeadless.this.finishing) {
				// The game decides what the loss of the host means (a lone opponent's leaving gives the win); a game
				// that cannot go on is ended here after a while.
				Log.warn("LAN: lost the host: " + reason);
				final Thread watchdog = new Thread("lan-host-lost") {
					@Override
					public void run() {
						try {
							Thread.sleep(Long.getLong("warsmash.lan.hostlostms", 20000L));
						}
						catch (final InterruptedException e) {
							return;
						}
						if (!LanHeadless.this.finishing) {
							fail("the game did not end after the host was lost");
						}
					}
				};
				watchdog.setDaemon(true);
				watchdog.start();
			}
		}
	}

	private void lobbyChanged(final LanMessages.LobbyState lobby) {
		final LanClient client = this.session.client();
		final int me = client.participantId();
		SlotInfo mine = null;
		int humanSlots = 0;
		int readyHumans = 0;
		int computers = 0;
		for (final SlotInfo s : lobby.slots) {
			if (s.kind == LanProtocol.SLOT_HUMAN) {
				humanSlots++;
				if (s.ready) {
					readyHumans++;
				}
				if (s.participantId == me) {
					mine = s;
				}
			}
			else if (s.kind == LanProtocol.SLOT_COMPUTER) {
				computers++;
			}
		}
		if (!this.auto || (mine == null)) {
			return;
		}
		if (!this.slotRequested) {
			this.slotRequested = true;
			final int defaultRace = this.hosting ? 1 : 2;
			client.slotRequest(mine.index, Integer.getInteger("warsmash.lan.race", defaultRace),
					Integer.getInteger("warsmash.lan.team", mine.team),
					Integer.getInteger("warsmash.lan.color", mine.color), 100);
			return;
		}
		if (this.hosting && !this.computersSet) {
			this.computersSet = true;
			if (this.computerSlots != null) {
				for (final String slot : this.computerSlots.split(",")) {
					client.hostSlot(Integer.parseInt(slot.split(":")[0].trim()), LanProtocol.ACTION_COMPUTER, 1);
				}
			}
			return;
		}
		if (!mine.ready) {
			client.setReady(true);
			return;
		}
		if (this.hosting && this.lobbyQuit > 0 && !this.lobbyQuitDone && humanSlots >= this.lobbyQuit
				&& System.getProperty("warsmash.lan.host") != null) {
			this.lobbyQuitDone = true;
			Log.warn("LAN: leaving the lobby with " + humanSlots + " humans in it");
			new Thread("lan-lobby-quit") {
				@Override
				public void run() {
					try {
						Thread.sleep(Long.getLong("warsmash.lan.lobbyquitms", 800L));
					}
					catch (final InterruptedException e) {
						return;
					}
					LanHeadless.this.finishing = true;
					LanHeadless.this.session.client().leave(LanProtocol.LEAVE_NORMAL);
					LanSessions.endNow(LanHeadless.this.session);
					com.badlogic.gdx.Gdx.app.postRunnable(new Runnable() {
						@Override
						public void run() {
							com.badlogic.gdx.Gdx.app.exit();
						}
					});
				}
			}.start();
			return;
		}
		if (this.hosting && !this.startRequested && (humanSlots == this.humans) && (readyHumans == humanSlots)
				&& (this.expectedComputers < 0 || computers == this.expectedComputers)
				&& (this.lobbyQuit == 0 || System.getProperty("warsmash.lan.host") == null)) {
			this.startRequested = true;
			Log.warn("LAN: starting with " + humanSlots + " players and " + computers + " computers");
			client.requestStart();
		}
	}

	private void started(final LanGameSetup setup) {
		Log.warn("LAN: start, seed " + setup.seed + ", " + setup.slots.size() + " slots");
		try {
			if (!LanGameStart.sameMap(this.data, this.mapFile, setup.map)) {
				fail("this machine's map is not the host's");
				return;
			}
		}
		catch (final IOException e) {
			fail("cannot read the map: " + e);
			return;
		}
		this.start = new LanGameStart(this.session.client(), setup, this.mapFile);
		if (this.botInterval > 0) {
			this.botRandom = new Random(setup.seed ^ (this.start.localSlot() * 7919L));
		}
		this.start.setTickListener(this::onStep);
		this.start.setPausedListener(this::onPausedFrame);
		this.start.setRestarter(this::restartGame);
		if (!this.menu.startLan(this.start)) {
			fail("the game cannot be started");
		}
	}

	// -------------------------------------------------------------------- game

	/** This game is not the others': drop it and play the session's turns from the first one in a new game. */
	private void restartGame() {
		Log.warn("LAN: the game is played again from the first turn");
		this.restarting = true;
		this.pausedSince = 0;
		this.unpauseSent = false;
		if (!this.menu.restartRunningGame(() -> {
			this.ticks = 0;
			started(this.session.client().setup());
		})) {
			fail("there is no game to play again");
		}
	}

	private void onPausedFrame() {
		final long now = System.currentTimeMillis();
		if (this.pausedSince == 0) {
			this.pausedSince = now;
			Log.warn("LAN: the game is paused at step " + this.ticks);
		}
		else if (!this.unpauseSent && (now - this.pausedSince) >= Long.getLong("warsmash.lan.pausems", 800L)) {
			this.unpauseSent = true;
			this.start.commandSender().setGamePaused(false);
		}
	}

	private void onStep(final int tick) {
		this.ticks = tick;
		if (this.pausedSince != 0) {
			Log.warn("LAN: the game runs again at step " + tick);
			this.pausedSince = 0;
			this.unpauseSent = false;
		}
		if ((this.desyncAt >= 0) && (tick >= this.desyncAt) && !this.restarting && (this.desyncGame != this.start)
				&& (!desyncDone || "1".equals(System.getProperty("warsmash.lan.desync.repeat")))) {
			desyncDone = true;
			this.desyncGame = this.start;
			Log.warn("LAN: changing this game at step " + tick + " to make it differ");
			this.start.turnManager().simulation().getPlayer(this.start.viewSlot()).addGold(7);
		}
		if ((this.crashAt >= 0) && (tick >= this.crashAt)) {
			Log.warn("LAN: crashing at step " + tick);
			Runtime.getRuntime().halt(9);
		}
		if ((this.leaveAt >= 0) && (tick >= this.leaveAt) && !this.stopping) {
			Log.warn("LAN: leaving the game at step " + tick);
			this.session.client().leave(LanProtocol.LEAVE_QUIT);
			this.leaveAt = -1;
			stopGame(tick);
			return;
		}
		// A game played through again after a rejoin repeats nothing the player did the first time.
		final boolean replaying = this.session.client().isCatchingUp();
		if ((this.nextPause < this.pauseSteps.length) && (tick >= this.pauseSteps[this.nextPause])) {
			this.nextPause++;
			if (!replaying) {
				Log.warn("LAN: pausing the game at step " + tick);
				this.start.commandSender().setGamePaused(true);
			}
		}
		if ((this.chatAt >= 0) && (tick >= this.chatAt)) {
			this.chatAt = -1;
			if (!replaying) {
				this.start.commandSender().chatMessage(0, -1, this.chatText);
			}
		}
		final String result = this.resultLogged ? "" : localResult();
		if (!this.resultLogged && !"none".equals(result) && !"NEUTRAL".equals(result)) {
			this.resultLogged = true;
			Log.warn("LAN: the game ended at step " + tick + ", result " + result);
			if (!this.stopping) {
				stopGame(tick);
			}
		}
		if ((this.stopAt >= 0) && (tick >= this.stopAt) && !this.stopping) {
			stopGame(tick);
		}
		while ((this.nextFault < this.faultSchedule.size()) && (this.faultSchedule.get(this.nextFault)[0] <= tick)) {
			final long[] fault = this.faultSchedule.get(this.nextFault++);
			final LanFaultInjector injector = this.session.faults();
			Log.warn("LAN: fault " + (fault[1] == 0 ? "stall " + fault[2] + " ms" : "cut") + " at step " + tick);
			if (fault[1] == 0) {
				injector.freeze(fault[2]);
			}
			else {
				injector.cut();
			}
		}
		if (this.feed != null) {
			feed(tick, replaying);
		}
		if ((this.botInterval > 0) && ((tick % this.botInterval) == 0) && (tick > 0) && !replaying) {
			bot();
		}
	}

	private String localResult() {
		try {
			final com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerGameResult result = this.start
					.turnManager().simulation().getPlayer(this.start.viewSlot()).getRemovedResult();
			return result == null ? "none" : result.name();
		}
		catch (final RuntimeException e) {
			return "unknown";
		}
	}

	/**
	 * Ends the program after a moment for the turns already sent. The game is not ended (that would send a LEAVE the
	 * slower players still have to play through); the replay closes at the step the program stops at.
	 */
	private void stopGame(final int tick) {
		this.stopping = true;
		this.finishing = true;
		Log.warn("LAN: stopping at step " + tick);
		final long delay = Long.getLong("warsmash.lan.exitdelay", 1500L);
		new Thread("lan-exit") {
			@Override
			public void run() {
				try {
					Thread.sleep(delay);
				}
				catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				if (Boolean.getBoolean("warsmash.lan.retire") || "1".equals(System.getProperty("warsmash.lan.retire"))) {
					LanSessions.end(LanHeadless.this.session);
					LanHeadless.this.session.awaitClosed(5000);
				}
				else {
					LanSessions.endNow(LanHeadless.this.session);
				}
				com.badlogic.gdx.Gdx.app.postRunnable(new Runnable() {
					@Override
					public void run() {
						com.badlogic.gdx.Gdx.app.exit();
					}
				});
			}
		}.start();
	}

	private void feed(final int tick, final boolean replaying) {
		while ((this.feedNext < this.feed.size()) && (this.feed.get(this.feedNext).getTick() <= tick)) {
			final CReplay.Command command = this.feed.get(this.feedNext++);
			if (replaying) {
				continue;
			}
			if ((command.getPlayerIndex() == this.feedPlayer) || ((this.feedPlayer < 0)
					&& (command.getPlayerIndex() == this.start.localSlot()))) {
				this.session.client().sendCommand(command.getBytes());
			}
		}
	}

	/** Sends one of the player's units that can move to a place near it. */
	private void bot() {
		final CSimulation game = this.start.turnManager().simulation();
		final int me = this.start.localSlot();
		if (me < 0) {
			return;
		}
		final List<CUnit> mine = new ArrayList<CUnit>();
		for (final CUnit unit : game.getUnits()) {
			if ((unit.getPlayerIndex() == me) && !unit.isDead() && !unit.isBuilding()) {
				mine.add(unit);
			}
		}
		if (mine.isEmpty()) {
			return;
		}
		final CUnit unit = mine.get(this.botRandom.nextInt(mine.size()));
		final float x = unit.getX() + ((this.botRandom.nextFloat() - 0.5f) * 1600f);
		final float y = unit.getY() + ((this.botRandom.nextFloat() - 0.5f) * 1600f);
		final CPlayerUnitOrderListener sender = this.start.commandSender();
		final AbilityPointTarget point = new AbilityPointTarget(x, y);
		for (final CAbility ability : unit.getAbilities()) {
			ability.checkCanUse(game, unit, me, OrderIds.smart, false,
					BooleanAbilityActivationReceiver.INSTANCE.reset());
			if (!BooleanAbilityActivationReceiver.INSTANCE.isOk()) {
				continue;
			}
			ability.checkCanTarget(game, unit, me, OrderIds.smart, false, point,
					PointAbilityTargetCheckReceiver.INSTANCE.reset());
			if (PointAbilityTargetCheckReceiver.INSTANCE.getTarget() != null) {
				sender.selectUnits(new int[0], new int[] { unit.getHandleId() });
				sender.issuePointOrder(unit.getHandleId(), ability.getHandleId(), OrderIds.smart, x, y, false);
				return;
			}
		}
	}

	// ------------------------------------------------------------ test options

	private void loadFeed(final String spec) throws IOException {
		final int hash = spec.lastIndexOf('#');
		final String file = hash < 0 ? spec : spec.substring(0, hash);
		this.feedPlayer = hash < 0 ? -1 : Integer.parseInt(spec.substring(hash + 1));
		try (InputStream in = new java.io.FileInputStream(new File(file))) {
			this.feed = CReplay.read(in).getCommands();
		}
		Log.warn("LAN: feeding " + this.feed.size() + " commands of " + file);
	}

	/** The key of the one game this run plays, in a file. */
	private static final class FileKey implements net.warsmash.lan.RejoinStore {
		private final File file;

		FileKey(final File file) {
			this.file = file;
		}

		@Override
		public net.warsmash.lan.RejoinKey load(final long gameId) {
			final net.warsmash.lan.RejoinKey key = net.warsmash.lan.RejoinKey.load(this.file);
			return key != null && key.gameId == gameId ? key : null;
		}

		@Override
		public void save(final net.warsmash.lan.RejoinKey key) {
			try {
				key.save(this.file);
			}
			catch (final IOException e) {
				Log.error("LAN: cannot keep the key: " + e);
			}
		}

		@Override
		public void clear(final long gameId) {
			// The file stays for the test that restarts a run, which reads it after the first run's end.
		}
	}

	/** Entries are {step, kind (0 stall, 1 cut), milliseconds}. */
	private LanFaultInjector parseFaults(final String spec) {
		final LanFaultInjector injector = new LanFaultInjector(Long.getLong("warsmash.lan.faultseed", 1L));
		int delay = 0;
		int jitter = 0;
		for (final String part : spec.split(",")) {
			final String item = part.trim();
			final int at = item.indexOf("@tick");
			final long tick = at < 0 ? -1 : Long.parseLong(item.substring(at + 5));
			final String name = at < 0 ? item : item.substring(0, at);
			if (name.startsWith("delay:")) {
				delay = Integer.parseInt(name.substring(6));
			}
			else if (name.startsWith("jitter:")) {
				jitter = Integer.parseInt(name.substring(7));
			}
			else if (name.startsWith("stall:") && (tick >= 0)) {
				this.faultSchedule.add(new long[] { tick, 0, Long.parseLong(name.substring(6)) });
			}
			else if ("cut".equals(name) && (tick >= 0)) {
				this.faultSchedule.add(new long[] { tick, 1, 0 });
			}
			else {
				throw new IllegalArgumentException("unknown fault " + item);
			}
		}
		injector.setDelay(delay, jitter);
		Log.warn("LAN: faults " + spec);
		this.faultSchedule.sort((a, b) -> Long.compare(a[0], b[0]));
		return injector;
	}
}
