package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import java.util.Locale;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.launcher.StorageRoot;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.GameOptions;

/**
 * Where a player's saved games, game caches and campaign progress live: Save/&lt;profile&gt; in the application's own
 * storage (Gdx.files.local), which is the working folder on desktop and the private files folder on Android, or
 * the folder the platform names in {@link StorageRoot} (Android keeps the profiles in the shared storage, where an
 * uninstall leaves them).
 */
public final class CSaveStorage {
	public static final String SAVE_EXTENSION = ".w3z";
	private static final String CURRENT_PROFILE_FALLBACK = "Player";
	private static String profile = CURRENT_PROFILE_FALLBACK;
	private static final ThreadPoolExecutor BACKGROUND_WRITER = new ThreadPoolExecutor(1, 1, 5, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(), runnable -> new Thread(runnable, "SaveWriter"));
	private static Future<?> lastBackgroundWrite;

	static {
		BACKGROUND_WRITER.allowCoreThreadTimeOut(true);
	}

	private CSaveStorage() {
	}

	public static void setProfile(final String profileName) {
		final String safe = safeName(profileName);
		final String next = safe.isEmpty() ? CURRENT_PROFILE_FALLBACK : safe;
		if (!next.equals(profile)) {
			profile = next;
			CGameCache.forgetLoaded();
			CCampaignProgress.forgetLoaded();
			GameOptions.reload();
		}
	}

	public static FileHandle profileFile(final String name) {
		return StorageRoot.local("Save/" + profile + "/" + name);
	}

	public static FileHandle savedGame(final String name) {
		finishBackgroundWrites();
		return profileFile(fileNameOf(name));
	}

	/**
	 * A script's name for a save can have folders and its own extension. The folders are kept, as in the original:
	 * zone maps keep a save per zone in Blizzard\Zones, copy them to a folder named after the player's save and
	 * remove the folder when the chapter starts again; the saved game list shows only the profile's own folder.
	 */
	private static String fileNameOf(final String name) {
		String path = folderOf(name);
		if (path.toLowerCase(Locale.ROOT).endsWith(SAVE_EXTENSION)) {
			path = path.substring(0, path.length() - SAVE_EXTENSION.length());
		}
		return path + SAVE_EXTENSION;
	}

	/** Each part made safe; empty, "." and ".." parts are dropped, so a name never leaves the profile folder. */
	private static String folderOf(final String name) {
		final StringBuilder path = new StringBuilder();
		for (final String part : name.replace('\\', '/').split("/")) {
			final String safe = safeName(part);
			if (!safe.isEmpty()) {
				path.append(path.length() == 0 ? "" : "/").append(safe);
			}
		}
		return path.toString();
	}

	/** A folder of saves named by a script, or null for an empty name (the profile folder itself). */
	public static FileHandle saveFolder(final String name) {
		finishBackgroundWrites();
		final String path = folderOf(name);
		return path.isEmpty() ? null : profileFile(path);
	}

	/** Names as typed by a player must stay inside the save folder and be legal on every file system. */
	public static String safeName(final String name) {
		return name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_").replaceAll("^\\.+", "").trim();
	}

	public static List<String> savedGameNames() {
		finishBackgroundWrites();
		final List<String> names = new ArrayList<>();
		final FileHandle folder = profileFile("");
		if (folder.isDirectory()) {
			for (final FileHandle file : folder.list(SAVE_EXTENSION)) {
				names.add(file.nameWithoutExtension());
			}
		}
		Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public interface Writer {
		void write(DataOutputStream out) throws IOException;
	}

	public interface Reader {
		void read(DataInputStream in) throws IOException;
	}

	/** Fills in what the writer could not know when it passed it: lengths of parts that were streamed. */
	public interface Patcher {
		void patch(RandomAccessFile file) throws IOException;
	}

	/** Written beside the target first: a crash or a full disk must not take the previous file with it. */
	public static boolean writeAtomically(final FileHandle file, final Writer writer) {
		return writeAtomically(file, writer, null);
	}

	public static boolean writeAtomically(final FileHandle file, final Writer writer, final Patcher patcher) {
		final FileHandle temporary = file.sibling(file.name() + ".tmp");
		try {
			file.parent().mkdirs();
			// The bytes must be on the disk before the rename, or a power cut leaves an empty file under the real name.
			try (FileOutputStream stream = new FileOutputStream(temporary.file())) {
				final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(stream));
				writer.write(out);
				out.flush();
				stream.getFD().sync();
			}
			if (patcher != null) {
				try (RandomAccessFile patched = new RandomAccessFile(temporary.file(), "rw")) {
					patcher.patch(patched);
					patched.getFD().sync();
				}
			}
			replaceFile(temporary.file(), file.file());
			return true;
		}
		catch (final Exception exc) {
			Log.error(exc);
			if (temporary.exists()) {
				temporary.delete();
			}
			return false;
		}
	}

	/** The target is either the old file or the new one, never a half-copied mix, where the file system can do that. */
	static void replaceFile(final File from, final File to) throws IOException {
		try {
			Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		}
		catch (final AtomicMoveNotSupportedException exc) {
			Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Campaign scripts save checkpoints in the middle of play, and compressing and storing a save held the frame for
	 * as long as it took; that part runs beside the game. Writes still happen one after another, in the order asked.
	 */
	public static synchronized void writeInBackground(final FileHandle file, final Writer writer) {
		writeInBackground(file, writer, null);
	}

	public static synchronized void writeInBackground(final FileHandle file, final Writer writer,
			final Patcher patcher) {
		lastBackgroundWrite = BACKGROUND_WRITER.submit(() -> {
			writeAtomically(file, writer, patcher);
		});
	}

	/** Anything that reads or lists saved games first waits for the ones still being written. */
	public static void finishBackgroundWrites() {
		final Future<?> pending;
		synchronized (CSaveStorage.class) {
			pending = lastBackgroundWrite;
		}
		if ((pending == null) || pending.isDone()) {
			return;
		}
		try {
			pending.get();
		}
		catch (final InterruptedException exc) {
			Thread.currentThread().interrupt();
		}
		catch (final ExecutionException exc) {
			Log.error(exc);
		}
	}

	public static boolean read(final FileHandle file, final Reader reader) {
		if (!file.exists()) {
			return false;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(file.read()))) {
			reader.read(in);
			return true;
		}
		catch (final Exception exc) {
			Log.error(exc);
			return false;
		}
	}
}
