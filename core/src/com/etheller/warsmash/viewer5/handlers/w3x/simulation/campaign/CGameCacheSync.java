package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.etheller.warsmash.util.Log;

/**
 * SyncStored*: the value one machine holds in a game cache, carried to every machine as a sync command of the player
 * that called it, so all caches end up with the same value at the same step. A value is cut into chunks that fit a sync
 * command; the chunks of one player arrive in order, and the value is applied when the last one is in.
 */
public final class CGameCacheSync {
	/** The first character is one no script's prefix may start with (BlzSendSyncData refuses it). */
	public static final String PREFIX = "\u0001gamecache";
	/** What a sync command of this kind may carry: the chunk and its position. */
	public static final int MAX_DATA_BYTES = 512;
	private static final int CHUNK_CHARS = 400;
	private static final int MAX_VALUE_CHARS = 16 * 1024;

	private static final byte INTEGER = 0;
	private static final byte REAL = 1;
	private static final byte BOOLEAN = 2;
	private static final byte STRING = 3;
	private static final byte UNIT = 4;

	private CGameCacheSync() {
	}

	public static boolean isCacheSync(final String prefix) {
		return PREFIX.equals(prefix);
	}

	/** The chunks to send for the local value of an integer, or none when this machine holds no such value. */
	public static List<String> integer(final CGameCache cache, final String mission, final String key) {
		final Integer value = cache == null ? null : cache.getInteger(mission, key);
		return value == null ? new ArrayList<>() : encode(INTEGER, cache, mission, key, out -> out.writeInt(value));
	}

	public static List<String> real(final CGameCache cache, final String mission, final String key) {
		final Float value = cache == null ? null : cache.getReal(mission, key);
		return value == null ? new ArrayList<>() : encode(REAL, cache, mission, key, out -> out.writeFloat(value));
	}

	public static List<String> bool(final CGameCache cache, final String mission, final String key) {
		final Boolean value = cache == null ? null : cache.getBoolean(mission, key);
		return value == null ? new ArrayList<>() : encode(BOOLEAN, cache, mission, key, out -> out.writeBoolean(value));
	}

	public static List<String> string(final CGameCache cache, final String mission, final String key) {
		final String value = cache == null ? null : cache.getString(mission, key);
		return value == null ? new ArrayList<>() : encode(STRING, cache, mission, key, out -> out.writeUTF(value));
	}

	public static List<String> unit(final CGameCache cache, final String mission, final String key) {
		final CStoredUnit value = cache == null ? null : cache.getUnit(mission, key);
		return value == null ? new ArrayList<>() : encode(UNIT, cache, mission, key, value::write);
	}

	private interface ValueWriter {
		void write(DataOutputStream out) throws IOException;
	}

	private static List<String> encode(final byte type, final CGameCache cache, final String mission, final String key,
			final ValueWriter writer) {
		final List<String> chunks = new ArrayList<>();
		try {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			final DataOutputStream out = new DataOutputStream(bytes);
			out.writeByte(type);
			out.writeUTF(cache.getFileName());
			out.writeUTF(mission == null ? "" : mission);
			out.writeUTF(key == null ? "" : key);
			writer.write(out);
			final String text = Base64.getEncoder().withoutPadding().encodeToString(bytes.toByteArray());
			if (text.length() > MAX_VALUE_CHARS) {
				return chunks;
			}
			final int count = (text.length() + CHUNK_CHARS - 1) / CHUNK_CHARS;
			for (int i = 0; i < count; i++) {
				chunks.add(i + "/" + count + "|"
						+ text.substring(i * CHUNK_CHARS, Math.min(text.length(), (i + 1) * CHUNK_CHARS)));
			}
		}
		catch (final IOException | RuntimeException e) {
			Log.error(e);
			chunks.clear();
		}
		return chunks;
	}

	/** What the executor of one player keeps of the chunks that came so far. */
	public static final class Receiver {
		private final StringBuilder text = new StringBuilder();
		private int next;

		public void accept(final String data) {
			try {
				final int bar = data.indexOf('|');
				final int slash = data.indexOf('/');
				if ((slash < 0) || (bar < slash)) {
					reset();
					return;
				}
				final int index = Integer.parseInt(data.substring(0, slash));
				final int count = Integer.parseInt(data.substring(slash + 1, bar));
				if (index == 0) {
					reset();
				}
				if ((index != this.next) || (count <= index) || (count > MAX_VALUE_CHARS)) {
					reset();
					return;
				}
				this.text.append(data, bar + 1, data.length());
				this.next++;
				if (this.next == count) {
					final String complete = this.text.toString();
					reset();
					apply(complete);
				}
			}
			catch (final IOException | RuntimeException e) {
				reset();
				Log.error(e);
			}
		}

		private void reset() {
			this.text.setLength(0);
			this.next = 0;
		}
	}

	private static void apply(final String text) throws IOException {
		final DataInputStream in = new DataInputStream(
				new ByteArrayInputStream(Base64.getDecoder().decode(text.getBytes(StandardCharsets.US_ASCII))));
		final byte type = in.readByte();
		final CGameCache cache = new CGameCache(in.readUTF());
		final String mission = in.readUTF();
		final String key = in.readUTF();
		switch (type) {
		case INTEGER:
			cache.storeInteger(mission, key, in.readInt());
			break;
		case REAL:
			cache.storeReal(mission, key, in.readFloat());
			break;
		case BOOLEAN:
			cache.storeBoolean(mission, key, in.readBoolean());
			break;
		case STRING:
			cache.storeString(mission, key, in.readUTF());
			break;
		case UNIT:
			cache.storeUnit(mission, key, CStoredUnit.read(in));
			break;
		default:
			break;
		}
	}
}
