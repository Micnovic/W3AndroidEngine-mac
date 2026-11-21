package net.warsmash.lan.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;

import net.warsmash.lan.protocol.LanTimings;

/**
 * The single {@code lan-net} thread: one selector for every TCP and UDP socket, one timer queue, one task queue.
 * All network state (connections, host and client logic) is touched only from this thread.
 *
 * The old shared nio channel wrapper was not reused: it connects with a blocking call, has no timers, no write
 * backpressure, and its read buffers are sized per channel instead of bounded by the protocol.
 */
public final class LanLoop {
	/** Registered with a selection key; called on the loop thread. */
	interface Handler {
		void onKey(SelectionKey key) throws IOException;
	}

	public static final class Timer implements Comparable<Timer> {
		private final long due;
		private final long order;
		private final Runnable task;
		private volatile boolean cancelled;

		Timer(final long due, final long order, final Runnable task) {
			this.due = due;
			this.order = order;
			this.task = task;
		}

		public void cancel() {
			this.cancelled = true;
		}

		@Override
		public int compareTo(final Timer o) {
			if (this.due != o.due) {
				return this.due < o.due ? -1 : 1;
			}
			return this.order < o.order ? -1 : (this.order == o.order ? 0 : 1);
		}
	}

	public interface AcceptListener {
		/** Set the connection's listener here; reading starts after this returns. */
		void onAccept(LanConnection connection);
	}

	public interface ConnectCallback {
		void onConnected(LanConnection connection);

		void onFailed(IOException error);
	}

	public final class ServerHandle implements Handler {
		private final ServerSocketChannel channel;
		private final AcceptListener listener;
		private SelectionKey key;

		ServerHandle(final ServerSocketChannel channel, final AcceptListener listener) {
			this.channel = channel;
			this.listener = listener;
		}

		public int port() {
			return ((InetSocketAddress) this.channel.socket().getLocalSocketAddress()).getPort();
		}

		public void close() {
			run(new Runnable() {
				@Override
				public void run() {
					closeQuietly();
				}
			});
		}

		void closeQuietly() {
			LanLoop.this.servers.remove(this);
			try {
				this.channel.close();
			}
			catch (final IOException e) {
				LanLog.warn("closing server socket", e);
			}
		}

		@Override
		public void onKey(final SelectionKey k) throws IOException {
			for (int i = 0; i < 16; i++) {
				final SocketChannel ch = this.channel.accept();
				if (ch == null) {
					return;
				}
				LanConnection c = null;
				try {
					ch.configureBlocking(false);
					c = new LanConnection(LanLoop.this, ch);
					this.listener.onAccept(c);
					c.start(null);
				}
				catch (final IOException | RuntimeException e) {
					LanLog.warn("accept failed", e);
					if (c != null) {
						c.close("accept failed");
					}
					else {
						ch.close();
					}
				}
			}
		}
	}

	private static final long CALL_POLL_MS = 100;

	private final String name;
	private final LanTimings timings;
	private final Selector selector;
	private final Thread thread;
	private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();
	private final PriorityQueue<Timer> timers = new PriorityQueue<Timer>();
	private final Set<LanConnection> connections = new HashSet<LanConnection>();
	private final Set<ServerHandle> servers = new HashSet<ServerHandle>();
	private final Set<Runnable> shutdownHooks = new HashSet<Runnable>();
	private volatile boolean running;
	private volatile boolean started;
	/** Set once the loop has cleaned up: no task queued after it runs any more. */
	private volatile boolean finished;
	private volatile LanFaultInjector injector;
	private long timerOrder;

	public LanLoop(final String name, final LanTimings timings) throws IOException {
		this.name = name;
		this.timings = timings;
		this.selector = Selector.open();
		this.thread = new Thread(new Runnable() {
			@Override
			public void run() {
				runLoop();
			}
		}, name);
		this.thread.setDaemon(true);
	}

	public LanLoop() throws IOException {
		this("lan-net", new LanTimings());
	}

	public static long nowMs() {
		return System.nanoTime() / 1000000L;
	}

	public LanTimings timings() {
		return this.timings;
	}

	public void start() {
		this.started = true;
		this.running = true;
		this.thread.start();
	}

	public boolean isLoopThread() {
		return Thread.currentThread() == this.thread;
	}

	public void setFaultInjector(final LanFaultInjector injector) {
		if (injector != null) {
			injector.attach(this);
		}
		this.injector = injector;
	}

	LanFaultInjector faultInjector() {
		return this.injector;
	}

	/** Queues a task for the loop thread. */
	public void execute(final Runnable task) {
		this.tasks.add(task);
		try {
			this.selector.wakeup();
		}
		catch (final java.nio.channels.ClosedSelectorException e) {
			// loop already shut down
		}
	}

	/** Runs the task now when called on the loop thread, otherwise queues it. */
	public void run(final Runnable task) {
		if (isLoopThread()) {
			task.run();
		}
		else {
			execute(task);
		}
	}

	public <T> T call(final Callable<T> callable) throws Exception {
		if (isLoopThread()) {
			return callable.call();
		}
		final FutureTask<T> f = new FutureTask<T>(callable);
		execute(f);
		try {
			while (true) {
				try {
					return f.get(CALL_POLL_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
				}
				catch (final java.util.concurrent.TimeoutException e) {
					// A loop that has stopped (a session closed while its listeners still ask it) runs no more tasks;
					// nothing else touches its state then, so the caller answers for it rather than waiting forever.
					if (this.finished && this.tasks.remove(f)) {
						f.run();
					}
				}
			}
		}
		catch (final java.util.concurrent.ExecutionException e) {
			final Throwable c = e.getCause();
			if (c instanceof Exception) {
				throw (Exception) c;
			}
			throw e;
		}
	}

	public Timer schedule(final long delayMs, final Runnable task) {
		if (isLoopThread()) {
			return addTimer(delayMs, task);
		}
		// the handle is created on the caller's thread so it can be cancelled before the loop sees it
		final long due = nowMs() + delayMs;
		final Timer handle = new Timer(due, 0, task);
		execute(new Runnable() {
			@Override
			public void run() {
				LanLoop.this.timers.add(new Timer(due, LanLoop.this.timerOrder++, new Runnable() {
					@Override
					public void run() {
						if (!handle.cancelled) {
							task.run();
						}
					}
				}));
			}
		});
		return handle;
	}

	private Timer addTimer(final long delayMs, final Runnable task) {
		final Timer t = new Timer(nowMs() + delayMs, this.timerOrder++, task);
		this.timers.add(t);
		return t;
	}

	void register(final LanConnection c) {
		this.connections.add(c);
	}

	void unregister(final LanConnection c) {
		this.connections.remove(c);
	}

	void closeAllConnections(final String reason) {
		for (final LanConnection c : new ArrayList<LanConnection>(this.connections)) {
			c.close(reason);
		}
	}

	/** Hooks run on the loop thread when the loop shuts down (hosts and clients release their resources there). */
	public void addShutdownHook(final Runnable hook) {
		run(new Runnable() {
			@Override
			public void run() {
				LanLoop.this.shutdownHooks.add(hook);
			}
		});
	}

	Selector selector() {
		return this.selector;
	}

	public ServerHandle listen(final int port, final int tries, final AcceptListener listener) throws IOException {
		try {
			return call(new Callable<ServerHandle>() {
				@Override
				public ServerHandle call() throws IOException {
					IOException last = null;
					for (int i = 0; i < Math.max(1, port == 0 ? 1 : tries); i++) {
						final ServerSocketChannel ch = ServerSocketChannel.open();
						try {
							ch.setOption(StandardSocketOptions.SO_REUSEADDR, Boolean.TRUE);
							ch.bind(new InetSocketAddress(port == 0 ? 0 : port + i));
							ch.configureBlocking(false);
							final ServerHandle h = new ServerHandle(ch, listener);
							h.key = ch.register(LanLoop.this.selector, SelectionKey.OP_ACCEPT, h);
							LanLoop.this.servers.add(h);
							return h;
						}
						catch (final IOException e) {
							last = e;
							ch.close();
						}
					}
					throw last;
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

	private final class ConnectOp implements Handler {
		private final SocketChannel channel;
		private final ConnectCallback callback;
		private Timer timeout;
		private boolean done;

		ConnectOp(final SocketChannel channel, final ConnectCallback callback) {
			this.channel = channel;
			this.callback = callback;
		}

		void fail(final IOException e) {
			if (this.done) {
				return;
			}
			this.done = true;
			if (this.timeout != null) {
				this.timeout.cancel();
			}
			try {
				this.channel.close();
			}
			catch (final IOException ignored) {
				// already failing
			}
			this.callback.onFailed(e);
		}

		void succeed(final SelectionKey key) throws IOException {
			if (this.done) {
				return;
			}
			final LanConnection c;
			try {
				c = new LanConnection(LanLoop.this, this.channel);
			}
			catch (final IOException e) {
				fail(e);
				return;
			}
			this.done = true;
			if (this.timeout != null) {
				this.timeout.cancel();
			}
			this.callback.onConnected(c);
			try {
				c.start(key);
			}
			catch (final IOException e) {
				// the caller holds the connection now: it hears of the failure as a close
				c.close("connect failed: " + e.getMessage());
			}
		}

		@Override
		public void onKey(final SelectionKey key) throws IOException {
			try {
				if (this.channel.finishConnect()) {
					succeed(key);
				}
			}
			catch (final IOException e) {
				key.cancel();
				fail(e);
			}
		}
	}

	/** Connects without blocking; the callback runs on the loop thread. */
	public void connect(final InetSocketAddress address, final int timeoutMs, final ConnectCallback callback) {
		run(new Runnable() {
			@Override
			public void run() {
				SocketChannel ch = null;
				try {
					ch = SocketChannel.open();
					ch.configureBlocking(false);
					final ConnectOp op = new ConnectOp(ch, callback);
					if (ch.connect(address)) {
						op.succeed(null);
						return;
					}
					ch.register(LanLoop.this.selector, SelectionKey.OP_CONNECT, op);
					op.timeout = addTimer(timeoutMs, new Runnable() {
						@Override
						public void run() {
							op.fail(new IOException("connect timed out"));
						}
					});
				}
				catch (final IOException e) {
					try {
						if (ch != null) {
							ch.close();
						}
					}
					catch (final IOException ignored) {
						// already failing
					}
					callback.onFailed(e);
				}
			}
		});
	}

	private void runLoop() {
		try {
			while (this.running) {
				long wait = 500;
				final Timer next = this.timers.peek();
				if (next != null) {
					wait = Math.max(0, Math.min(wait, next.due - nowMs()));
				}
				if (!this.tasks.isEmpty()) {
					wait = 0;
				}
				if (wait == 0) {
					this.selector.selectNow();
				}
				else {
					this.selector.select(wait);
				}
				dispatchKeys();
				runTasks();
				runTimers();
			}
		}
		catch (final IOException e) {
			LanLog.warn(this.name + " selector failed", e);
		}
		finally {
			cleanup();
			this.finished = true;
		}
	}

	private void dispatchKeys() {
		final Iterator<SelectionKey> it = this.selector.selectedKeys().iterator();
		while (it.hasNext()) {
			final SelectionKey key = it.next();
			it.remove();
			final Object att = key.attachment();
			if (!key.isValid() || !(att instanceof Handler)) {
				continue;
			}
			try {
				((Handler) att).onKey(key);
			}
			catch (final IOException e) {
				LanLog.warn("channel error", e);
				if (att instanceof LanConnection) {
					((LanConnection) att).close("io error: " + e.getMessage());
				}
			}
			catch (final RuntimeException e) {
				LanLog.warn("handler failed", e);
				if (att instanceof LanConnection) {
					((LanConnection) att).close("handler failed");
				}
			}
		}
	}

	private void runTasks() {
		Runnable r;
		int budget = 10000;
		while (budget-- > 0 && (r = this.tasks.poll()) != null) {
			try {
				r.run();
			}
			catch (final RuntimeException e) {
				LanLog.warn("task failed", e);
			}
		}
	}

	private void runTimers() {
		final long now = nowMs();
		Timer t;
		while ((t = this.timers.peek()) != null && t.due <= now) {
			this.timers.poll();
			if (t.cancelled) {
				continue;
			}
			try {
				t.task.run();
			}
			catch (final RuntimeException e) {
				LanLog.warn("timer failed", e);
			}
		}
	}

	private void cleanup() {
		runTasks();
		for (final Runnable hook : new ArrayList<Runnable>(this.shutdownHooks)) {
			try {
				hook.run();
			}
			catch (final RuntimeException e) {
				LanLog.warn("shutdown hook failed", e);
			}
		}
		for (final ServerHandle s : new ArrayList<ServerHandle>(this.servers)) {
			s.closeQuietly();
		}
		drainConnections();
		closeAllConnections("loop shut down");
		for (final SelectionKey k : this.selector.keys()) {
			try {
				k.channel().close();
			}
			catch (final IOException ignored) {
				// shutting down
			}
		}
		try {
			this.selector.close();
		}
		catch (final IOException e) {
			LanLog.warn("closing selector", e);
		}
		this.timers.clear();
	}

	/**
	 * Closing a socket right after the last write can reset it before the peer has read that write (a LEAVE). Every
	 * connection gets its queue flushed and its output shut down, then the peer a short, bounded time to close.
	 * Runs on the loop thread, a daemon, so it never holds the application back.
	 */
	private void drainConnections() {
		if (this.connections.isEmpty()) {
			return;
		}
		for (final LanConnection c : new ArrayList<LanConnection>(this.connections)) {
			c.drainAndClose("loop shut down");
		}
		final long end = nowMs() + SHUTDOWN_DRAIN_MS;
		try {
			while (!this.connections.isEmpty()) {
				final long left = end - nowMs();
				if (left <= 0) {
					return;
				}
				this.selector.select(Math.min(left, 20));
				dispatchKeys();
				runTasks();
			}
		}
		catch (final IOException | RuntimeException e) {
			LanLog.warn(this.name + " drain failed", e);
		}
	}

	/** The longest {@link #drainConnections} waits for the peers. */
	static final long SHUTDOWN_DRAIN_MS = 500;

	/** How long a caller that must stay responsive (a screen's thread) waits for the loop to close everything. */
	public static final long RESPONSIVE_WAIT_MS = 400;

	/** Stops the thread after closing everything; blocks (briefly) unless called on the loop thread. */
	public void shutdown() {
		shutdown(5000);
	}

	/**
	 * As {@link #shutdown()}, but waits at most {@code maxWaitMs} for the thread to end; a loop still closing after
	 * that finishes on its own, and {@link #call} on it is answered by the caller once it has finished.
	 */
	public void shutdown(final long maxWaitMs) {
		this.running = false;
		if (!this.started) {
			cleanup();
			this.finished = true;
			return;
		}
		try {
			this.selector.wakeup();
		}
		catch (final RuntimeException e) {
			// selector already closed
		}
		if ((maxWaitMs > 0) && !isLoopThread() && this.thread.isAlive()) {
			try {
				this.thread.join(maxWaitMs);
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
