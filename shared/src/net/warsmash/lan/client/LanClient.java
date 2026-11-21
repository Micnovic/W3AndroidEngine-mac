package net.warsmash.lan.client;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.RejoinKey;
import net.warsmash.lan.TurnArchive;
import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.maps.LanMapIndex;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanData.CmdEntry;
import net.warsmash.lan.protocol.LanData.TurnData;
import net.warsmash.lan.protocol.LanMessage;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanProtocolException;
import net.warsmash.lan.transport.LanConnection;
import net.warsmash.lan.transport.LanLog;
import net.warsmash.lan.transport.LanLoop;

/**
 * A participant's side: lobby copy, map check and download, turn intake and the command queue with sequence numbers.
 * A command stays
 * pending until a turn contains its sequence number, so a command is never lost and never executed twice, even over
 * a cut and a reconnect. Turns are exposed through {@link #pollTurn()} for the game thread.
 * <p>
 * With {@link Config#mapIndex} set, every announced map is looked up on this device and downloaded from the host if
 * missing; the host is told the outcome and does not accept READY before {@link #MAP_READY}.
 */
public final class LanClient {
	/** Map states reported to {@link Listener#onMapState}. */
	public static final int MAP_CHECKING = 0;
	public static final int MAP_DOWNLOADING = 1;
	public static final int MAP_VERIFYING = 2;
	public static final int MAP_READY = 3;
	public static final int MAP_FAILED = 4;

	/** The reason the client's disconnect event carries when the player left on purpose. */
	public static final String LEFT_REASON = "left";

	/** What the player sees when a download fails; the cause is in the failure code. */
	public static final String MAP_FAILED_TEXT = "Map download failed";

	/** Event sink; override what you need. Delivered through the configured executor. */
	public abstract static class Listener {
		public void onWelcome(final LanMessages.Welcome welcome) {
		}

		public void onLobby(final LanMessages.LobbyState lobby) {
		}

		public void onChat(final LanMessages.Chat chat) {
		}

		public void onReject(final LanMessages.Reject reject) {
		}

		public void onKicked(final String reason) {
		}

		public void onCountdown(final int seconds) {
		}

		public void onCountdownCancel() {
		}

		public void onStart(final LanGameSetup setup) {
		}

		public void onLoadProgress(final int participant, final int percent) {
		}

		public void onGo() {
		}

		public void onTurn(final TurnData turn) {
		}

		public void onLag(final List<LanData.LagEntry> entries) {
		}

		public void onLagEnd() {
		}

		public void onDesync(final LanMessages.Desync desync) {
		}

		public void onPlayerState(final int participant, final int controlScheme) {
		}

		public void onGameEnd(final int reason) {
		}

		/** The host answered a rejoin: {@code total} archived turns will be replayed from {@code from}. */
		public void onRejoin(final LanGameSetup setup, final int from, final int total) {
		}

		public void onCaughtUp() {
		}

		/**
		 * The game this client plays must start over: {@code kind} is a {@code LanProtocol.RESYNC_} value ({@code
		 * RESYNC_DESYNC} with the step the sums first differed at, {@code RESYNC_TURNS} when the turns it holds are not
		 * the game's, {@code RESYNC_GIVE_UP} when it cannot stay in sync and takes no more part). The turns of the
		 * game come from the first one again; the engine plays them in a new game, then {@link #resumeTurns} runs.
		 */
		public void onResync(final int kind, final int tick) {
		}

		/** The link of a running game broke; the client keeps trying to get back for a while. */
		public void onReconnecting() {
		}

		/** The game answered again after {@link #onReconnecting}. */
		public void onReconnected() {
		}

		public void onDisconnected(final String reason) {
		}

		/** The host is gone and the game looks for a new one; {@link #onHostChanged} says when it has one. */
		public void onMigrating() {
		}

		/** Another participant is the host now; the turns go on from {@code firstTurn} (0 in a lobby). */
		public void onHostChanged(final int newHost, final int firstTurn) {
		}

		/** This client became the host: the game it plays goes on with the host given here, in this process. */
		public void onPromoted(final LanHost host) {
		}

		/**
		 * The state of the lobby map on this device. {@code file} is set in {@link LanClient#MAP_READY} (null when
		 * no index is configured), {@code failure} (a {@link MapDownload} FAIL_ code) in {@link LanClient#MAP_FAILED}.
		 */
		public void onMapState(final int state, final LanData.MapInfo map, final File file, final int failure) {
		}

		/** Bytes received so far (first call: what an earlier attempt left); at most about ten times a second. */
		public void onMapProgress(final long done, final int total) {
		}
	}

	public static final class Config {
		public String engineVersion = "";
		public String buildCommit = "";
		public boolean officialBuild;
		public long dataChecksum;
		public String playerName = "Player";
		public String portrait = "";
		public int listenPort;
		public int controlScheme;
		public int wantedRole = LanProtocol.ROLE_PLAYER;
		/** Acknowledge each turn as soon as it arrives; the engine turns this off and calls {@link #turnProcessed}. */
		public boolean autoAck = true;
		/** Report LOADED right after START; the engine turns this off and calls {@link #loaded}. */
		public boolean autoLoad;
		/** Where events go; null runs them on the network thread. */
		public Executor events;
		/** Finds maps on this device and receives downloads; null leaves maps to the caller (reported as present). */
		public LanMapIndex mapIndex;
		/** The key of a running game this client comes back to after the app restarted; null for a new join. */
		public RejoinKey rejoinKey;
		/**
		 * Report CAUGHT_UP as soon as the archived turns have arrived; the engine turns this off and calls
		 * {@link #caughtUp} once it has played them.
		 */
		public boolean autoCaughtUp = true;
		/** How long a lost link of a running game is retried before the loss is reported; 0 reports it at once. */
		public int reconnectMs;
		/** A map download with no data for this long fails (its part file is kept for a retry). */
		public int mapStallMs = 15000;
		/**
		 * What a host made by this client is like (version, data, validator, turn length, ...). Set, the client listens
		 * for the others and takes the game over when the host is gone and it is next in the order; null never hosts.
		 */
		public LanHost.Config hostTemplate;
		/** Whether this machine may become a host now (it has a network); null for always. */
		public java.util.function.BooleanSupplier canHost;
	}

	private final LanLoop loop;
	private final Config config;
	private final Listener listener;
	private final ConcurrentLinkedQueue<TurnData> turns = new ConcurrentLinkedQueue<TurnData>();
	private final Map<Integer, byte[]> pending = new LinkedHashMap<Integer, byte[]>();
	private final Object cmdLock = new Object();
	private int nextSeq;
	private LanConnection conn;
	private InetSocketAddress lastAddress;
	private boolean welcomed;
	private int participantId = LanProtocol.NO_PARTICIPANT;
	private int mySlot = LanProtocol.NO_SLOT;
	private byte[] token;
	private long gameId;
	private LanGameSetup setup;
	private volatile int lastTurn = -1;
	private boolean inGame;
	private boolean leaving;
	private volatile boolean catchingUp;
	private volatile int catchUpTotal;
	private volatile long catchUpSteps;
	private volatile long catchUpTarget;
	private volatile boolean runningJoin;
	/** After a resync: no turns go to the game that is being dropped, and none of its sums or acknowledgments to the host. */
	private volatile boolean held;
	/** Whether the game has taken any turn from {@link #pollTurn}; turns nobody has played yet are free to keep. */
	private volatile boolean polled;
	private volatile int resyncKind = -1;
	/** After a RESYNC and before the REJOIN that answers it: turns still on their way are the dropped game's. */
	private boolean awaitingRejoin;
	private volatile int resyncTick = -1;
	private boolean rejoinAttempt;
	private long reconnectUntil;
	private boolean rejectedWhileReconnecting;
	private boolean userClosed;
	private int ackedSentTo = -1;
	private TurnArchive history = new TurnArchive();
	/**
	 * The sums this client reported, by step: a host that takes the game over compares what comes after it with them,
	 * as the host it replaces compared with its own log.
	 */
	private final java.util.TreeMap<Integer, Integer> reportedSums = new java.util.TreeMap<Integer, Integer>();
	private final Map<Integer, Integer> presence = new java.util.HashMap<Integer, Integer>();
	private int hostId = LanProtocol.NO_PARTICIPANT;
	private int phase = LanProtocol.STATE_LOBBY;
	private LanMessages.LobbyState lastLobby;
	private boolean welcomedOnce;
	private boolean ended;
	private boolean loadedSent;
	private boolean simPausedNow;
	private boolean migrating;
	private boolean attempting;
	private String lostReason = "";
	private long migrateDeadline;
	private int candidateIndex;
	private LanStandby standby;
	private int ownListenPort;
	private LanHost promotedHost;
	private LanLoop.Timer candidateTimer;
	private LanData.MapInfo mapInfo;
	private int mapState = -1;
	private File mapFile;
	private MapDownload download;
	private int mapGeneration;
	private long lastMapEvent;
	private long lastMapStatus;

	public LanClient(final LanLoop loop, final Config config, final Listener listener) {
		this.loop = loop;
		this.config = config;
		this.listener = listener;
		loop.addShutdownHook(new Runnable() {
			@Override
			public void run() {
				cancelDownload(false);
			}
		});
	}



	// -------------------------------------------------------------- connecting

	/** Connects and sends HELLO; the outcome arrives as onWelcome / onReject / onDisconnected. */
	public void connect(final InetSocketAddress address) {
		final RejoinKey key = this.config.rejoinKey;
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				openStandby();
				if (key != null) {
					LanClient.this.token = key.token();
					LanClient.this.gameId = key.gameId;
				}
			}
		});
		connect(address, key != null);
	}

	private void openStandby() {
		if (this.config.hostTemplate == null || this.standby != null) {
			return;
		}
		final LanStandby s = new LanStandby(this.loop, new LanStandby.Owner() {
			@Override
			public boolean hostAlive() {
				return LanClient.this.conn != null && LanClient.this.welcomed && !LanClient.this.migrating;
			}

			@Override
			public InetSocketAddress hostAddress() {
				return LanClient.this.lastAddress;
			}

			@Override
			public boolean searching() {
				return LanClient.this.conn == null && (reconnecting() || LanClient.this.migrating);
			}
		});
		try {
			this.ownListenPort = s.open(this.config.hostTemplate.port, this.config.hostTemplate.portTries);
			this.standby = s;
			this.loop.addShutdownHook(new Runnable() {
				@Override
				public void run() {
					s.close();
				}
			});
		}
		catch (final IOException e) {
			LanLog.warn("cannot listen for a host change; this machine will not take the game over", e);
		}
	}

	/**
	 * Comes back to the running game from its first turn after the engine started over (the graphics were lost, say):
	 * the turns already received are dropped, since the new game has applied none of them.
	 */
	public void rejoinFromStart() {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				LanClient.this.turns.clear();
				LanClient.this.lastTurn = -1;
				LanClient.this.ackedSentTo = -1;
				LanClient.this.pending.clear();
				LanClient.this.runningJoin = false;
				LanClient.this.held = false;
				resetHistory();
				final InetSocketAddress a = LanClient.this.lastAddress;
				if (a != null) {
					connect(a, true);
				}
			}
		});
	}

	/** Reconnects to the same game with the stored key, asking only for the turns this client lacks. */
	public void reconnect() {
		final InetSocketAddress a = this.lastAddress;
		if (a == null) {
			throw new IllegalStateException("never connected");
		}
		connect(a, true);
	}

	public void reconnect(final InetSocketAddress address) {
		connect(address, true);
	}

	private void connect(final InetSocketAddress address, final boolean rejoin) {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				if (LanClient.this.conn != null) {
					final LanConnection old = LanClient.this.conn;
					LanClient.this.conn = null;
					old.close("reconnecting");
				}
				// A new link starts a new stay: an earlier leave must not make this one look like a leave.
				LanClient.this.leaving = false;
				LanClient.this.userClosed = false;
				LanClient.this.rejectedWhileReconnecting = false;
				LanClient.this.ended = false;
				LanClient.this.lastAddress = address;
				LanClient.this.loop.connect(address, LanClient.this.loop.timings().connectTimeoutMs,
						new LanLoop.ConnectCallback() {
							@Override
							public void onConnected(final LanConnection c) {
								attach(c, rejoin);
							}

							@Override
							public void onFailed(final IOException error) {
								if (reconnecting()) {
									if (migrationEnabled() && refused(error)) {
										// nothing listens where the host was: it is gone, no need to wait for it
										startMigration("connect failed: " + error.getMessage());
										return;
									}
									retryLater("connect failed: " + error.getMessage());
									return;
								}
								fire(new Runnable() {
									@Override
									public void run() {
										LanClient.this.listener.onDisconnected("connect failed: " + error.getMessage());
									}
								});
							}
						});
			}
		});
	}

	private void attach(final LanConnection c, final boolean rejoin) {
		attach(c, rejoin, false);
	}

	/** @param migrate sends MIGRATE_HELLO, which a candidate for the new host takes in place of a HELLO */
	private void attach(final LanConnection c, final boolean rejoin, final boolean migrate) {
		this.conn = c;
		this.welcomed = false;
		// A lobby has no game to play through: coming back to one is a join with the old seat.
		final boolean lobbyBack = this.welcomedOnce && this.phase == LanProtocol.STATE_LOBBY;
		this.rejoinAttempt = rejoin && !lobbyBack;
		// the new session starts with no map status at the host, so the map is judged again on WELCOME
		cancelDownload(false);
		this.mapInfo = null;
		c.setListener(new LanConnection.Listener() {
			@Override
			public void onMessage(final LanConnection connection, final LanMessage m) {
				if (connection == LanClient.this.conn) {
					handle(m);
				}
			}

			@Override
			public void onClosed(final LanConnection connection, final String reason) {
				if (connection == LanClient.this.conn) {
					LanClient.this.conn = null;
					cancelDownload(false);
					if (LanClient.this.migrating) {
						nextCandidate();
						return;
					}
					if (mayReconnect()) {
						retryLater(reason);
						return;
					}
					LanClient.this.catchingUp = false;
					LanClient.this.reconnectUntil = 0;
					final String told = LanClient.this.leaving ? LEFT_REASON : reason;
					fire(new Runnable() {
						@Override
						public void run() {
							LanClient.this.listener.onDisconnected(told);
						}
					});
				}
			}
		});
		final LanMessages.Hello h = new LanMessages.Hello();
		h.engineVersion = this.config.engineVersion;
		h.buildCommit = this.config.buildCommit;
		h.officialBuild = this.config.officialBuild;
		h.dataChecksum = this.config.dataChecksum;
		h.playerName = this.config.playerName;
		h.portrait = this.config.portrait;
		h.listenPort = this.ownListenPort != 0 ? this.ownListenPort : this.config.listenPort;
		h.controlScheme = this.config.controlScheme;
		h.wantedRole = this.config.wantedRole;
		if ((migrate || lobbyBack) && rejoin && this.token != null && this.participantId != LanProtocol.NO_PARTICIPANT) {
			c.send(new LanMessages.MigrateHello(this.participantId, this.token, this.inGame ? this.lastTurn : -1,
					new ArrayList<CmdEntry>(), h.listenPort));
			return;
		}
		if (rejoin && this.token != null) {
			h.rejoin = new RejoinKey(this.gameId, this.token).toRejoinData(this.lastTurn + 1);
		}
		c.send(h);
	}

	// -------------------------------------------------------------- reconnecting

	private boolean reconnecting() {
		return this.reconnectUntil != 0;
	}

	/** Whether a link that just closed is worth trying to get back: a running game, not left, not refused. */
	private boolean mayReconnect() {
		return this.config.reconnectMs > 0 && (this.inGame || (this.welcomedOnce && migrationEnabled()))
				&& this.token != null && !this.leaving && !this.userClosed && !this.ended
				&& !this.rejectedWhileReconnecting && this.lastAddress != null;
	}

	private boolean migrationEnabled() {
		return this.config.hostTemplate != null && this.standby != null && this.config.reconnectMs > 0;
	}

	private static boolean refused(final IOException error) {
		final String m = error.getMessage();
		return error instanceof java.net.ConnectException && m != null && m.toLowerCase(Locale.ROOT).contains("refused");
	}

	/** Waits a moment and tries again, until the time for it is up; then the loss is reported. */
	private void retryLater(final String reason) {
		final long now = LanLoop.nowMs();
		if (this.reconnectUntil == 0) {
			this.reconnectUntil = now + (migrationEnabled() ? this.loop.timings().hostGoneMs
					: this.config.reconnectMs);
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onReconnecting();
				}
			});
		}
		if (migrationEnabled() && now >= this.reconnectUntil && !this.rejectedWhileReconnecting) {
			startMigration(reason);
			return;
		}
		if (now >= this.reconnectUntil || this.rejectedWhileReconnecting) {
			this.reconnectUntil = 0;
			this.catchingUp = false;
			final String told = this.leaving ? LEFT_REASON : reason;
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onDisconnected(told);
				}
			});
			return;
		}
		this.loop.schedule(this.loop.timings().reconnectRetryMs, new Runnable() {
			@Override
			public void run() {
				if (reconnecting() && LanClient.this.conn == null) {
					connect(LanClient.this.lastAddress, true);
				}
			}
		});
	}

	/** The host took the client back (REJOIN, or START of a game still loading). */
	private void reconnected() {
		if (this.reconnectUntil != 0) {
			this.reconnectUntil = 0;
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onReconnected();
				}
			});
		}
		// a host that has just taken over does not know the game stands still
		if (this.simPausedNow && this.conn != null) {
			this.conn.send(new LanMessages.AppState(LanProtocol.SIM_PAUSED));
		}
	}

	private void fire(final Runnable r) {
		final Executor e = this.config.events;
		if (e == null) {
			r.run();
		}
		else {
			e.execute(r);
		}
	}

	// ---------------------------------------------------------------- incoming

	private void handle(final LanMessage m) {
		switch (m.type()) {
		case LanProtocol.WELCOME: {
			final LanMessages.Welcome w = (LanMessages.Welcome) m;
			this.welcomed = true;
			this.welcomedOnce = true;
			this.participantId = w.participantId;
			this.token = w.slotToken;
			this.gameId = w.gameId;
			this.hostId = w.ownerId;
			if (this.migrating || this.migrateDeadline != 0) {
				endMigration();
			}
			if (this.phase != LanProtocol.STATE_IN_GAME) {
				this.phase = w.gameState;
			}
			this.lastLobby = w.lobby;
			for (final LanData.SlotInfo s : w.lobby.slots) {
				if (s.participantId == w.participantId && s.kind == LanProtocol.SLOT_HUMAN) {
					this.mySlot = s.index;
				}
			}
			LanLog.info("joined as participant " + w.participantId + ", slot " + this.mySlot + ", state "
					+ w.gameState);
			// A client coming back to a running game has no lobby to show; a link that only broke has nothing new.
			if (!this.rejoinAttempt || this.reconnectUntil == 0) {
				fire(new Runnable() {
					@Override
					public void run() {
						LanClient.this.listener.onWelcome(w);
					}
				});
			}
			if (!this.rejoinAttempt) {
				fire(new Runnable() {
					@Override
					public void run() {
						LanClient.this.listener.onLobby(w.lobby);
					}
				});
			}
			checkMap(w.lobby.map);
			if (w.gameState == LanProtocol.STATE_LOBBY || (w.gameState == LanProtocol.STATE_LOADING && this.inGame)) {
				reconnected();
			}
			if (w.gameState == LanProtocol.STATE_LOADING && this.loadedSent && this.conn != null) {
				// a host that took the game over while it loaded has to hear it again
				this.conn.send(new LanMessages.Loaded());
			}
			return;
		}
		case LanProtocol.REJECT: {
			final LanMessages.Reject r = (LanMessages.Reject) m;
			if (this.migrating) {
				// that candidate does not take this client: the next one is tried when its connection closes
				if (this.conn != null) {
					this.conn.close("rejected: " + r.text);
				}
				return;
			}
			if (reconnecting()) {
				this.rejectedWhileReconnecting = true;
			}
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onReject(r);
				}
			});
			return;
		}
		case LanProtocol.LOBBY_STATE: {
			final LanMessages.LobbyState ls = (LanMessages.LobbyState) m;
			this.lastLobby = ls;
			final int previousSlot = this.mySlot;
			this.mySlot = LanProtocol.NO_SLOT;
			for (final LanData.SlotInfo s : ls.slots) {
				if (s.participantId == this.participantId && s.kind == LanProtocol.SLOT_HUMAN) {
					this.mySlot = s.index;
				}
			}
			if (this.mySlot != previousSlot) {
				LanLog.info("own slot " + previousSlot + " -> " + this.mySlot);
			}
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onLobby(ls);
				}
			});
			checkMap(ls.map);
			return;
		}
		case LanProtocol.MAP_CHUNK: {
			final MapDownload d = this.download;
			// a chunk still on its way after the download was dropped or replaced is harmless
			if (d != null && !d.onChunk((LanMessages.MapChunk) m) && this.conn != null) {
				this.conn.close("bad map data");
			}
			return;
		}
		case LanProtocol.CHAT: {
			final LanMessages.Chat c = (LanMessages.Chat) m;
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onChat(c);
				}
			});
			return;
		}
		case LanProtocol.KICKED: {
			final String reason = ((LanMessages.Kicked) m).reason;
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onKicked(reason);
				}
			});
			return;
		}
		case LanProtocol.COUNTDOWN: {
			final int s = ((LanMessages.Countdown) m).seconds;
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onCountdown(s);
				}
			});
			return;
		}
		case LanProtocol.COUNTDOWN_CANCEL:
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onCountdownCancel();
				}
			});
			return;
		case LanProtocol.START: {
			final LanGameSetup s = ((LanMessages.Start) m).setup;
			LanLog.info("start, seed " + s.seed + ", " + s.slots.size() + " slots");
			reconnected();
			if (this.phase == LanProtocol.STATE_LOBBY) {
				this.phase = LanProtocol.STATE_LOADING;
			}
			resetHistory();
			onSetup(s);
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onStart(s);
				}
			});
			if (this.config.autoLoad) {
				this.loadedSent = true;
				loaded();
			}
			return;
		}
		case LanProtocol.LOAD_PROGRESS: {
			final LanMessages.LoadProgress lp = (LanMessages.LoadProgress) m;
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onLoadProgress(lp.participant, lp.percent);
				}
			});
			return;
		}
		case LanProtocol.GO:
			this.inGame = true;
			this.phase = LanProtocol.STATE_IN_GAME;
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onGo();
				}
			});
			return;
		case LanProtocol.TURN:
			onTurn(((LanMessages.Turn) m).turn);
			return;
		case LanProtocol.TURN_BLOCK:
			for (final TurnData t : ((LanMessages.TurnBlock) m).turns) {
				onTurn(t);
			}
			return;
		case LanProtocol.REJOIN: {
			final LanMessages.Rejoin r = (LanMessages.Rejoin) m;
			this.awaitingRejoin = false;
			// A game that has played none of the turns held (it loaded after the host let the others go) has nothing to
			// drop: they are the host's own, and the ones that come again are skipped as seen.
			final boolean keepHeld = r.turnsFrom == 0 && this.lastTurn >= 0 && !this.held && !this.polled;
			if (r.turnsFrom == 0 && this.lastTurn >= 0 && !this.held && !keepHeld) {
				// The host has none of what this client holds beyond its own turns (a game handed over to a new host).
				startOver(LanProtocol.RESYNC_TURNS, -1);
			}
			if (r.turnsFrom == 0 && !keepHeld) {
				resetHistory();
			}
			onSetup(r.setup);
			this.welcomed = true;
			this.inGame = true;
			this.phase = LanProtocol.STATE_IN_GAME;
			this.catchUpTotal = r.totalTurns;
			this.catchUpSteps = 0;
			this.catchUpTarget = r.totalSteps;
			this.catchingUp = true;
			this.runningJoin = r.turnsFrom == 0 && !keepHeld;
			// The host remembers the commands of the seat; a client that started over numbers its own after them.
			synchronized (this.cmdLock) {
				if (this.nextSeq < r.lastSeq) {
					this.nextSeq = r.lastSeq;
				}
			}
			reconnected();
			checkMap(r.setup.map);
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onRejoin(r.setup, r.turnsFrom, r.totalTurns);
				}
			});
			checkCaughtUp();
			return;
		}
		case LanProtocol.RESYNC: {
			final LanMessages.Resync rs = (LanMessages.Resync) m;
			if (rs.kind == LanProtocol.RESYNC_GIVE_UP) {
				this.userClosed = true;
				this.held = true;
				fire(new Runnable() {
					@Override
					public void run() {
						LanClient.this.listener.onResync(rs.kind, rs.tick);
					}
				});
				if (this.conn != null) {
					this.conn.close("cannot stay in sync");
				}
			}
			else if (rs.kind != LanProtocol.RESYNC_ACK) {
				startOver(rs.kind, rs.tick);
				this.awaitingRejoin = true;
			}
			return;
		}
		case LanProtocol.LAG: {
			final List<LanData.LagEntry> entries = ((LanMessages.Lag) m).entries;
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onLag(entries);
				}
			});
			return;
		}
		case LanProtocol.LAG_END:
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onLagEnd();
				}
			});
			return;
		case LanProtocol.DESYNC: {
			final LanMessages.Desync d = (LanMessages.Desync) m;
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onDesync(d);
				}
			});
			return;
		}
		case LanProtocol.PLAYER_STATE: {
			final LanMessages.PlayerState ps = (LanMessages.PlayerState) m;
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onPlayerState(ps.participant, ps.controlScheme);
				}
			});
			return;
		}
		case LanProtocol.HOST_CHANGED: {
			final LanMessages.HostChanged hc = (LanMessages.HostChanged) m;
			this.hostId = hc.newHost;
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onHostChanged(hc.newHost, hc.firstTurn);
				}
			});
			return;
		}
		case LanProtocol.REDIRECT:
			onRedirect((LanMessages.Redirect) m);
			return;
		case LanProtocol.TURN_FILL_REQUEST:
			onFillRequest((LanMessages.TurnFillRequest) m);
			return;
		case LanProtocol.GAME_END: {
			this.ended = true;
			final int reason = ((LanMessages.GameEnd) m).reason;
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onGameEnd(reason);
				}
			});
			return;
		}
		default:
			LanLog.warn("unexpected message from host: " + m.type());
			if (this.conn != null) {
				this.conn.close("unexpected message " + m.type());
			}
		}
	}

	/**
	 * The game being played has to be dropped: what the client holds of it is not the game's. The turns come again
	 * from the first; until {@link #resumeTurns} (the new game exists) none goes to the old one.
	 */
	private void startOver(final int kind, final int tick) {
		this.turns.clear();
		this.lastTurn = -1;
		this.ackedSentTo = -1;
		this.pending.clear();
		this.held = true;
		this.polled = false;
		this.resyncKind = kind;
		this.resyncTick = tick;
		resetHistory();
		if (this.conn != null) {
			// Everything the old game sent before this is ahead of the answer on the link.
			this.conn.send(new LanMessages.Resync(LanProtocol.RESYNC_ACK, tick));
		}
		fire(new Runnable() {
			@Override
			public void run() {
				LanClient.this.listener.onResync(kind, tick);
			}
		});
	}

	/** The {@code RESYNC_} kind that made the game being played start over, or -1 when it did not. */
	public int resyncKind() {
		return this.resyncKind;
	}

	/** The step the sums differed at, or -1. */
	public int resyncTick() {
		return this.resyncTick;
	}

	/** The game after a resync exists and takes the turns now. */
	public void resumeTurns() {
		this.held = false;
		this.resyncKind = -1;
		this.resyncTick = -1;
	}

	private void resetHistory() {
		this.history = new TurnArchive();
		this.reportedSums.clear();
		this.presence.clear();
	}

	private void onSetup(final LanGameSetup s) {
		this.setup = s;
		for (final LanData.ParticipantInfo p : s.participants) {
			if (!this.presence.containsKey(p.id)) {
				this.presence.put(p.id, LanProtocol.PRESENCE_PLAYING);
			}
		}
		final LanData.ParticipantInfo me = s.participant(this.participantId);
		if (me != null) {
			this.mySlot = s.commandSlot(this.participantId);
		}
	}

	private void onTurn(final TurnData t) {
		if (t.n <= this.lastTurn || this.awaitingRejoin) {
			return;
		}
		if (t.n != this.lastTurn + 1) {
			if (this.conn != null) {
				this.conn.close("turn gap: expected " + (this.lastTurn + 1) + " got " + t.n);
			}
			return;
		}
		try {
			this.history.append(t);
		}
		catch (final LanProtocolException e) {
			LanLog.warn("cannot keep a turn for a host change", e);
		}
		for (final LanData.PresenceEntry e : t.presence) {
			this.presence.put(e.participant, e.state);
		}
		this.lastTurn = t.n;
		if (this.catchingUp && t.n < this.catchUpTotal) {
			this.catchUpSteps += t.steps;
		}
		for (final CmdEntry c : t.cmds) {
			if (c.slot == this.mySlot) {
				this.pending.remove(c.seq);
			}
		}
		this.turns.add(t);
		if (this.config.autoAck) {
			sendAck(t.n);
		}
		fire(new Runnable() {
			@Override
			public void run() {
				LanClient.this.listener.onTurn(t);
			}
		});
		checkCaughtUp();
	}

	private void sendAck(final int n) {
		if (this.conn != null && n > this.ackedSentTo) {
			this.ackedSentTo = n;
			this.conn.send(new LanMessages.TurnDone(n));
		}
	}

	private void checkCaughtUp() {
		if (this.config.autoCaughtUp && this.lastTurn >= this.catchUpTotal - 1) {
			finishCatchUp();
		}
	}

	private void finishCatchUp() {
		if (this.catchingUp && this.conn != null) {
			this.catchingUp = false;
			this.conn.send(new LanMessages.CaughtUp(Math.max(0, this.lastTurn), 0));
			resendPending();
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onCaughtUp();
				}
			});
		}
	}

	/**
	 * The engine has played every archived turn (checking the sums as it went) and is within a few turns of the live
	 * game: the host counts the player in again. Only for clients without {@link Config#autoCaughtUp}.
	 */
	public void caughtUp() {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				finishCatchUp();
			}
		});
	}

	/** Whether the client was given the game so far and its owner has not yet played it through. */
	public boolean isCatchingUp() {
		return this.catchingUp;
	}

	/** The number of archived turns being played through (the turns after them are live ones). */
	public int catchUpTotal() {
		return this.catchUpTotal;
	}

	/**
	 * The steps of the archived turns the host said there were, which is how far the game has to be played through;
	 * the steps received so far when it said none.
	 */
	public long catchUpSteps() {
		return Math.max(this.catchUpTarget, this.catchUpSteps);
	}

	/** Whether the host has started the game (GO, or a rejoin's answer) and this client has not left it. */
	public boolean isInGame() {
		return query(new Callable<Boolean>() {
			@Override
			public Boolean call() {
				return LanClient.this.inGame && !LanClient.this.leaving;
			}
		});
	}

	/** Whether this client entered a game that had started without it (it plays the whole game from the first turn). */
	public boolean isRunningJoin() {
		return this.runningJoin;
	}

	/** Sends every command no turn has carried yet, oldest first; the host drops the ones it already holds. */
	private void resendPending() {
		for (final Map.Entry<Integer, byte[]> e : this.pending.entrySet()) {
			this.conn.send(new LanMessages.Cmd(e.getKey(), e.getValue()));
		}
	}

	// --------------------------------------------------------------------- map

	private static boolean sameMap(final LanData.MapInfo a, final LanData.MapInfo b) {
		return a.name.equals(b.name) && a.size == b.size && a.crc32 == b.crc32
				&& java.util.Arrays.equals(a.sha256, b.sha256);
	}

	private void checkMap(final LanData.MapInfo info) {
		if (this.mapInfo != null && sameMap(this.mapInfo, info) && this.mapState != MAP_FAILED) {
			return;
		}
		cancelDownload(false);
		this.mapInfo = info;
		this.mapFile = null;
		final int generation = ++this.mapGeneration;
		final LanMapIndex index = this.config.mapIndex;
		if (index == null || info.size == 0 && info.name.isEmpty()) {
			mapReady(null);
			return;
		}
		setMapState(MAP_CHECKING, 0);
		final Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				File found = null;
				try {
					found = index.find(info);
				}
				catch (final IOException | RuntimeException e) {
					LanLog.warn("map search failed", e);
				}
				final File result = found;
				LanClient.this.loop.execute(new Runnable() {
					@Override
					public void run() {
						if (generation != LanClient.this.mapGeneration) {
							return;
						}
						if (result != null) {
							mapReady(result);
						}
						else {
							startDownload(info);
						}
					}
				});
			}
		}, "lan-map-search");
		t.setDaemon(true);
		t.start();
	}

	private void startDownload(final LanData.MapInfo info) {
		final int generation = this.mapGeneration;
		final MapDownload d = new MapDownload(this.loop, info, this.config.mapIndex.downloadDir(),
				this.config.mapStallMs, new MapDownload.Callback() {
					@Override
					public void onProgress(final long done, final int total) {
						if (generation != LanClient.this.mapGeneration) {
							return;
						}
						final long now = LanLoop.nowMs();
						if (now - LanClient.this.lastMapEvent >= 100) {
							LanClient.this.lastMapEvent = now;
							fire(new Runnable() {
								@Override
								public void run() {
									LanClient.this.listener.onMapProgress(done, total);
								}
							});
						}
						if (now - LanClient.this.lastMapStatus >= 250) {
							LanClient.this.lastMapStatus = now;
							sendMapStatus(LanProtocol.MAP_STATUS_DOWNLOADING, (int) (done * 100 / total));
						}
					}

					@Override
					public void onVerifying() {
						if (generation == LanClient.this.mapGeneration) {
							fire(new Runnable() {
								@Override
								public void run() {
									LanClient.this.listener.onMapProgress(info.size, info.size);
								}
							});
							setMapState(MAP_VERIFYING, 100);
						}
					}

					@Override
					public void onFinished(final File file, final byte[] sha256, final int crc32) {
						if (generation == LanClient.this.mapGeneration) {
							LanClient.this.config.mapIndex.remember(file, sha256, crc32);
							mapReady(file);
						}
					}

					@Override
					public void onFailed(final int reason, final String detail) {
						if (generation == LanClient.this.mapGeneration) {
							mapFailed(reason, detail);
						}
					}
				});
		this.download = d;
		final int offset = d.begin();
		if (offset >= 0 && this.download == d && this.mapState != MAP_FAILED) {
			final long resumed = d.received();
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onMapProgress(resumed, info.size);
				}
			});
			setMapState(MAP_DOWNLOADING, (int) (resumed * 100 / info.size));
			if (this.conn != null) {
				this.conn.send(new LanMessages.MapRequest(offset));
			}
		}
	}

	private void cancelDownload(final boolean deletePart) {
		if (this.download != null) {
			this.download.abort(deletePart);
			this.download = null;
		}
	}

	private void mapReady(final File file) {
		this.download = null;
		this.mapFile = file;
		LanLog.info("map ready: " + (file == null ? "-" : file.getName()));
		this.mapState = MAP_READY;
		sendMapStatus(LanProtocol.MAP_STATUS_HAVE, 100);
		fireMapState(0);
	}

	private void mapFailed(final int reason, final String detail) {
		LanLog.warn(MAP_FAILED_TEXT + ": " + detail);
		this.download = null;
		this.mapState = MAP_FAILED;
		sendMapStatus(LanProtocol.MAP_STATUS_FAILED, 0);
		fireMapState(reason);
		if (this.conn != null) {
			this.conn.closeAfterFlush(MAP_FAILED_TEXT);
		}
	}

	private void setMapState(final int state, final int percent) {
		if (state != this.mapState && this.mapInfo != null) {
			if (state == MAP_DOWNLOADING) {
				LanLog.info("map download started: " + this.mapInfo.name + " (" + this.mapInfo.size + " bytes), "
						+ percent + "% already there");
			}
			else if (state == MAP_VERIFYING) {
				LanLog.info("map download complete, verifying");
			}
		}
		this.mapState = state;
		if (state == MAP_DOWNLOADING || state == MAP_VERIFYING) {
			sendMapStatus(LanProtocol.MAP_STATUS_DOWNLOADING, percent);
		}
		fireMapState(0);
	}

	private void sendMapStatus(final int status, final int percent) {
		if (this.conn != null) {
			this.conn.send(new LanMessages.MapStatus(status, Math.max(0, Math.min(100, percent))));
		}
	}

	private void fireMapState(final int failure) {
		final int state = this.mapState;
		final LanData.MapInfo info = this.mapInfo;
		final File file = this.mapFile;
		fire(new Runnable() {
			@Override
			public void run() {
				LanClient.this.listener.onMapState(state, info, file, failure);
			}
		});
	}

	// ---------------------------------------------------------------- host change

	/**
	 * The link to the host is lost and the host does not answer. The participants come to the first of them that is
	 * playing (the order the start gave, the lobby's seats in a lobby); that one makes itself the host
	 * ({@link LanHost#LanHost(LanLoop, LanHost.Config, LanHost.Takeover)}) once it has found out for itself that the
	 * host is gone.
	 */
	private void startMigration(final String reason) {
		this.reconnectUntil = 0;
		this.lostReason = reason;
		if (!this.migrating) {
			this.migrating = true;
			this.candidateIndex = 0;
			if (this.migrateDeadline == 0) {
				this.migrateDeadline = LanLoop.nowMs() + this.loop.timings().migrateGiveUpMs;
			}
			fire(new Runnable() {
				@Override
				public void run() {
					LanClient.this.listener.onMigrating();
				}
			});
		}
		nextCandidate();
	}

	private void nextCandidate() {
		if (!this.migrating) {
			return;
		}
		this.attempting = false;
		if (this.candidateTimer != null) {
			this.candidateTimer.cancel();
			this.candidateTimer = null;
		}
		if (this.leaving || this.userClosed || this.ended) {
			// the player is gone from the game, or the game is over: nothing to look for
			this.migrating = false;
			this.migrateDeadline = 0;
			return;
		}
		if (LanLoop.nowMs() >= this.migrateDeadline) {
			failMigration();
			return;
		}
		final List<Integer> order = candidates();
		if (order.isEmpty()) {
			failMigration();
			return;
		}
		if (this.candidateIndex >= order.size()) {
			// nobody took the game over this round; it is looked for again until the time is up
			this.candidateIndex = 0;
			this.loop.schedule(this.loop.timings().reconnectRetryMs, new Runnable() {
				@Override
				public void run() {
					if (LanClient.this.migrating && LanClient.this.conn == null && !LanClient.this.attempting) {
						nextCandidate();
					}
				}
			});
			return;
		}
		final int who = order.get(this.candidateIndex++);
		if (who == this.participantId) {
			promote();
		}
		else {
			connectCandidate(who);
		}
	}

	/** The participants that may take the game over, in the order they do; this client's own id is among them. */
	private List<Integer> candidates() {
		final List<int[]> keyed = new ArrayList<int[]>();
		if (this.phase == LanProtocol.STATE_LOBBY || this.setup == null) {
			if (this.lastLobby != null) {
				for (final LanData.SlotInfo s : this.lastLobby.slots) {
					if (s.kind == LanProtocol.SLOT_HUMAN && s.participantId != this.hostId) {
						keyed.add(new int[] { s.index, s.participantId });
					}
				}
				for (final LanData.ObserverInfo o : this.lastLobby.observers) {
					if (o.participantId != this.hostId) {
						keyed.add(new int[] { LanProtocol.hostKey(o.role, LanProtocol.NO_SLOT, o.participantId),
								o.participantId });
					}
				}
			}
		}
		else {
			for (final LanData.ParticipantInfo p : this.setup.participants) {
				final Integer state = this.presence.get(p.id);
				if (p.id == this.hostId || (state != null && state != LanProtocol.PRESENCE_PLAYING)) {
					continue;
				}
				keyed.add(new int[] { LanProtocol.hostKey(p.role, p.slot, p.id), p.id });
			}
		}
		java.util.Collections.sort(keyed, new java.util.Comparator<int[]>() {
			@Override
			public int compare(final int[] a, final int[] b) {
				return a[0] != b[0] ? (a[0] < b[0] ? -1 : 1) : (a[1] < b[1] ? -1 : (a[1] == b[1] ? 0 : 1));
			}
		});
		final List<Integer> ids = new ArrayList<Integer>();
		for (final int[] k : keyed) {
			if (k[1] != this.participantId || this.standby != null) {
				ids.add(k[1]);
			}
		}
		return ids;
	}

	private InetSocketAddress candidateAddress(final int who) {
		String address = null;
		int port = 0;
		if (this.phase == LanProtocol.STATE_LOBBY || this.setup == null) {
			if (this.lastLobby != null) {
				for (final LanData.MemberInfo m : this.lastLobby.members) {
					if (m.participantId == who) {
						address = m.address;
						port = m.listenPort;
					}
				}
			}
		}
		else {
			final LanData.ParticipantInfo p = this.setup.participant(who);
			if (p != null) {
				address = p.address;
				port = p.listenPort;
			}
		}
		if (address == null || address.isEmpty() || port == 0) {
			return null;
		}
		try {
			return new InetSocketAddress(java.net.InetAddress.getByName(address), port);
		}
		catch (final java.net.UnknownHostException e) {
			return null;
		}
	}

	private void connectCandidate(final int who) {
		final InetSocketAddress address = candidateAddress(who);
		if (address == null) {
			nextCandidate();
			return;
		}
		this.attempting = true;
		this.loop.connect(address, this.loop.timings().connectTimeoutMs, new LanLoop.ConnectCallback() {
			@Override
			public void onConnected(final LanConnection c) {
				if (!LanClient.this.migrating || LanClient.this.conn != null) {
					c.close("not needed");
					return;
				}
				LanClient.this.lastAddress = address;
				attach(c, true, true);
				LanClient.this.candidateTimer = LanClient.this.loop.schedule(
						LanClient.this.loop.timings().hostGoneMs + LanClient.this.loop.timings().migrateWaitMs,
						new Runnable() {
							@Override
							public void run() {
								if (LanClient.this.conn == c && !LanClient.this.welcomed) {
									c.close("the candidate does not answer");
								}
							}
						});
			}

			@Override
			public void onFailed(final IOException error) {
				if (LanClient.this.migrating && LanClient.this.conn == null) {
					nextCandidate();
				}
			}
		});
	}

	/** A host (this one's own, or another's) has taken this client; the search is over. */
	private void endMigration() {
		this.migrating = false;
		this.attempting = false;
		this.migrateDeadline = 0;
		if (this.candidateTimer != null) {
			this.candidateTimer.cancel();
			this.candidateTimer = null;
		}
		if (this.standby != null && this.promotedHost == null) {
			this.standby.hostFound();
		}
	}

	private void failMigration() {
		this.migrating = false;
		this.attempting = false;
		this.migrateDeadline = 0;
		this.reconnectUntil = 0;
		this.catchingUp = false;
		final String told = this.leaving ? LEFT_REASON : this.lostReason;
		fire(new Runnable() {
			@Override
			public void run() {
				LanClient.this.listener.onDisconnected(told);
			}
		});
	}

	/** This client is the next one: it makes the host, and then comes to it like the others. */
	private void promote() {
		if (this.promotedHost != null) {
			connectToOwnHost();
			return;
		}
		if (this.config.canHost != null && !this.config.canHost.getAsBoolean()) {
			// With no network this machine would only host itself: it keeps looking until the time is up.
			this.candidateIndex--;
			this.loop.schedule(this.loop.timings().reconnectRetryMs, new Runnable() {
				@Override
				public void run() {
					if (LanClient.this.migrating && LanClient.this.conn == null) {
						nextCandidate();
					}
				}
			});
			return;
		}
		final LanHost host = new LanHost(this.loop, this.config.hostTemplate.copy(), buildTakeover());
		host.attachServer(this.standby.server());
		this.standby.promoted(host);
		this.promotedHost = host;
		fire(new Runnable() {
			@Override
			public void run() {
				LanClient.this.listener.onPromoted(host);
			}
		});
		connectToOwnHost();
	}

	private void connectToOwnHost() {
		final InetSocketAddress own = new InetSocketAddress("127.0.0.1", this.standby.port());
		this.attempting = true;
		this.loop.connect(own, this.loop.timings().connectTimeoutMs, new LanLoop.ConnectCallback() {
			@Override
			public void onConnected(final LanConnection c) {
				if (LanClient.this.conn != null) {
					c.close("not needed");
					return;
				}
				LanClient.this.lastAddress = own;
				attach(c, true, true);
			}

			@Override
			public void onFailed(final IOException error) {
				if (LanClient.this.migrating && LanClient.this.conn == null) {
					nextCandidate();
				}
			}
		});
	}

	private LanHost.Takeover buildTakeover() {
		final LanHost.Takeover t = new LanHost.Takeover();
		t.gameId = this.gameId;
		t.selfId = this.participantId;
		t.selfToken = this.token;
		t.previousHost = this.hostId;
		t.mapFile = this.mapFile;
		t.presence = new java.util.HashMap<Integer, Integer>(this.presence);
		if (this.phase == LanProtocol.STATE_LOBBY || this.setup == null) {
			t.gameState = LanProtocol.STATE_LOBBY;
			t.lobby = this.lastLobby;
		}
		else {
			t.gameState = this.inGame || this.phase == LanProtocol.STATE_IN_GAME ? LanProtocol.STATE_IN_GAME
					: LanProtocol.STATE_LOADING;
			t.setup = this.setup;
			t.archive = this.history.snapshot();
			t.checksums = new java.util.TreeMap<Integer, Integer>(this.reportedSums);
		}
		return t;
	}

	/** A participant that still sees the host says so: the connection is not the host's to lose, so it goes back. */
	private void onRedirect(final LanMessages.Redirect r) {
		if (!this.migrating) {
			return;
		}
		final InetSocketAddress address;
		try {
			address = new InetSocketAddress(java.net.InetAddress.getByName(r.host), r.port);
		}
		catch (final java.net.UnknownHostException e) {
			return;
		}
		this.migrating = false;
		this.attempting = false;
		if (this.candidateTimer != null) {
			this.candidateTimer.cancel();
			this.candidateTimer = null;
		}
		final LanConnection old = this.conn;
		this.conn = null;
		if (old != null) {
			old.close("redirected");
		}
		this.reconnectUntil = LanLoop.nowMs() + this.loop.timings().hostGoneMs;
		connect(address, true);
	}

	/** The new host is missing turns this client holds: it hands over the ones asked for. */
	private void onFillRequest(final LanMessages.TurnFillRequest r) {
		final List<TurnData> list = new ArrayList<TurnData>();
		int bytes = 0;
		try {
			for (int n = r.from; n < r.to && n <= this.lastTurn && list.size() < 512; n++) {
				final TurnData t = this.history.get(n);
				final int size = t.toBytes().length;
				if (!list.isEmpty() && bytes + size > 56000) {
					break;
				}
				bytes += size;
				list.add(t);
			}
		}
		catch (final LanProtocolException e) {
			LanLog.warn("cannot hand over turns", e);
		}
		if (this.conn != null) {
			this.conn.send(new LanMessages.TurnFill(list));
		}
	}

	// ---------------------------------------------------------------- outgoing

	private void send(final LanMessage m) {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				if (LanClient.this.conn != null) {
					LanClient.this.conn.send(m);
				}
			}
		});
	}

	public void slotRequest(final int slot, final int race, final int team, final int color, final int handicap) {
		send(new LanMessages.SlotRequest(slot, race, team, color, handicap));
	}

	public void setReady(final boolean ready) {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				// the host would refuse it anyway; keeping it here means the UI never shows a false checkmark
				if (!ready || LanClient.this.config.mapIndex == null || LanClient.this.mapState == MAP_READY) {
					send(new LanMessages.Ready(ready));
				}
			}
		});
	}

	public void chat(final int channel, final String text) {
		send(new LanMessages.Chat(channel, 0, text));
	}

	public void hostSlot(final int slot, final int action, final int level) {
		send(new LanMessages.HostSlot(slot, action, level));
	}

	/** The host sets a computer's race, team, color and handicap. */
	public void hostSetup(final int slot, final int race, final int team, final int color, final int handicap) {
		send(new LanMessages.HostSetup(slot, race, team, color, handicap));
	}

	public void hostOptions(final LanData.Options options) {
		send(new LanMessages.HostOptions(options));
	}

	public void requestStart() {
		send(new LanMessages.StartRequest());
	}

	public void cancelCountdown() {
		send(new LanMessages.CountdownCancel());
	}

	public void playerState(final int controlScheme) {
		send(new LanMessages.PlayerState(0, controlScheme));
	}

	public void loadProgress(final int percent) {
		send(new LanMessages.LoadProgress(0, percent));
	}

	public void loaded() {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				LanClient.this.loadedSent = true;
			}
		});
		send(new LanMessages.Loaded());
	}

	public void checksum(final int tick, final int crc) {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				if (!LanClient.this.held && (LanClient.this.reportedSums.size() < LanHost.MAX_CHECKSUM_LOG
						|| LanClient.this.reportedSums.containsKey(tick))) {
					LanClient.this.reportedSums.put(tick, crc);
				}
				if (!LanClient.this.held && LanClient.this.conn != null) {
					LanClient.this.conn.send(new LanMessages.Checksum(tick, crc));
				}
			}
		});
	}

	public void appState(final int state) {
		send(new LanMessages.AppState(state));
	}

	/** The game's simulation stands still or runs again; the host stops giving it steps meanwhile. */
	public void simPaused(final boolean paused) {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				LanClient.this.simPausedNow = paused;
			}
		});
		appState(paused ? LanProtocol.SIM_PAUSED : LanProtocol.SIM_RESUMED);
	}

	public void vote(final int vote) {
		send(new LanMessages.Vote(vote));
	}

	/** Tells the host that turn {@code n} was applied; only needed when autoAck is off. */
	public void turnProcessed(final int n) {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				if (LanClient.this.held) {
					return;
				}
				sendAck(n);
			}
		});
	}

	/**
	 * Queues a command for the next turn and returns its sequence number, or -1 for a command too long to travel.
	 * Safe from any thread.
	 */
	public int sendCommand(final byte[] command) {
		if (command.length > LanProtocol.MAX_COMMAND_BYTES) {
			// Its number would never reach the host, which closes the link at the gap the next command leaves.
			LanLog.warn("command of " + command.length + " bytes not sent");
			return -1;
		}
		final int seq;
		synchronized (this.cmdLock) {
			seq = ++this.nextSeq;
			this.loop.execute(new Runnable() {
				@Override
				public void run() {
					LanClient.this.pending.put(seq, command);
					if (LanClient.this.conn != null && LanClient.this.welcomed && LanClient.this.inGame
							&& !LanClient.this.catchingUp) {
						LanClient.this.conn.send(new LanMessages.Cmd(seq, command));
					}
				}
			});
		}
		return seq;
	}

	/** Leaves for good (LEAVE, then close). */
	public void leave(final int reason) {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				LanClient.this.leaving = true;
				if (LanClient.this.conn != null) {
					LanClient.this.conn.send(new LanMessages.Leave(reason));
					LanClient.this.conn.closeAfterFlush(LEFT_REASON);
				}
			}
		});
	}

	/** Drops the link without telling the host (a crash, seen from the host). */
	public void close() {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				LanClient.this.userClosed = true;
				if (LanClient.this.conn != null) {
					LanClient.this.conn.close("closed by client");
				}
			}
		});
	}

	// --------------------------------------------------------------- accessors

	/** Next received turn for the game thread, or null. */
	public TurnData pollTurn() {
		if (this.held) {
			return null;
		}
		final TurnData t = this.turns.poll();
		if (t != null) {
			this.polled = true;
		}
		return t;
	}

	public int lastTurn() {
		return this.lastTurn;
	}

	public int participantId() {
		return query(new Callable<Integer>() {
			@Override
			public Integer call() {
				return LanClient.this.participantId;
			}
		});
	}

	public boolean isConnected() {
		return query(new Callable<Boolean>() {
			@Override
			public Boolean call() {
				return LanClient.this.conn != null && LanClient.this.welcomed;
			}
		});
	}

	/** One of the {@code MAP_*} states, or -1 before the first lobby arrived. */
	public int mapState() {
		return query(new Callable<Integer>() {
			@Override
			public Integer call() {
				return LanClient.this.mapState;
			}
		});
	}

	/** The verified map file once {@link #MAP_READY}; null while missing or when no index is configured. */
	public File mapFile() {
		return query(new Callable<File>() {
			@Override
			public File call() {
				return LanClient.this.mapFile;
			}
		});
	}

	public int pendingCommands() {
		return query(new Callable<Integer>() {
			@Override
			public Integer call() {
				return LanClient.this.pending.size();
			}
		});
	}

	/** The key to store for a later rejoin, or null before WELCOME. */
	public RejoinKey rejoinKey() {
		return query(new Callable<RejoinKey>() {
			@Override
			public RejoinKey call() {
				if (LanClient.this.token == null) {
					return null;
				}
				final InetSocketAddress a = LanClient.this.lastAddress;
				// A host on this very machine goes with its process: there is nothing to look for later.
				final String host = a == null || a.getAddress() == null || a.getAddress().isLoopbackAddress() ? null
						: a.getAddress().getHostAddress() + ":" + a.getPort();
				return new RejoinKey(LanClient.this.gameId, LanClient.this.token, host);
			}
		});
	}

	/** The participant that is the host now, or {@link LanProtocol#NO_PARTICIPANT} before WELCOME. */
	public int hostId() {
		return query(new Callable<Integer>() {
			@Override
			public Integer call() {
				return LanClient.this.hostId;
			}
		});
	}

	/** The port this client listens on for the others to come to it when the host is gone; 0 when it does not. */
	public int standbyPort() {
		return query(new Callable<Integer>() {
			@Override
			public Integer call() {
				return LanClient.this.ownListenPort;
			}
		});
	}

	/** The host this client made of itself when the host was gone, or null. */
	public LanHost promotedHost() {
		return query(new Callable<LanHost>() {
			@Override
			public LanHost call() {
				return LanClient.this.promotedHost;
			}
		});
	}

	/** Whether the game has lost its host and is looking for a new one. */
	public boolean isMigrating() {
		return query(new Callable<Boolean>() {
			@Override
			public Boolean call() {
				return LanClient.this.migrating;
			}
		});
	}

	public LanGameSetup setup() {
		return query(new Callable<LanGameSetup>() {
			@Override
			public LanGameSetup call() {
				return LanClient.this.setup;
			}
		});
	}

	private <T> T query(final Callable<T> c) {
		try {
			return this.loop.call(c);
		}
		catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
