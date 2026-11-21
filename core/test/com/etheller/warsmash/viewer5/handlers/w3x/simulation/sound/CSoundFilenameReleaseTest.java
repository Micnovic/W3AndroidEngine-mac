package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.audio.Sound;

/** A killed sound handle gives its sound back once, when it cannot play any more. */
class CSoundFilenameReleaseTest {
	private final List<CSoundFilename> watched = new ArrayList<>();
	private final List<Sound> released = new ArrayList<>();
	private final Sound sound = (Sound) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] { Sound.class }, (proxy, method, args) -> null);

	private CSoundFilename handle() {
		final CSoundFilename handle = new CSoundFilename(this.sound, 2f, null, false, false, 0, 0, "");
		handle.setOwner(new CSoundFilename.Owner() {
			@Override
			public void watchKilled(final CSoundFilename killed) {
				CSoundFilenameReleaseTest.this.watched.add(killed);
			}

			@Override
			public void releaseSound(final Sound given) {
				CSoundFilenameReleaseTest.this.released.add(given);
			}
		});
		return handle;
	}

	@Test
	void unkilledHandleKeepsItsSound() {
		final CSoundFilename handle = handle();
		assertFalse(handle.releaseIfDone(0));
		assertEquals(0, this.released.size());
		assertEquals(0, this.watched.size());
	}

	@Test
	void killedHandleReleasesAfterItPlayedOut() {
		final CSoundFilename handle = handle();
		handle.start(0);
		handle.killWhenDone();
		assertEquals(1, this.watched.size());
		assertFalse(handle.releaseIfDone(1));
		assertTrue(handle.releaseIfDone(1000));
		assertEquals(1, this.released.size());
		assertTrue(handle.releaseIfDone(1001));
		assertEquals(1, this.released.size());
		// a killed handle cannot start again
		handle.start(2000);
		assertFalse(handle.isPlaying(2000));
	}

	@Test
	void handleKilledBeforeItsSoundCameGivesItBackWhenItComes() {
		final CSoundFilename handle = new CSoundFilename(null, 2f, null, false, false, 0, 0, "");
		handle.setOwner(new CSoundFilename.Owner() {
			@Override
			public void watchKilled(final CSoundFilename killed) {
				CSoundFilenameReleaseTest.this.watched.add(killed);
			}

			@Override
			public void releaseSound(final Sound given) {
				CSoundFilenameReleaseTest.this.released.add(given);
			}
		});
		handle.awaitSound();
		handle.start(0);
		handle.killWhenDone();
		assertFalse(handle.releaseIfDone(1));
		assertTrue(handle.releaseIfDone(1000));
		assertEquals(0, this.released.size());
		handle.bindSound(this.sound, 1001);
		assertEquals(1, this.released.size());
		assertTrue(handle.releaseIfDone(1002));
		assertEquals(1, this.released.size());
	}

	@Test
	void handleThatWaitedForItsSoundHoldsAndReleasesItAsUsual() {
		final CSoundFilename handle = new CSoundFilename(null, 2f, null, false, false, 0, 0, "");
		handle.setOwner(new CSoundFilename.Owner() {
			@Override
			public void watchKilled(final CSoundFilename killed) {
				CSoundFilenameReleaseTest.this.watched.add(killed);
			}

			@Override
			public void releaseSound(final Sound given) {
				CSoundFilenameReleaseTest.this.released.add(given);
			}
		});
		handle.awaitSound();
		handle.bindSound(this.sound, 0);
		handle.start(0);
		handle.killWhenDone();
		assertTrue(handle.releaseIfDone(1000));
		assertEquals(1, this.released.size());
	}

	@Test
	void killedStoppedHandleReleasesAtOnce() {
		final CSoundFilename handle = handle();
		handle.start(0);
		handle.killWhenDone();
		handle.stop();
		assertTrue(handle.releaseIfDone(1));
		assertEquals(1, this.released.size());
	}
}
