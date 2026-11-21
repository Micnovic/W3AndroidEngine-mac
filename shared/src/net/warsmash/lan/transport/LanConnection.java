package net.warsmash.lan.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;

import net.warsmash.lan.protocol.LanCodec;
import net.warsmash.lan.protocol.LanMessage;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanProtocolException;

/**
 * One TCP link. Framing, keepalive (PING every second, closed after the silence limit), write backpressure and
 * optional fault injection live here; {@link #send} and {@link #close} are safe from any thread, everything else
 * runs on the loop thread.
 */
public final class LanConnection implements LanLoop.Handler {
	public interface Listener {
		void onMessage(LanConnection connection, LanMessage message);

		/** Called exactly once, on the loop thread. */
		void onClosed(LanConnection connection, String reason);
	}

	private static final class Delayed {
		final long release;
		final LanMessage message;
		final byte[] frame;

		Delayed(final long release, final LanMessage message, final byte[] frame) {
			this.release = release;
			this.message = message;
			this.frame = frame;
		}
	}

	private final LanLoop loop;
	private final SocketChannel channel;
	private final InetSocketAddress remote;
	private final LanCodec.FrameDecoder decoder = new LanCodec.FrameDecoder();
	private final byte[] readArray = new byte[16 * 1024];
	private final ArrayDeque<ByteBuffer> outQueue = new ArrayDeque<ByteBuffer>();
	private final ArrayDeque<Delayed> inDelayed = new ArrayDeque<Delayed>();
	private final ArrayDeque<Delayed> outDelayed = new ArrayDeque<Delayed>();
	private SelectionKey key;
	private Listener listener;
	private long queuedBytes;
	private long delayedBytes;
	private Runnable drainListener;
	private long lastRecv;
	private long lastInRelease;
	private long lastOutRelease;
	private LanLoop.Timer keepalive;
	private LanLoop.Timer inTimer;
	private LanLoop.Timer outTimer;
	private boolean closed;
	private String closeAfterFlushReason;
	/** Our side is shut down after a flush; the socket stays open, unread, until the peer closes. */
	private boolean lingering;
	private volatile int rttMs = -1;
	private volatile boolean closedFlag;

	/** Free slot for the owner (host or client bookkeeping). */
	public volatile Object attachment;

	LanConnection(final LanLoop loop, final SocketChannel channel) throws IOException {
		this.loop = loop;
		this.channel = channel;
		channel.setOption(StandardSocketOptions.TCP_NODELAY, Boolean.TRUE);
		channel.setOption(StandardSocketOptions.SO_KEEPALIVE, Boolean.TRUE);
		InetSocketAddress addr = null;
		try {
			addr = (InetSocketAddress) channel.getRemoteAddress();
		}
		catch (final IOException e) {
			// closed already; reported through the first read
		}
		this.remote = addr;
		loop.register(this);
	}

	public void setListener(final Listener listener) {
		this.listener = listener;
	}

	public InetSocketAddress remoteAddress() {
		return this.remote;
	}

	/** Round trip time of the last answered PING, -1 before the first. */
	public int rttMs() {
		return this.rttMs;
	}

	public boolean isOpen() {
		return !this.closedFlag;
	}

	/** Bytes accepted by {@link #send} that the socket has not taken yet, delayed frames included. Loop thread only. */
	public long pendingBytes() {
		return this.queuedBytes + this.delayedBytes;
	}

	/**
	 * Called on the loop thread after a write when {@link #pendingBytes} is at most {@link #DRAIN_LEVEL}; lets bulk
	 * senders keep the queue short so small messages never wait behind much data. Loop thread only.
	 */
	public void setDrainListener(final Runnable listener) {
		this.drainListener = listener;
	}

	public static final int DRAIN_LEVEL = 64 * 1024;

	private void notifyDrain() {
		final Runnable l = this.drainListener;
		if (l != null && !this.closed && this.queuedBytes + this.delayedBytes <= DRAIN_LEVEL) {
			l.run();
		}
	}

	void start(final SelectionKey existing) throws IOException {
		if (existing != null) {
			this.key = existing;
			existing.attach(this);
			existing.interestOps(SelectionKey.OP_READ);
		}
		else {
			this.key = this.channel.register(this.loop.selector(), SelectionKey.OP_READ, this);
		}
		this.lastRecv = LanLoop.nowMs();
		scheduleKeepalive();
	}

	private void scheduleKeepalive() {
		this.keepalive = this.loop.schedule(this.loop.timings().keepaliveMs, new Runnable() {
			@Override
			public void run() {
				keepaliveTick();
			}
		});
	}

	private void keepaliveTick() {
		if (this.closed) {
			return;
		}
		final long now = LanLoop.nowMs();
		if (now - this.lastRecv > this.loop.timings().silenceMs) {
			close("timeout");
			return;
		}
		if (this.outDelayed.isEmpty() && !this.lingering) {
			send(new LanMessages.Ping(now));
		}
		scheduleKeepalive();
	}

	// ------------------------------------------------------------------ sending

	public static byte[] encode(final LanMessage message) {
		try {
			return LanCodec.encodeFrame(message);
		}
		catch (final LanProtocolException e) {
			throw new IllegalArgumentException("cannot encode " + message.getClass().getSimpleName(), e);
		}
	}

	/** Thread-safe. Messages from one thread keep their order. */
	public void send(final LanMessage message) {
		sendFrame(encode(message));
	}

	/** Sends an already encoded frame; lets a broadcast encode once. */
	public void sendFrame(final byte[] frame) {
		if (this.closedFlag) {
			return;
		}
		this.loop.execute(new Runnable() {
			@Override
			public void run() {
				enqueueOut(frame);
			}
		});
	}

	private void enqueueOut(final byte[] frame) {
		if (this.closed || this.lingering) {
			return;
		}
		final LanFaultInjector f = this.loop.faultInjector();
		if ((f != null && f.isActive()) || !this.outDelayed.isEmpty()) {
			final long now = LanLoop.nowMs();
			this.lastOutRelease = f != null && f.isActive() ? f.releaseTime(now, this.lastOutRelease)
					: Math.max(now, this.lastOutRelease);
			this.outDelayed.add(new Delayed(this.lastOutRelease, null, frame));
			this.delayedBytes += frame.length;
			drainOut();
		}
		else {
			writeNow(frame);
		}
	}

	private void drainOut() {
		final LanFaultInjector f = this.loop.faultInjector();
		while (!this.outDelayed.isEmpty() && !this.closed) {
			final Delayed d = this.outDelayed.peek();
			final long now = LanLoop.nowMs();
			final long release = f != null ? Math.max(d.release, f.frozenUntil()) : d.release;
			if (release > now) {
				if (this.outTimer == null) {
					this.outTimer = this.loop.schedule(release - now, new Runnable() {
						@Override
						public void run() {
							LanConnection.this.outTimer = null;
							drainOut();
						}
					});
				}
				return;
			}
			this.outDelayed.poll();
			this.delayedBytes -= d.frame.length;
			writeNow(d.frame);
		}
		maybeFinishClose();
	}

	private void writeNow(final byte[] frame) {
		if (this.closed) {
			return;
		}
		this.outQueue.add(ByteBuffer.wrap(frame));
		this.queuedBytes += frame.length;
		if (this.queuedBytes > LanProtocol.MAX_OUT_QUEUE_BYTES) {
			close("send queue overflow");
			return;
		}
		flush();
	}

	private void flush() {
		try {
			while (!this.outQueue.isEmpty()) {
				final ByteBuffer b = this.outQueue.peek();
				this.channel.write(b);
				if (b.hasRemaining()) {
					this.key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
					notifyDrain();
					return;
				}
				this.queuedBytes -= b.capacity();
				this.outQueue.poll();
			}
			this.key.interestOps(SelectionKey.OP_READ);
		}
		catch (final IOException e) {
			close("write failed: " + e.getMessage());
			return;
		}
		notifyDrain();
		maybeFinishClose();
	}

	// ---------------------------------------------------------------- receiving

	@Override
	public void onKey(final SelectionKey k) throws IOException {
		if (this.closed) {
			return;
		}
		if (k.isReadable()) {
			read();
		}
		if (!this.closed && k.isValid() && k.isWritable()) {
			flush();
		}
	}

	private void read() throws IOException {
		for (int i = 0; i < 16 && !this.closed; i++) {
			final int n;
			try {
				n = this.channel.read(ByteBuffer.wrap(this.readArray));
			}
			catch (final IOException e) {
				if (this.lingering) {
					doClose(this.closeAfterFlushReason);
					return;
				}
				throw e;
			}
			if (this.lingering) {
				if (n < 0) {
					doClose(this.closeAfterFlushReason);
					return;
				}
				if (n == 0) {
					return;
				}
				continue;
			}
			if (n < 0) {
				close("closed by peer");
				return;
			}
			if (n == 0) {
				return;
			}
			try {
				this.decoder.feed(this.readArray, 0, n, new LanCodec.MessageSink() {
					@Override
					public void accept(final LanMessage m) {
						received(m);
					}
				});
			}
			catch (final LanProtocolException e) {
				close("protocol error: " + e.getMessage());
				return;
			}
		}
	}

	private void received(final LanMessage m) {
		if (this.closed) {
			return;
		}
		final LanFaultInjector f = this.loop.faultInjector();
		if ((f != null && f.isActive()) || !this.inDelayed.isEmpty()) {
			final long now = LanLoop.nowMs();
			this.lastInRelease = f != null && f.isActive() ? f.releaseTime(now, this.lastInRelease)
					: Math.max(now, this.lastInRelease);
			this.inDelayed.add(new Delayed(this.lastInRelease, m, null));
			drainIn();
		}
		else {
			deliver(m);
		}
	}

	private void drainIn() {
		final LanFaultInjector f = this.loop.faultInjector();
		while (!this.inDelayed.isEmpty() && !this.closed) {
			final Delayed d = this.inDelayed.peek();
			final long now = LanLoop.nowMs();
			final long release = f != null ? Math.max(d.release, f.frozenUntil()) : d.release;
			if (release > now) {
				if (this.inTimer == null) {
					this.inTimer = this.loop.schedule(release - now, new Runnable() {
						@Override
						public void run() {
							LanConnection.this.inTimer = null;
							drainIn();
						}
					});
				}
				return;
			}
			this.inDelayed.poll();
			deliver(d.message);
		}
	}

	private void deliver(final LanMessage m) {
		this.lastRecv = LanLoop.nowMs();
		if (m instanceof LanMessages.Ping) {
			send(new LanMessages.Pong(((LanMessages.Ping) m).stamp));
			return;
		}
		if (m instanceof LanMessages.Pong) {
			this.rttMs = (int) Math.max(0, this.lastRecv - ((LanMessages.Pong) m).stamp);
			return;
		}
		final Listener l = this.listener;
		if (l == null) {
			return;
		}
		try {
			l.onMessage(this, m);
		}
		catch (final RuntimeException e) {
			LanLog.warn("message handler failed", e);
			close("handler failed");
		}
	}

	// ------------------------------------------------------------------ closing

	/** Closes at once. Safe from any thread. */
	public void close(final String reason) {
		this.loop.run(new Runnable() {
			@Override
			public void run() {
				doClose(reason);
			}
		});
	}

	/** Closes after everything queued so far was written (REJECT, KICKED); gives up after two seconds. */
	public void closeAfterFlush(final String reason) {
		// always queued, so it runs after the sends the caller made just before
		this.loop.execute(new Runnable() {
			@Override
			public void run() {
				if (LanConnection.this.closed) {
					return;
				}
				LanConnection.this.closeAfterFlushReason = reason;
				LanConnection.this.loop.schedule(2000, new Runnable() {
					@Override
					public void run() {
						doClose(reason);
					}
				});
				maybeFinishClose();
			}
		});
	}

	/** Loop thread only: as {@link #closeAfterFlush} for a loop that is shutting down, without the timer. */
	void drainAndClose(final String reason) {
		if (this.closed) {
			return;
		}
		if (this.closeAfterFlushReason == null) {
			this.closeAfterFlushReason = reason;
		}
		maybeFinishClose();
	}

	private void maybeFinishClose() {
		if (this.closeAfterFlushReason != null && !this.closed && this.outQueue.isEmpty()
				&& this.outDelayed.isEmpty() && !this.lingering) {
			// Closing a socket that still has unread input resets the connection, and the peer then loses what we
			// sent last (a LEAVE, a REJECT). Half-close, so everything arrives, and wait for the peer to close.
			this.lingering = true;
			try {
				this.channel.shutdownOutput();
			}
			catch (final IOException e) {
				doClose(this.closeAfterFlushReason);
			}
		}
	}

	private void doClose(final String reason) {
		if (this.closed) {
			return;
		}
		this.closed = true;
		this.closedFlag = true;
		if (this.keepalive != null) {
			this.keepalive.cancel();
		}
		if (this.inTimer != null) {
			this.inTimer.cancel();
		}
		if (this.outTimer != null) {
			this.outTimer.cancel();
		}
		if (this.key != null) {
			this.key.cancel();
		}
		try {
			this.channel.close();
		}
		catch (final IOException e) {
			LanLog.warn("closing channel", e);
		}
		this.loop.unregister(this);
		this.outQueue.clear();
		this.inDelayed.clear();
		this.outDelayed.clear();
		this.delayedBytes = 0;
		this.drainListener = null;
		final Listener l = this.listener;
		if (l != null) {
			try {
				l.onClosed(this, reason);
			}
			catch (final RuntimeException e) {
				LanLog.warn("close handler failed", e);
			}
		}
	}
}
