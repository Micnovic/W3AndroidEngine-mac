package com.etheller.warsmash.viewer5;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.audio.Sound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;

/**
 * Stops looping sounds. A loop heard from a place has a sound device voice only while the camera is near enough to
 * hear it, but its caller keeps one id for its whole life: ids below -1 name such loops here, any other id is the
 * device's own. Callers stop a loop through here so that it is forgotten wherever it is followed.
 */
public final class SoundLoops {
	private static final long FIRST_ID = -2;
	private static final Map<Long, PositionalLoop> PLACED = new HashMap<>();
	private static long nextId = FIRST_ID;

	private SoundLoops() {
	}

	static boolean isLoopId(final long id) {
		return id <= FIRST_ID;
	}

	static synchronized long register(final PositionalLoop loop) {
		final long id = nextId--;
		PLACED.put(id, loop);
		return id;
	}

	static synchronized void forget(final long id) {
		PLACED.remove(id);
	}

	/**
	 * @return whether a loop id may still sound: a loop heard from a place is live until it is stopped, and the
	 *         device's own ids are not followed here, so they count as live
	 */
	public static boolean isLive(final long id) {
		if (!isLoopId(id)) {
			return true;
		}
		synchronized (SoundLoops.class) {
			return PLACED.containsKey(id);
		}
	}

	/** Ends the loop an id from a play call names. */
	public static void stop(final Sound sound, final long id) {
		if (isLoopId(id)) {
			final PositionalLoop loop;
			synchronized (SoundLoops.class) {
				loop = PLACED.get(id);
			}
			if (loop != null) {
				loop.stop();
			}
			return;
		}
		sound.stop(id);
		CSoundMixer.audible().loopStopped(sound, id);
	}

	/**
	 * @return whether a loop heard from a place follows this voice of the device and asks for another once it is lost;
	 *         a voice no such loop follows is the caller's own to carry on with
	 */
	public static boolean isFollowed(final Sound sound, final long id) {
		synchronized (SoundLoops.class) {
			for (final PositionalLoop loop : PLACED.values()) {
				if (loop.isOf(sound) && loop.holds(id)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Tells that the device took a voice away from a sound, as for a dialogue. A loop heard from a place that held it
	 * gives it up and asks for another again later, so it sounds again once a voice is free.
	 */
	public static void voiceLost(final Sound sound, final long id) {
		// A loop of the device's own is over for good once its voice is taken, so it is no longer followed.
		CSoundMixer.audible().loopStopped(sound, id);
		final List<PositionalLoop> holders = new ArrayList<>();
		synchronized (SoundLoops.class) {
			for (final PositionalLoop loop : PLACED.values()) {
				if (loop.isOf(sound)) {
					holders.add(loop);
				}
			}
		}
		for (final PositionalLoop loop : holders) {
			loop.voiceLost(id);
		}
	}

	/** Ends every voice and loop of the sound. */
	public static void stopAll(final Sound sound) {
		final List<PositionalLoop> ended = new ArrayList<>();
		synchronized (SoundLoops.class) {
			for (final PositionalLoop loop : PLACED.values()) {
				if (loop.isOf(sound)) {
					ended.add(loop);
				}
			}
		}
		for (final PositionalLoop loop : ended) {
			loop.stop();
		}
		sound.stop();
		CSoundMixer.audible().loopsStopped(sound);
	}
}
