package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * The looped sounds started for a caster by an ability or buff, so that stopping the effect of one caster ends only
 * the loops it started and not those of other casters playing the same sound.
 */
final class CasterSoundLoops<K> {
	static final class Loop {
		final String sound;
		final long id;

		Loop(final String sound, final long id) {
			this.sound = sound;
			this.id = id;
		}
	}

	/** Names a caster by identity; maps keep insertion order so that nothing depends on hash codes. */
	private static final class Caster {
		final Object object;

		Caster(final Object object) {
			this.object = object;
		}

		@Override
		public boolean equals(final Object other) {
			return (other instanceof Caster) && (((Caster) other).object == this.object);
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(this.object);
		}
	}

	private final Map<Caster, Map<K, List<Loop>>> byCaster = new LinkedHashMap<>();

	void add(final Object caster, final K key, final String sound, final long id) {
		this.byCaster.computeIfAbsent(new Caster(caster), c -> new LinkedHashMap<>()).computeIfAbsent(key, k -> new ArrayList<>())
				.add(new Loop(sound, id));
	}

	/**
	 * Forgets one loop, as when its own handle ended it.
	 *
	 * @return whether the loop was still known, that is, nobody stopped it before
	 */
	boolean remove(final Object caster, final K key, final long id) {
		final Caster who = new Caster(caster);
		final Map<K, List<Loop>> keys = this.byCaster.get(who);
		if (keys == null) {
			return false;
		}
		final List<Loop> loops = keys.get(key);
		if (loops == null) {
			return false;
		}
		boolean found = false;
		for (final Iterator<Loop> iterator = loops.iterator(); iterator.hasNext();) {
			if (iterator.next().id == id) {
				iterator.remove();
				found = true;
				break;
			}
		}
		if (loops.isEmpty()) {
			keys.remove(key);
			if (keys.isEmpty()) {
				this.byCaster.remove(who);
			}
		}
		return found;
	}

	/** @return the loops this caster started for the key, which are forgotten */
	List<Loop> take(final Object caster, final K key) {
		final Caster who = new Caster(caster);
		final Map<K, List<Loop>> keys = this.byCaster.get(who);
		if (keys == null) {
			return new ArrayList<>();
		}
		final List<Loop> loops = keys.remove(key);
		if (keys.isEmpty()) {
			this.byCaster.remove(who);
		}
		return loops == null ? new ArrayList<>() : loops;
	}

	/** @return every loop this caster started, which are forgotten, as when the caster leaves the game */
	List<Loop> takeAll(final Object caster) {
		final List<Loop> all = new ArrayList<>();
		final Map<K, List<Loop>> keys = this.byCaster.remove(new Caster(caster));
		if (keys != null) {
			for (final List<Loop> loops : keys.values()) {
				all.addAll(loops);
			}
		}
		return all;
	}

	/** @return every loop of every caster, which are forgotten, as when the map ends */
	List<Loop> takeEvery() {
		final List<Loop> all = new ArrayList<>();
		for (final Map<K, List<Loop>> keys : this.byCaster.values()) {
			for (final List<Loop> loops : keys.values()) {
				all.addAll(loops);
			}
		}
		this.byCaster.clear();
		return all;
	}

	/** @return whether some caster still has a loop of this sound */
	boolean hasSound(final String sound) {
		for (final Map<K, List<Loop>> keys : this.byCaster.values()) {
			for (final List<Loop> loops : keys.values()) {
				for (final Loop loop : loops) {
					if (loop.sound.equals(sound)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Forgets the loops that ended some way no handle heard of (the sound was let go, the map's sounds were ended),
	 * which would otherwise keep their caster from being freed for as long as the game runs.
	 *
	 * @return how many were forgotten
	 */
	int prune(final LongPredicate live) {
		int forgotten = 0;
		for (final Iterator<Map<K, List<Loop>>> casters = this.byCaster.values().iterator(); casters.hasNext();) {
			final Map<K, List<Loop>> keys = casters.next();
			for (final Iterator<List<Loop>> lists = keys.values().iterator(); lists.hasNext();) {
				final List<Loop> loops = lists.next();
				for (final Iterator<Loop> iterator = loops.iterator(); iterator.hasNext();) {
					if (!live.test(iterator.next().id)) {
						iterator.remove();
						forgotten++;
					}
				}
				if (loops.isEmpty()) {
					lists.remove();
				}
			}
			if (keys.isEmpty()) {
				casters.remove();
			}
		}
		return forgotten;
	}

	int size() {
		int count = 0;
		for (final Map<K, List<Loop>> keys : this.byCaster.values()) {
			for (final List<Loop> loops : keys.values()) {
				count += loops.size();
			}
		}
		return count;
	}
}
