package com.etheller.warsmash.viewer5;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Textures whose decoding and compressing runs on helper threads while the game thread goes on with other loading,
 * and whose last step, the upload, runs on the game thread once the helper is done.
 * <p>
 * The helpers start the next texture themselves when they finish one, so they do not wait for the game thread to come
 * back for a texture first. What stops them running ahead is the bytes of the results that are done but not yet
 * uploaded.
 */
public final class TextureLoadQueue implements AutoCloseable {
	private final ExecutorService executor;
	private final int threads;
	private final long readyLimit;
	private final Object lock = new Object();
	private final Deque<Entry> entries = new ArrayDeque<>();
	private int running;
	private long readyBytes;
	private boolean closed;

	/**
	 * @param executor   runs the preparations, with this many threads
	 * @param readyLimit the bytes of finished preparations after which no further one starts
	 */
	public TextureLoadQueue(final ExecutorService executor, final int threads, final long readyLimit) {
		this.executor = executor;
		this.threads = threads;
		this.readyLimit = readyLimit;
	}

	/**
	 * @param prepare runs on a helper thread and may not touch OpenGL or anything the game thread uses; it returns the
	 *                bytes of the result it holds
	 * @param finish  runs on the game thread after prepare, from {@link #loadNext()}
	 */
	public void add(final LongSupplier prepare, final Runnable finish) {
		synchronized (this.lock) {
			if (this.closed) {
				return;
			}
			this.entries.add(new Entry(prepare, finish));
			startReady();
		}
	}

	/**
	 * Drops the queued textures. Preparations that have not started when their helper gets to them return at once, so
	 * a shared executor is not held up by the work of a viewer that is gone; one already running finishes alone.
	 */
	@Override
	public void close() {
		synchronized (this.lock) {
			this.closed = true;
			this.entries.clear();
			this.readyBytes = 0;
		}
	}

	public boolean isEmpty() {
		synchronized (this.lock) {
			return this.entries.isEmpty();
		}
	}

	public int size() {
		synchronized (this.lock) {
			return this.entries.size();
		}
	}

	/** Finishes the first queued texture whose preparation is done; when none is done it waits a moment for the first. */
	public void loadNext() {
		if (finishReady()) {
			return;
		}
		final Future<?> first;
		synchronized (this.lock) {
			final Entry head = this.entries.peekFirst();
			first = head == null ? null : head.future;
		}
		if (first != null) {
			try {
				first.get(2, TimeUnit.MILLISECONDS);
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			catch (final java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
				// a failed preparation is seen as an unprepared texture by its finish
			}
		}
	}

	/** Finishes the first queued texture whose preparation is done, without waiting; false when none is done yet. */
	public boolean finishReady() {
		Entry done = null;
		synchronized (this.lock) {
			startReady();
			for (final Iterator<Entry> iterator = this.entries.iterator(); iterator.hasNext();) {
				final Entry entry = iterator.next();
				if (entry.done) {
					iterator.remove();
					done = entry;
					break;
				}
			}
		}
		if (done == null) {
			return false;
		}
		try {
			done.finish.run();
		}
		finally {
			// the result holds its memory until the upload is over
			synchronized (this.lock) {
				if (!this.closed) {
					this.readyBytes -= done.bytes;
					startReady();
				}
			}
		}
		return true;
	}

	/** Starts the preparations in order while a helper is free and the finished results are not too many. */
	private void startReady() {
		if (this.closed) {
			return;
		}
		for (final Entry entry : this.entries) {
			if (entry.future != null) {
				continue;
			}
			if ((this.running >= this.threads) || (this.readyBytes >= this.readyLimit)) {
				return;
			}
			this.running++;
			entry.future = this.executor.submit(() -> run(entry));
		}
	}

	private void run(final Entry entry) {
		long bytes = 0;
		try {
			synchronized (this.lock) {
				if (this.closed) {
					return;
				}
			}
			bytes = entry.prepare.getAsLong();
		}
		finally {
			synchronized (this.lock) {
				entry.bytes = bytes;
				entry.done = true;
				this.running--;
				if (!this.closed) {
					this.readyBytes += bytes;
					startReady();
				}
			}
		}
	}

	private static final class Entry {
		private final LongSupplier prepare;
		private final Runnable finish;
		private Future<?> future;
		private boolean done;
		private long bytes;

		private Entry(final LongSupplier prepare, final Runnable finish) {
			this.prepare = prepare;
			this.finish = finish;
		}
	}
}
