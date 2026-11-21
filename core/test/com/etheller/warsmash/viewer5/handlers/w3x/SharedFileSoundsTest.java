package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

/** A sound made from a file is shared by every handle of it and let go when the last handle is done with it. */
class SharedFileSoundsTest {
	private final List<Object> loaded = new ArrayList<>();
	private final List<Object> disposed = new ArrayList<>();
	private final SharedFileSounds<Object> sounds = new SharedFileSounds<>(name -> {
		if (name.startsWith("missing")) {
			return null;
		}
		final Object sound = new Object();
		this.loaded.add(sound);
		return sound;
	}, this.disposed::add);

	@Test
	void handlesOfOneFileShareOneSoundAnyCase() {
		final Object first = this.sounds.acquire("Sound/A.wav", 1);
		final Object second = this.sounds.acquire("sound/a.WAV", 1);
		assertSame(first, second);
		assertEquals(1, this.loaded.size());
	}

	@Test
	void soundIsFreedOnlyAfterTheLastHolderAndItsPlayTime() {
		final Object sound = this.sounds.acquire("a.wav", 2);
		this.sounds.acquire("a.wav", 2);
		this.sounds.release(sound, 0);
		this.sounds.sweep(1_000_000);
		assertEquals(0, this.disposed.size());
		this.sounds.release(sound, 10_000);
		this.sounds.sweep(10_000 + 2000 + SharedFileSounds.LINGER_MILLIS - 1);
		assertEquals(0, this.disposed.size());
		this.sounds.sweep(10_000 + 2000 + SharedFileSounds.LINGER_MILLIS);
		assertEquals(1, this.disposed.size());
		assertSame(sound, this.disposed.get(0));
		assertEquals(0, this.sounds.size());
		// released twice does nothing
		this.sounds.release(sound, 20_000);
		this.sounds.sweep(1_000_000);
		assertEquals(1, this.disposed.size());
	}

	@Test
	void soundAcquiredAgainWhileLingeringIsKept() {
		final Object sound = this.sounds.acquire("a.wav", 0);
		this.sounds.release(sound, 0);
		assertSame(sound, this.sounds.acquire("a.wav", 0));
		this.sounds.sweep(1_000_000);
		assertEquals(0, this.disposed.size());
		this.sounds.release(sound, 1_000_000);
		this.sounds.sweep(1_000_000 + SharedFileSounds.LINGER_MILLIS);
		assertEquals(1, this.disposed.size());
	}

	@Test
	void freedSoundIsLoadedAgainOnTheNextRequest() {
		final Object sound = this.sounds.acquire("a.wav", 0);
		this.sounds.release(sound, 0);
		this.sounds.sweep(SharedFileSounds.LINGER_MILLIS);
		assertNotSame(sound, this.sounds.acquire("a.wav", 0));
		assertEquals(2, this.loaded.size());
	}

	@Test
	void pinnedSoundIsNeverFreed() {
		final Object sound = this.sounds.pin("a.wav", 0);
		assertSame(sound, this.sounds.acquire("a.wav", 0));
		this.sounds.release(sound, 0);
		this.sounds.sweep(1_000_000_000);
		assertEquals(0, this.disposed.size());
		assertEquals("a.wav", this.sounds.fileNameOf(sound));
	}

	@Test
	void missingFileGivesNoSoundAndNothingToRelease() {
		assertNull(this.sounds.acquire("missing.wav", 1));
		this.sounds.release(null, 0);
		this.sounds.sweep(1_000_000);
		assertEquals(0, this.disposed.size());
	}

	@Test
	void soundMadeInTheBackgroundGoesToEveryWaitingHandleOnce() {
		final List<Consumer<Object>> started = new ArrayList<>();
		final SharedFileSounds<Object> later = new SharedFileSounds<>(name -> new Object(), (name, done) -> {
			started.add(done);
			return true;
		}, this.disposed::add);
		final List<Object> first = new ArrayList<>();
		final List<Object> second = new ArrayList<>();
		later.acquireLater("a.wav", 1, first::add);
		later.acquireLater("A.wav", 1, second::add);
		assertEquals(1, started.size());
		assertEquals(0, first.size());
		final Object sound = new Object();
		started.get(0).accept(sound);
		assertEquals(1, first.size());
		assertSame(sound, first.get(0));
		assertSame(sound, second.get(0));
		// both hold it: it stays until both have given it back
		later.release(sound, 0);
		later.sweep(1_000_000);
		assertEquals(0, this.disposed.size());
		later.release(sound, 0);
		later.sweep(1_000_000);
		assertEquals(1, this.disposed.size());
	}

	@Test
	void soundAskedForAtOnceWhileBeingMadeServesTheWaitingToo() {
		final List<Consumer<Object>> started = new ArrayList<>();
		final SharedFileSounds<Object> later = new SharedFileSounds<>(name -> new Object(), (name, done) -> {
			started.add(done);
			return true;
		}, this.disposed::add);
		final List<Object> waiting = new ArrayList<>();
		later.acquireLater("a.wav", 1, waiting::add);
		final Object now = later.acquire("a.wav", 1);
		assertEquals(1, waiting.size());
		assertSame(now, waiting.get(0));
		// the background result comes too late and is let go
		final Object late = new Object();
		started.get(0).accept(late);
		assertEquals(1, this.disposed.size());
		assertSame(late, this.disposed.get(0));
	}

	@Test
	void backgroundLoadThatCannotStartLoadsAtOnce() {
		final SharedFileSounds<Object> later = new SharedFileSounds<>(name -> new Object(), (name, done) -> false,
				this.disposed::add);
		final List<Object> got = new ArrayList<>();
		later.acquireLater("a.wav", 1, got::add);
		assertEquals(1, got.size());
		assertSame(got.get(0), later.acquire("a.wav", 1));
	}

	@Test
	void manyKilledHandlesOfDistinctFilesDoNotAccumulate() {
		for (int i = 0; i < 500; i++) {
			final Object sound = this.sounds.acquire("line" + i + ".wav", 1);
			this.sounds.release(sound, i * 100L);
			this.sounds.sweep(i * 100L);
		}
		assertEquals(500 - this.sounds.size(), this.disposed.size());
		// 1 s of play plus the linger covers at most 41 100 ms steps
		assertTrue(this.sounds.size() <= 41);
	}
}
