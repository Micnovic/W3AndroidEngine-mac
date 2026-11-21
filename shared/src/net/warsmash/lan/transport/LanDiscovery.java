package net.warsmash.lan.transport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.warsmash.lan.protocol.LanCodec;
import net.warsmash.lan.protocol.LanData.GameInfo;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanProtocolException;

/**
 * UDP discovery on the loop thread: hosts announce every few seconds and answer SEARCH, searchers keep a table of
 * games that expires entries nobody refreshed. Datagrams with a foreign magic are ignored without a trace.
 */
public final class LanDiscovery implements LanLoop.Handler {
	public static final class Found {
		public final GameInfo info;
		/** Address of the announcing host; the game is reachable at this address and {@code info.tcpPort}. */
		public final InetAddress address;
		long lastSeen;

		Found(final GameInfo info, final InetAddress address, final long lastSeen) {
			this.info = info;
			this.address = address;
			this.lastSeen = lastSeen;
		}
	}

	/** Called on the loop thread. */
	public interface Listener {
		void onGameFound(Found game);

		void onGameLost(long gameId);
	}

	private final LanLoop loop;
	private final LanNetwork network;
	private final int bindPort;
	private final int targetPort;
	/** Games kept from announcements; a network flooded with made-up games cannot grow the list without end. */
	static final int MAX_GAMES = 64;

	private final Map<Long, Found> games = new HashMap<Long, Found>();
	private DatagramChannel channel;
	private SelectionKey key;
	private Listener listener;
	private GameInfo announced;
	private LanLoop.Timer announceTimer;
	private LanLoop.Timer expiryTimer;
	private boolean lockHeld;

	public LanDiscovery(final LanLoop loop, final LanNetwork network, final int bindPort, final int targetPort) {
		this.loop = loop;
		this.network = network;
		this.bindPort = bindPort;
		this.targetPort = targetPort;
	}

	public LanDiscovery(final LanLoop loop, final LanNetwork network) {
		this(loop, network, LanProtocol.DEFAULT_PORT, LanProtocol.DEFAULT_PORT);
	}

	public void setListener(final Listener listener) {
		this.listener = listener;
	}

	public void open() throws IOException {
		try {
			this.loop.call(new java.util.concurrent.Callable<Void>() {
				@Override
				public Void call() throws IOException {
					openOnLoop();
					return null;
				}
			});
		}
		catch (final IOException e) {
			throw e;
		}
		catch (final Exception e) {
			throw new IOException(e);
		}
	}

	private void openOnLoop() throws IOException {
		if (this.channel != null) {
			return;
		}
		this.network.acquireDiscoveryLock();
		this.lockHeld = true;
		final DatagramChannel ch = DatagramChannel.open();
		try {
			ch.setOption(StandardSocketOptions.SO_REUSEADDR, Boolean.TRUE);
			ch.setOption(StandardSocketOptions.SO_BROADCAST, Boolean.TRUE);
			ch.bind(new InetSocketAddress(this.bindPort));
			ch.configureBlocking(false);
			this.key = ch.register(this.loop.selector(), SelectionKey.OP_READ, this);
		}
		catch (final IOException e) {
			ch.close();
			releaseLock();
			throw e;
		}
		this.channel = ch;
		this.loop.addShutdownHook(new Runnable() {
			@Override
			public void run() {
				closeOnLoop();
			}
		});
		scheduleExpiry();
	}

	public int boundPort() {
		return this.channel == null ? -1 : ((InetSocketAddress) this.channel.socket().getLocalSocketAddress()).getPort();
	}

	private void releaseLock() {
		if (this.lockHeld) {
			this.lockHeld = false;
			this.network.releaseDiscoveryLock();
		}
	}

	/** Broadcasts a SEARCH; announcing hosts answer with their GAME_INFO. */
	public void search() {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				broadcast(new LanCodec.Search(LanProtocol.PROTOCOL_VERSION));
			}
		});
	}

	/** Sends a SEARCH to one address: a host there answers if it has a game, whatever the broadcasts do. */
	public void searchAt(final InetAddress address) {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				if (LanDiscovery.this.channel == null) {
					return;
				}
				try {
					sendTo(LanCodec.encodeDatagram(new LanCodec.Search(LanProtocol.PROTOCOL_VERSION)),
							new InetSocketAddress(address, LanDiscovery.this.targetPort));
				}
				catch (final LanProtocolException e) {
					LanLog.warn("cannot encode datagram", e);
				}
			}
		});
	}

	/** Starts (or updates) announcing a game: sent now and every {@code announceMs}. */
	public void announce(final GameInfo info) {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				LanDiscovery.this.announced = info;
				if (LanDiscovery.this.announceTimer == null) {
					announceTick();
				}
				else {
					broadcastAnnouncement(info);
				}
			}
		});
	}

	public void stopAnnouncing() {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				stopAnnouncingOnLoop();
			}
		});
	}

	private void stopAnnouncingOnLoop() {
		if (this.announced != null) {
			broadcast(new LanCodec.GameGone(this.announced.gameId));
		}
		this.announced = null;
		if (this.announceTimer != null) {
			this.announceTimer.cancel();
			this.announceTimer = null;
		}
	}

	private void announceTick() {
		this.announceTimer = null;
		if (this.announced == null || this.channel == null) {
			return;
		}
		broadcastAnnouncement(this.announced);
		this.announceTimer = this.loop.schedule(this.loop.timings().announceMs, new Runnable() {
			@Override
			public void run() {
				announceTick();
			}
		});
	}

	private void broadcastAnnouncement(final GameInfo info) {
		broadcast(new LanCodec.GameAnnounce(info));
		broadcast(new LanCodec.GamePortrait(info.gameId, info.portrait));
	}

	/** Snapshot of the known games. */
	public List<Found> games() {
		try {
			return this.loop.call(new java.util.concurrent.Callable<List<Found>>() {
				@Override
				public List<Found> call() {
					return new ArrayList<Found>(LanDiscovery.this.games.values());
				}
			});
		}
		catch (final Exception e) {
			return new ArrayList<Found>();
		}
	}

	public void close() {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				closeOnLoop();
			}
		});
	}

	private void closeOnLoop() {
		stopAnnouncingOnLoop();
		if (this.expiryTimer != null) {
			this.expiryTimer.cancel();
			this.expiryTimer = null;
		}
		if (this.key != null) {
			this.key.cancel();
		}
		if (this.channel != null) {
			try {
				this.channel.close();
			}
			catch (final IOException e) {
				LanLog.warn("closing discovery socket", e);
			}
			this.channel = null;
		}
		releaseLock();
		this.games.clear();
	}

	private void broadcast(final LanCodec.Datagram d) {
		if (this.channel == null) {
			return;
		}
		final byte[] data;
		try {
			data = LanCodec.encodeDatagram(d);
		}
		catch (final LanProtocolException e) {
			LanLog.warn("cannot encode datagram", e);
			return;
		}
		for (final InetAddress a : this.network.broadcastAddresses()) {
			sendTo(data, new InetSocketAddress(a, this.targetPort));
		}
	}

	private void sendTo(final byte[] data, final SocketAddress to) {
		try {
			this.channel.send(ByteBuffer.wrap(data), to);
		}
		catch (final IOException e) {
			// no route on this interface; other targets may still work
		}
	}

	@Override
	public void onKey(final SelectionKey k) throws IOException {
		final byte[] buf = new byte[2048];
		for (int i = 0; i < 32 && this.channel != null; i++) {
			final ByteBuffer bb = ByteBuffer.wrap(buf);
			final SocketAddress from = this.channel.receive(bb);
			if (from == null) {
				return;
			}
			handle(buf, bb.position(), (InetSocketAddress) from);
		}
	}

	private void handle(final byte[] buf, final int len, final InetSocketAddress from) {
		final LanCodec.Datagram d;
		try {
			d = LanCodec.decodeDatagram(buf, 0, len);
		}
		catch (final LanProtocolException e) {
			return;
		}
		if (d == null) {
			return;
		}
		if (d instanceof LanCodec.Search) {
			if (this.announced != null) {
				try {
					sendTo(LanCodec.encodeDatagram(new LanCodec.GameAnnounce(this.announced)), from);
					sendTo(LanCodec.encodeDatagram(
							new LanCodec.GamePortrait(this.announced.gameId, this.announced.portrait)), from);
				}
				catch (final LanProtocolException e) {
					LanLog.warn("cannot encode announcement", e);
				}
			}
		}
		else if (d instanceof LanCodec.GameAnnounce) {
			final GameInfo info = ((LanCodec.GameAnnounce) d).info;
			if (this.announced != null && info.gameId == this.announced.gameId) {
				return;
			}
			final Found known = this.games.get(info.gameId);
			if (known == null && this.games.size() >= MAX_GAMES) {
				return;
			}
			if (known != null) {
				// the portrait travels in its own datagram; keep what it told us between announcements
				info.portrait = known.info.portrait;
			}
			final Found f = new Found(info, from.getAddress(), LanLoop.nowMs());
			this.games.put(info.gameId, f);
			if (this.listener != null) {
				this.listener.onGameFound(f);
			}
		}
		else if (d instanceof LanCodec.GamePortrait) {
			final LanCodec.GamePortrait p = (LanCodec.GamePortrait) d;
			final Found f = this.games.get(p.gameId);
			if (f != null && !p.portrait.equals(f.info.portrait)) {
				// the listener holds the old Found; hand it a new one instead of changing it under the reader
				final GameInfo updated = f.info.copy();
				updated.portrait = p.portrait;
				final Found nf = new Found(updated, f.address, f.lastSeen);
				this.games.put(p.gameId, nf);
				if (this.listener != null) {
					this.listener.onGameFound(nf);
				}
			}
		}
		else if (d instanceof LanCodec.GameGone) {
			final long id = ((LanCodec.GameGone) d).gameId;
			if (this.games.remove(id) != null && this.listener != null) {
				this.listener.onGameLost(id);
			}
		}
	}

	private void scheduleExpiry() {
		this.expiryTimer = this.loop.schedule(Math.max(50, this.loop.timings().announceMs / 2), new Runnable() {
			@Override
			public void run() {
				expire();
			}
		});
	}

	private void expire() {
		final long limit = LanLoop.nowMs() - this.loop.timings().gameExpiryMs;
		final Iterator<Map.Entry<Long, Found>> it = this.games.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry<Long, Found> e = it.next();
			if (e.getValue().lastSeen < limit) {
				it.remove();
				if (this.listener != null) {
					this.listener.onGameLost(e.getKey());
				}
			}
		}
		if (this.channel != null) {
			scheduleExpiry();
		}
	}
}
