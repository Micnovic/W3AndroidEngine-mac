package com.etheller.warsmash.android;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import android.util.Log;

/**
 * Bookkeeping for the copies of game sounds and music kept in the cache folder. The Android backend plays real files
 * only, so each is copied out of the game data once; this keeps the folder under a size limit by deleting the least
 * recently used copies, never those used since the process started or in the last few minutes.
 */
final class SoundCache {
	private static final String TAG = "Warsmash";
	private static final long DEFAULT_LIMIT_MB = 256;
	// Sounds that are streamed or music are opened again as they play, so a copy used lately is never deleted.
	private static final long RECENT_MILLIS = 10 * 60 * 1000;
	private static final long STALE_PART_MILLIS = 60 * 60 * 1000;
	private static final long TOUCH_AFTER_MILLIS = 60 * 1000;
	// Trimming again during a session waits until this much was copied in, so a long campaign cannot fill the storage.
	private static final long TRIM_AFTER_COPIED_BYTES = 48L * 1024 * 1024;
	// Changed whenever what a copy holds changes (a copy is not converted the same way), so that copies made before
	// are never used again and are deleted by the next trim.
	private static final String COPY_PREFIX = "v2_";

	private static final Set<String> IN_USE = ConcurrentHashMap.newKeySet();
	private static final AtomicLong COPIED_SINCE_TRIM = new AtomicLong();
	private static final AtomicBoolean TRIMMING = new AtomicBoolean();

	private SoundCache() {
	}

	static long limitBytes() {
		return Math.max(32, Long.getLong("warsmash.soundCacheMb", DEFAULT_LIMIT_MB)) * 1024 * 1024;
	}

	/** The name of the copy of a game file, which holds the file as the current version of the app prepares it. */
	static String copyName(final String gamePath) {
		return COPY_PREFIX + gamePath.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	/** Marks a copy as used by this session, before it is looked for, so that no trimming removes it. */
	static void use(final File copy) {
		IN_USE.add(copy.getName());
	}

	/** Records a use of an existing copy, which is what orders the copies for deleting. */
	static void touch(final File copy) {
		if ((System.currentTimeMillis() - copy.lastModified()) > TOUCH_AFTER_MILLIS) {
			copy.setLastModified(System.currentTimeMillis());
		}
	}

	/** Records a copy made now; after enough of them in one session the folder is trimmed again. */
	static void copied(final File folder, final long bytes) {
		if (COPIED_SINCE_TRIM.addAndGet(bytes) >= TRIM_AFTER_COPIED_BYTES) {
			trimInBackground(folder);
		}
	}

	static void trimInBackground(final File folder) {
		if (!TRIMMING.compareAndSet(false, true)) {
			return;
		}
		COPIED_SINCE_TRIM.set(0);
		final Thread thread = new Thread(() -> {
			try {
				trim(folder, limitBytes());
			}
			catch (final RuntimeException e) {
				Log.w(TAG, "Unable to trim the sound cache", e);
			}
			finally {
				TRIMMING.set(false);
			}
		}, "SoundCacheTrim");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	static void trim(final File folder, final long limit) {
		final File[] files = folder.listFiles();
		if (files == null) {
			return;
		}
		final long now = System.currentTimeMillis();
		final List<File> candidates = new ArrayList<>();
		long total = 0;
		for (final File file : files) {
			if (!file.isFile()) {
				continue;
			}
			if (!file.getName().startsWith(COPY_PREFIX)) {
				file.delete();
				continue;
			}
			if (file.getName().endsWith(".part")) {
				if (!IN_USE.contains(file.getName().substring(0, file.getName().length() - 5))
						&& ((now - file.lastModified()) > STALE_PART_MILLIS)) {
					file.delete();
				}
				continue;
			}
			total += file.length();
			candidates.add(file);
		}
		if (total <= limit) {
			return;
		}
		final long before = total;
		final List<File> byAge = new ArrayList<>(candidates);
		// Snapshot the times once, so a copy touched meanwhile does not break the ordering.
		final java.util.Map<File, Long> times = new java.util.HashMap<>();
		for (final File file : byAge) {
			times.put(file, file.lastModified());
		}
		Collections.sort(byAge, Comparator.comparingLong(times::get));
		int deleted = 0;
		for (final File file : byAge) {
			if (total <= limit) {
				break;
			}
			if (IN_USE.contains(file.getName()) || ((now - file.lastModified()) < RECENT_MILLIS)) {
				continue;
			}
			final long length = file.length();
			if (file.delete()) {
				total -= length;
				deleted++;
			}
		}
		Log.i(TAG, "Sound cache: " + deleted + " copies deleted, " + (before / 1048576) + " MB -> "
				+ (total / 1048576) + " MB (limit " + (limit / 1048576) + " MB)");
	}
}
