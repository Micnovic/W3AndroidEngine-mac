package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The sounds a script makes from files, one for each file however many handles the script makes of it. A sound is
 * let go once no handle holds it and its last play has run out, so a script that makes a sound for every line it
 * speaks does not keep every decoded copy until the map ends.
 *
 * @param <S> the sound type
 */
final class SharedFileSounds<S> {
	/** How long a sound nobody holds is kept, past the length of its file, for a handle made again at once. */
	static final long LINGER_MILLIS = 3000;

	private static final class Entry<S> {
		private final String key;
		private final String fileName;
		private S sound;
		/** Who is told when a sound being made in the background is done, null when none is being made. */
		private List<Consumer<S>> waiting;
		private final long playMillis;
		private int holders;
		private boolean pinned;
		private long idleSince;

		private Entry(final String key, final String fileName, final S sound, final long playMillis) {
			this.key = key;
			this.fileName = fileName;
			this.sound = sound;
			this.playMillis = playMillis;
		}
	}

	private final Function<String, S> loader;
	private final BiPredicate<String, Consumer<S>> laterLoader;
	private final Consumer<S> disposer;
	private final Map<String, Entry<S>> byKey = new HashMap<>();
	private final Map<S, Entry<S>> bySound = new IdentityHashMap<>();
	private final Set<Entry<S>> idle = new LinkedHashSet<>();

	SharedFileSounds(final Function<String, S> loader, final Consumer<S> disposer) {
		this(loader, (fileName, done) -> false, disposer);
	}

	/**
	 * @param laterLoader starts making the sound of a file in the background and calls back with it (null when it
	 *                    could not be made); false when that cannot be done and the sound is to be made by the loader
	 */
	SharedFileSounds(final Function<String, S> loader, final BiPredicate<String, Consumer<S>> laterLoader,
			final Consumer<S> disposer) {
		this.loader = loader;
		this.laterLoader = laterLoader;
		this.disposer = disposer;
	}

	/**
	 * As {@link #acquire}, for a caller that can wait for its sound: a sound not made yet is made in the background,
	 * and the caller, which holds it from now, is given it when it is, at once when it is already.
	 */
	void acquireLater(final String fileName, final float fileSeconds, final Consumer<S> ready) {
		final String key = fileName.toLowerCase(Locale.ROOT);
		Entry<S> entry = this.byKey.get(key);
		if (entry == null) {
			final Entry<S> loading = new Entry<>(key, fileName, null, Math.round(Math.max(0, fileSeconds) * 1000f));
			loading.waiting = new ArrayList<>();
			this.byKey.put(key, loading);
			entry = loading;
			final boolean started = this.laterLoader.test(fileName, sound -> finishLater(loading, sound));
			if (!started) {
				loading.waiting = null;
				loading.sound = this.loader.apply(fileName);
				if (loading.sound != null) {
					this.bySound.put(loading.sound, loading);
				}
			}
		}
		if (entry.waiting != null) {
			entry.holders++;
			this.idle.remove(entry);
			entry.waiting.add(ready);
			return;
		}
		if (entry.sound != null) {
			entry.holders++;
			this.idle.remove(entry);
		}
		ready.accept(entry.sound);
	}

	private void finishLater(final Entry<S> entry, final S sound) {
		if (entry.waiting == null) {
			// a sound was needed at once meanwhile and made by the loader
			if ((sound != null) && (sound != entry.sound)) {
				this.disposer.accept(sound);
			}
			return;
		}
		final List<Consumer<S>> waiting = entry.waiting;
		entry.waiting = null;
		entry.sound = sound;
		if (sound != null) {
			this.bySound.put(sound, entry);
		}
		for (final Consumer<S> consumer : waiting) {
			consumer.accept(sound);
		}
	}

	/**
	 * The sound of a file, loaded when none is held; the caller holds it until it passes it to {@link #release}.
	 *
	 * @param fileSeconds the length of the file, which a voice started from the sound can last
	 */
	S acquire(final String fileName, final float fileSeconds) {
		final Entry<S> entry = entry(fileName, fileSeconds);
		if (entry.sound == null) {
			return null;
		}
		entry.holders++;
		this.idle.remove(entry);
		return entry.sound;
	}

	/** The sound of a file that is never let go, for state that is loaded back without knowing its holders. */
	S pin(final String fileName, final float fileSeconds) {
		final Entry<S> entry = entry(fileName, fileSeconds);
		entry.pinned = true;
		this.idle.remove(entry);
		return entry.sound;
	}

	private Entry<S> entry(final String fileName, final float fileSeconds) {
		final String key = fileName.toLowerCase(Locale.ROOT);
		Entry<S> entry = this.byKey.get(key);
		if (entry == null) {
			final S sound = this.loader.apply(fileName);
			entry = new Entry<>(key, fileName, sound, Math.round(Math.max(0, fileSeconds) * 1000f));
			this.byKey.put(key, entry);
			if (sound != null) {
				this.bySound.put(sound, entry);
			}
		}
		else if (entry.waiting != null) {
			// asked for at once while it is made in the background: those waiting get this one
			final S sound = this.loader.apply(fileName);
			final List<Consumer<S>> waiting = entry.waiting;
			entry.waiting = null;
			entry.sound = sound;
			if (sound != null) {
				this.bySound.put(sound, entry);
			}
			for (final Consumer<S> consumer : waiting) {
				consumer.accept(sound);
			}
		}
		return entry;
	}

	/** Gives back what {@link #acquire} gave. */
	void release(final S sound, final long nowMillis) {
		final Entry<S> entry = sound == null ? null : this.bySound.get(sound);
		if ((entry == null) || (entry.holders <= 0)) {
			return;
		}
		entry.holders--;
		if ((entry.holders == 0) && !entry.pinned) {
			entry.idleSince = nowMillis;
			this.idle.add(entry);
		}
	}

	/** Lets go of the sounds that nobody holds and that have played out. */
	void sweep(final long nowMillis) {
		if (this.idle.isEmpty()) {
			return;
		}
		for (final Iterator<Entry<S>> it = this.idle.iterator(); it.hasNext();) {
			final Entry<S> entry = it.next();
			if ((nowMillis - entry.idleSince) >= (entry.playMillis + LINGER_MILLIS)) {
				it.remove();
				this.byKey.remove(entry.key);
				this.bySound.remove(entry.sound);
				this.disposer.accept(entry.sound);
			}
		}
	}

	/** The file a sound of this cache was made from, as the script wrote it. */
	String fileNameOf(final S sound) {
		final Entry<S> entry = sound == null ? null : this.bySound.get(sound);
		return entry == null ? null : entry.fileName;
	}

	boolean holds(final S sound) {
		return this.bySound.containsKey(sound);
	}

	int size() {
		return this.bySound.size();
	}

	/** Forgets every sound without letting go of it, when something else lets go of all of them. */
	void clear() {
		this.byKey.clear();
		this.bySound.clear();
		this.idle.clear();
	}
}
