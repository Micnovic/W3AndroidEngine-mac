package net.warsmash.lan.host;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.transport.LanConnection;
import net.warsmash.lan.transport.LanLoop;
import net.warsmash.lan.transport.LanLog;

/**
 * Sends the lobby's map file to participants that ask for it, in 16 KB chunks over their normal link. A transfer
 * never queues more than {@link #HIGH_WATER} bytes on a connection and goes on only when the socket has taken most of
 * it, so a turn or a chat line sent meanwhile waits behind at most that much, and a slow receiver costs the host no
 * memory. Between steps the loop thread serves everyone else. Everything runs on the loop thread.
 */
public final class MapServer {
	public interface Listener {
		/** Progress of the bytes handed to the connection; {@code sent == total} when everything went out. */
		void onSending(int participantId, long sent, int total);
	}

	static final int HIGH_WATER = 256 * 1024;
	private static final int CHUNKS_PER_STEP = 8;
	private static final long EVENT_INTERVAL_MS = 100;

	private final class Transfer implements Runnable {
		final int participantId;
		final LanConnection conn;
		final RandomAccessFile file;
		final byte[] buffer = new byte[LanProtocol.MAX_MAP_CHUNK];
		long offset;
		boolean waiting;
		boolean scheduled;
		boolean finished;
		long lastEvent;

		Transfer(final int participantId, final LanConnection conn, final RandomAccessFile file, final long offset) {
			this.participantId = participantId;
			this.conn = conn;
			this.file = file;
			this.offset = offset;
		}

		@Override
		public void run() {
			this.scheduled = false;
			step(this);
		}
	}

	private final LanLoop loop;
	private final Listener listener;
	private final Map<Integer, Transfer> transfers = new HashMap<Integer, Transfer>();
	private File file;
	private int size;

	public MapServer(final LanLoop loop, final Listener listener) {
		this.loop = loop;
		this.listener = listener;
	}

	/** Chooses the file to serve; running transfers of the previous map stop. */
	public void setMap(final File file, final int size) {
		cancelAll();
		this.file = file;
		this.size = size;
	}

	public boolean hasMap() {
		return this.file != null;
	}

	public int size() {
		return this.size;
	}

	/**
	 * Starts (or restarts) sending to a participant from {@code offset}. Returns false if there is nothing to send or
	 * the file no longer has the announced size. A request at the end is refused: the receiver has every byte and
	 * never asks, and a chunk without data is a protocol violation for it.
	 */
	public boolean serve(final int participantId, final LanConnection conn, final int offset) {
		cancel(participantId);
		if (this.file == null || offset < 0 || offset >= this.size) {
			return false;
		}
		final RandomAccessFile raf;
		try {
			raf = new RandomAccessFile(this.file, "r");
			if (raf.length() != this.size) {
				raf.close();
				LanLog.warn("map file changed after it was announced");
				return false;
			}
			raf.seek(offset);
		}
		catch (final IOException e) {
			LanLog.warn("cannot open the map for sending", e);
			return false;
		}
		final Transfer t = new Transfer(participantId, conn, raf, offset);
		this.transfers.put(participantId, t);
		conn.setDrainListener(new Runnable() {
			@Override
			public void run() {
				if (t.waiting) {
					t.waiting = false;
					schedule(t);
				}
			}
		});
		schedule(t);
		return true;
	}

	public void cancel(final int participantId) {
		final Transfer t = this.transfers.remove(participantId);
		if (t != null) {
			end(t);
		}
	}

	public void cancelAll() {
		for (final Transfer t : new ArrayList<Transfer>(this.transfers.values())) {
			end(t);
		}
		this.transfers.clear();
	}

	private void end(final Transfer t) {
		t.finished = true;
		if (this.transfers.get(t.participantId) == t) {
			this.transfers.remove(t.participantId);
		}
		t.conn.setDrainListener(null);
		try {
			t.file.close();
		}
		catch (final IOException ignored) {
			// read-only handle
		}
	}

	private void schedule(final Transfer t) {
		if (!t.scheduled && !t.finished) {
			t.scheduled = true;
			this.loop.execute(t);
		}
	}

	private void step(final Transfer t) {
		if (t.finished) {
			return;
		}
		if (!t.conn.isOpen()) {
			end(t);
			return;
		}
		// frames posted by earlier steps were queued before this task ran, so the count is exact here
		long pending = t.conn.pendingBytes();
		if (pending >= HIGH_WATER) {
			t.waiting = true;
			return;
		}
		try {
			for (int i = 0; i < CHUNKS_PER_STEP && pending < HIGH_WATER && t.offset < this.size; i++) {
				final int n = (int) Math.min(t.buffer.length, this.size - t.offset);
				t.file.readFully(t.buffer, 0, n);
				t.conn.send(new LanMessages.MapChunk((int) t.offset, this.size, Arrays.copyOf(t.buffer, n)));
				t.offset += n;
				pending += n;
			}
			if (t.offset >= this.size) {
				report(t, true);
				end(t);
				return;
			}
		}
		catch (final IOException e) {
			LanLog.warn("reading the map failed", e);
			end(t);
			t.conn.close("map unreadable");
			return;
		}
		report(t, false);
		schedule(t);
	}

	private void report(final Transfer t, final boolean last) {
		final long now = LanLoop.nowMs();
		if (this.listener != null && (last || now - t.lastEvent >= EVENT_INTERVAL_MS)) {
			t.lastEvent = now;
			this.listener.onSending(t.participantId, t.offset, this.size);
		}
	}
}
