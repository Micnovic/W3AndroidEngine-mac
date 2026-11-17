package com.etheller.warsmash.lan;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.badlogic.gdx.Gdx;
import com.etheller.warsmash.util.Log;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.RejoinStore;
import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanTimings;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.transport.LanDiscovery;
import net.warsmash.lan.transport.LanFaultInjector;
import net.warsmash.lan.transport.LanLoop;
import net.warsmash.lan.transport.LanNetwork;

/**
 * The network side of one LAN game on this machine: the client that is this machine's player, and, when it hosts,
 * the host with its own network thread. Events reach the listeners on the game thread (libGDX's posted runnables).
 */
public final class LanSession {
	private final LanTimings timings;
	private final CopyOnWriteArrayList<LanClient.Listener> listeners = new CopyOnWriteArrayList<LanClient.Listener>();
	private LanLoop hostLoop;
	private LanLoop clientLoop;
	private LanHost host;
	private LanClient client;
	private LanFaultInjector faults;
	private final LanNetwork network;
	private LanLoop searchLoop;
	private LanDiscovery searching;
	private LanDiscovery announcing;
	private boolean announce;
	private boolean sessionLockHeld;
	private boolean closed;
	private volatile Runnable catchUp;
	private volatile RejoinStore rejoinStore;
	private volatile LanHost.Config hostTemplate;
	private final java.util.concurrent.CountDownLatch closedLatch = new java.util.concurrent.CountDownLatch(1);

	public LanSession(final LanTimings timings) {
		this(timings, LanSessions.network());
	}

	/** @param network the platform: addresses, radio locks and, on Android, the foreground service */
	public LanSession(final LanTimings timings, final LanNetwork network) {
		this.timings = timings;
		this.network = network;
	}

	public LanNetwork network() {
		return this.network;
	}

	/** Where the key of the game is kept while this session plays it; null keeps none. */
	public void setRejoinStore(final RejoinStore store) {
		this.rejoinStore = store;
	}

	private void keepKey() {
		final RejoinStore store = this.rejoinStore;
		final LanClient c = this.client;
		if (store != null && c != null) {
			final net.warsmash.lan.RejoinKey key = c.rejoinKey();
			if (key != null) {
				this.keptGameId = key.gameId;
				this.keptKey = true;
				store.save(key);
			}
		}
	}

	// The game of the key kept is remembered here: dropping it must not ask the client, whose loop may be stopped
	// while the caller is the app's main thread.
	private volatile long keptGameId;
	private volatile boolean keptKey;

	private void dropKey() {
		final RejoinStore store = this.rejoinStore;
		if (store != null && this.keptKey) {
			store.clear(this.keptGameId);
			this.keptKey = false;
		}
	}

	/**
	 * What a host made by this machine's player is like when the host of the game it joins is gone and this player is
	 * next (see {@link LanClient.Config#hostTemplate}); set before {@link #connect}. Null never takes a game over.
	 */
	public void setHostTemplate(final LanHost.Config template) {
		this.hostTemplate = template;
	}

	/** Whether a host made by {@link #openHost} announces its game by broadcast; set before it. */
	public void setAnnounce(final boolean announce) {
		this.announce = announce;
	}

	/** The platform keeps the radio and the process alive from the first connection until {@link #close}. */
	private synchronized void holdSession() {
		if (!this.sessionLockHeld && !this.closed) {
			this.sessionLockHeld = true;
			this.network.acquireSessionLock();
		}
	}

	public LanTimings timings() {
		return this.timings;
	}

	/** Starts hosting; returns the port bound. */
	public int openHost(final LanHost.Config config) throws IOException {
		holdSession();
		this.hostLoop = new LanLoop("lan-host", this.timings);
		this.hostLoop.start();
		if (this.faults != null) {
			this.hostLoop.setFaultInjector(this.faults);
		}
		this.host = new LanHost(this.hostLoop, config);
		final int port = this.host.open();
		if (this.announce) {
			try {
				announce();
			}
			catch (final IOException e) {
				// The game can still be joined by address.
				Log.warn("LAN: cannot announce the game: " + e);
			}
		}
		return port;
	}

	/** Looks for announced games; the listener is called on the search thread. */
	public synchronized void search(final LanDiscovery.Listener listener) throws IOException {
		stopSearch();
		holdSession();
		this.searchLoop = new LanLoop("lan-search", this.timings);
		this.searchLoop.start();
		this.searching = new LanDiscovery(this.searchLoop, this.network);
		this.searching.setListener(listener);
		this.searching.open();
		this.searching.search();
	}

	public synchronized void stopSearch() {
		if (this.searching != null) {
			this.searching.close();
			this.searching = null;
		}
		if (this.searchLoop != null) {
			this.searchLoop.shutdown(LanLoop.RESPONSIVE_WAIT_MS);
			this.searchLoop = null;
		}
	}

	/** The app went to the background or came back: tells the host, and on return plays on to the turns that piled up. */
	public void appState(final boolean background) {
		final LanClient c = this.client;
		if (c != null) {
			c.appState(background ? LanProtocol.APP_BACKGROUND : LanProtocol.APP_FOREGROUND);
		}
		final Runnable r = this.catchUp;
		if (!background && (r != null) && (Gdx.app != null)) {
			Gdx.app.postRunnable(r);
		}
	}

	/** What the game does on the game thread when the app is back (plays the waiting turns without drawing). */
	public void setCatchUp(final Runnable catchUp) {
		this.catchUp = catchUp;
	}

	/**
	 * Makes the hosted game findable on the network: it is announced until the session ends. Needs a host, so call it
	 * after {@link #openHost}.
	 */
	public synchronized void announce() throws IOException {
		final LanHost h = host();
		if (h == null) {
			throw new IllegalStateException("not hosting");
		}
		if (this.announcing != null) {
			return;
		}
		final LanDiscovery discovery = new LanDiscovery(this.hostLoop != null ? this.hostLoop : this.clientLoop,
				this.network);
		discovery.open();
		this.announcing = discovery;
		h.setDiscovery(discovery);
	}

	/** Holds the network's session lock (Wi-Fi lock, foreground service) until the session closes. */
	public void holdNetwork() {
		holdSession();
	}

	/** Connects this machine's player; the outcome comes as listener events. */
	public LanClient connect(final LanClient.Config config, final InetSocketAddress address) throws IOException {
		holdSession();
		this.clientLoop = new LanLoop("lan-client", this.timings);
		this.clientLoop.start();
		if ((this.faults != null) && (this.hostLoop == null)) {
			this.clientLoop.setFaultInjector(this.faults);
		}
		if (config.events == null) {
			config.events = new java.util.concurrent.Executor() {
				@Override
				public void execute(final Runnable command) {
					Gdx.app.postRunnable(command);
				}
			};
		}
		if ((config.hostTemplate == null) && (this.hostLoop == null)) {
			config.hostTemplate = this.hostTemplate;
		}
		if (config.canHost == null) {
			config.canHost = new java.util.function.BooleanSupplier() {
				@Override
				public boolean getAsBoolean() {
					return LanSession.this.network.isNetworkAvailable();
				}
			};
		}
		config.autoAck = false;
		config.autoLoad = false;
		// The engine plays the archive through and reports CAUGHT_UP itself; a broken link is retried for a while.
		config.autoCaughtUp = false;
		config.reconnectMs = this.timings.reconnectMs;
		this.client = new LanClient(this.clientLoop, config, new Forwarding());
		this.client.connect(address);
		return this.client;
	}

	/**
	 * Faults to apply to the connections of this machine's network thread: the host's when it hosts (so every player's
	 * link is the same), else the client's. Set before {@link #openHost} or {@link #connect}.
	 */
	public void setFaults(final LanFaultInjector faults) {
		this.faults = faults;
	}

	public LanFaultInjector faults() {
		return this.faults;
	}

	public LanClient client() {
		return this.client;
	}

	/** The host this machine runs: the one it made, or the one it became when the game's host was gone. */
	public LanHost host() {
		final LanHost h = this.host;
		if (h != null) {
			return h;
		}
		final LanClient c = this.client;
		return c == null ? null : c.promotedHost();
	}

	public void addListener(final LanClient.Listener listener) {
		this.listeners.add(listener);
	}

	public void removeListener(final LanClient.Listener listener) {
		this.listeners.remove(listener);
	}

	/**
	 * The player of this machine is done with the game. A host whose game still has other players hands it over: its
	 * leaving goes into a turn, its connections close, and the next participant takes the game on (see
	 * {@link LanHost#retire}); the session closes itself afterwards. Returns whether it does, else the caller closes it.
	 */
	public boolean retire() {
		final LanHost h = host();
		if ((h == null) || !h.hasOtherPlayersInGame()) {
			return false;
		}
		h.retire(new Runnable() {
			@Override
			public void run() {
				new Thread("lan-retired") {
					@Override
					public void run() {
						close();
					}
				}.start();
			}
		});
		return true;
	}

	/** Waits until {@link #close} has been called (a retiring host closes later), at most this long. */
	public void awaitClosed(final long ms) {
		try {
			this.closedLatch.await(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * The engine started over inside the process (the graphics were lost): the running game is asked for again from
	 * its first turn, since the new game has applied none of them. {@code started} runs on the game thread when the
	 * host has answered, {@code failed} when it has not or refused.
	 */
	public void rejoinInPlace(final Runnable started, final Runnable failed) {
		final LanClient c = this.client;
		if (c == null) {
			failed.run();
			return;
		}
		final LanClient.Listener once = new LanClient.Listener() {
			private boolean done;

			private boolean first() {
				if (this.done) {
					return false;
				}
				this.done = true;
				removeListener(this);
				return true;
			}

			@Override
			public void onRejoin(final LanGameSetup setup, final int from, final int total) {
				if (first()) {
					started.run();
				}
			}

			@Override
			public void onReject(final LanMessages.Reject reject) {
				if (first()) {
					failed.run();
				}
			}

			@Override
			public void onDisconnected(final String reason) {
				if (first()) {
					failed.run();
				}
			}
		};
		addListener(once);
		c.rejoinFromStart();
	}

	/** Leaves the game and stops the network threads. */
	public void close() {
		stopSearch();
		synchronized (this) {
			this.closed = true;
			if (this.sessionLockHeld) {
				this.sessionLockHeld = false;
				this.network.releaseSessionLock();
			}
		}
		if (this.clientLoop != null) {
			this.clientLoop.shutdown(LanLoop.RESPONSIVE_WAIT_MS);
		}
		if (this.hostLoop != null) {
			this.hostLoop.shutdown(LanLoop.RESPONSIVE_WAIT_MS);
		}
		this.closedLatch.countDown();
	}

	private final class Forwarding extends LanClient.Listener {
		@Override
		public void onWelcome(final LanMessages.Welcome welcome) {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onWelcome(welcome);
			}
		}

		@Override
		public void onLobby(final LanMessages.LobbyState lobby) {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onLobby(lobby);
			}
		}

		@Override
		public void onChat(final LanMessages.Chat chat) {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onChat(chat);
			}
		}

		@Override
		public void onReject(final LanMessages.Reject reject) {
			// A refused rejoin means the seat is gone (left, or the game ended): the key is of no use any more.
			if (reject.reason == LanProtocol.REJECT_BAD_HELLO) {
				dropKey();
			}
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onReject(reject);
			}
		}

		@Override
		public void onKicked(final String reason) {
			dropKey();
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onKicked(reason);
			}
		}

		@Override
		public void onCountdown(final int seconds) {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onCountdown(seconds);
			}
		}

		@Override
		public void onCountdownCancel() {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onCountdownCancel();
			}
		}

		@Override
		public void onStart(final LanGameSetup setup) {
			keepKey();
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onStart(setup);
			}
		}

		@Override
		public void onLoadProgress(final int participant, final int percent) {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onLoadProgress(participant, percent);
			}
		}

		@Override
		public void onGo() {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onGo();
			}
		}

		@Override
		public void onLag(final List<LanData.LagEntry> entries) {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onLag(entries);
			}
		}

		@Override
		public void onLagEnd() {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onLagEnd();
			}
		}

		@Override
		public void onDesync(final LanMessages.Desync desync) {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onDesync(desync);
			}
		}

		@Override
		public void onPlayerState(final int participant, final int controlScheme) {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onPlayerState(participant, controlScheme);
			}
		}

		@Override
		public void onGameEnd(final int reason) {
			dropKey();
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onGameEnd(reason);
			}
		}

		@Override
		public void onRejoin(final LanGameSetup setup, final int from, final int total) {
			keepKey();
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onRejoin(setup, from, total);
			}
		}

		@Override
		public void onCaughtUp() {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onCaughtUp();
			}
		}

		@Override
		public void onDisconnected(final String reason) {
			if (LanClient.LEFT_REASON.equals(reason)) {
				dropKey();
			}
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onDisconnected(reason);
			}
		}

		@Override
		public void onResync(final int kind, final int tick) {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onResync(kind, tick);
			}
		}

		@Override
		public void onReconnecting() {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onReconnecting();
			}
		}

		@Override
		public void onMigrating() {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onMigrating();
			}
		}

		@Override
		public void onHostChanged(final int newHost, final int firstTurn) {
			// the key names the host it was last used at
			keepKey();
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onHostChanged(newHost, firstTurn);
			}
		}

		@Override
		public void onPromoted(final LanHost promoted) {
			// the game stays findable (and rejoinable) under its own id
			try {
				announce();
			}
			catch (final IOException e) {
				Log.warn("LAN: cannot announce the game after taking it over: " + e);
			}
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onPromoted(promoted);
			}
		}

		@Override
		public void onReconnected() {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onReconnected();
			}
		}

		@Override
		public void onMapState(final int state, final LanData.MapInfo map, final java.io.File file,
				final int failure) {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onMapState(state, map, file, failure);
			}
		}

		@Override
		public void onMapProgress(final long done, final int total) {
			for (final LanClient.Listener l : LanSession.this.listeners) {
				l.onMapProgress(done, total);
			}
		}
	}
}
