package com.etheller.warsmash.viewer5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.audio.Sound;
import com.etheller.warsmash.viewer5.gl.AudioExtension;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;

/** A loop heard from a place holds a voice of the sound device only while it can be heard. */
class SoundLoopsTest {
	private final List<Long> started = new ArrayList<>();
	private final List<Long> stopped = new ArrayList<>();
	private final List<float[]> seeks = new ArrayList<>();
	private long now;
	private long nextVoice = 100;
	private AudioExtension saved;
	private AudioContext context;
	private AudioContext.Listener.Placed listener;
	private Sound sound;

	@BeforeEach
	void setUp() {
		this.saved = Extensions.audio;
		this.listener = new AudioContext.Listener.Placed();
		this.context = new AudioContext(this.listener, null);
		this.context.world = true;
		PositionalLoop.clock = () -> this.now;
		Extensions.audio = new AudioExtension() {
			@Override
			public float duration(final Sound buffer) {
				return 10;
			}

			@Override
			public void seek(final Sound buffer, final long id, final float seconds) {
				SoundLoopsTest.this.seeks.add(new float[] { id, seconds });
			}

			@Override
			public AudioContext createContext(final boolean world) {
				return null;
			}

			@Override
			public long play(final Sound buffer, final float volume, final float pitch, final float x, final float y,
					final float z, final boolean is3DSound, final float maxDistance, final float refDistance,
					final boolean looping) {
				final long id = SoundLoopsTest.this.nextVoice++;
				SoundLoopsTest.this.started.add(id);
				return id;
			}
		};
		this.sound = (Sound) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Sound.class },
				(proxy, method, args) -> {
					if (method.getName().equals("stop") && (args != null) && (args.length == 1)) {
						this.stopped.add((Long) args[0]);
					}
					return null;
				});
	}

	@AfterEach
	void tearDown() {
		Extensions.audio = this.saved;
		PositionalLoop.clock = System::nanoTime;
	}

	private long startLoopAt(final float x) {
		final AudioPanner panner = this.context.createPanner();
		panner.setPosition(x, 0, 0);
		panner.setDistances(1000, 100);
		final AudioBufferSource source = this.context.createBufferSource();
		source.buffer = this.sound;
		source.connect(panner);
		return source.startLoop(1f, 0, 1f, false);
	}

	@Test
	void outOfRangeLoopHoldsNoVoiceAndGetsOneWhenHeard() {
		final long id = startLoopAt(5000);
		assertTrue(id <= -2);
		assertEquals(0, this.started.size());
		this.listener.setPosition(4500, 0, 0);
		this.context.updateLoops();
		assertEquals(1, this.started.size());
		this.listener.setPosition(0, 0, 0);
		this.context.updateLoops();
		assertEquals(this.started, this.stopped);
	}

	@Test
	void stoppedLoopIsForgotten() {
		final long id = startLoopAt(10);
		assertEquals(1, this.started.size());
		SoundLoops.stop(this.sound, id);
		assertEquals(this.started, this.stopped);
		this.context.updateLoops();
		assertEquals(1, this.started.size());
		// stopping again, or after the camera comes back, does nothing
		SoundLoops.stop(this.sound, id);
		assertEquals(1, this.stopped.size());
	}

	@Test
	void manyLoopsOutOfRangeDoNotEvictEachOther() {
		final List<Long> ids = new ArrayList<>();
		for (int i = 0; i < 300; i++) {
			ids.add(startLoopAt(5000));
		}
		this.listener.setPosition(5000, 0, 0);
		this.context.updateLoops();
		assertEquals(300, this.started.size());
		for (final long id : ids) {
			SoundLoops.stop(this.sound, id);
		}
		assertEquals(300, this.stopped.size());
	}

	@Test
	void onlyAPlacedLoopFollowsItsVoice() {
		startLoopAt(10);
		final long voice = this.started.get(0);
		assertTrue(SoundLoops.isFollowed(this.sound, voice));
		assertFalse(SoundLoops.isFollowed(this.sound, 9999));
		SoundLoops.voiceLost(this.sound, voice);
		assertFalse(SoundLoops.isFollowed(this.sound, voice));
	}

	@Test
	void loopWhoseVoiceWasTakenAsksForAnotherLater() {
		startLoopAt(10);
		assertEquals(1, this.started.size());
		SoundLoops.voiceLost(this.sound, this.started.get(0));
		for (int frame = 0; frame < 40; frame++) {
			this.context.updateLoops();
		}
		assertEquals(2, this.started.size());
		// a voice that is not this loop's own changes nothing
		SoundLoops.voiceLost(this.sound, 9999);
		for (int frame = 0; frame < 40; frame++) {
			this.context.updateLoops();
		}
		assertEquals(2, this.started.size());
	}

	@Test
	void endingAContextStopsItsLoopsAndForgetsThem() {
		final long near = startLoopAt(10);
		final long far = startLoopAt(5000);
		this.context.endLoops();
		assertEquals(this.started, this.stopped);
		this.context.updateLoops();
		assertEquals(1, this.started.size());
		// the ids are no longer known, so stopping them does nothing more
		SoundLoops.stop(this.sound, near);
		SoundLoops.stop(this.sound, far);
		assertEquals(1, this.stopped.size());
	}

	@Test
	void offsetIsTheTimeAloneModuloTheLength() {
		assertEquals(0, PositionalLoop.resumeOffset(0, 1, 10), 1e-6);
		assertEquals(3, PositionalLoop.resumeOffset(3, 1, 10), 1e-6);
		assertEquals(2, PositionalLoop.resumeOffset(32, 1, 10), 1e-4);
		assertEquals(4, PositionalLoop.resumeOffset(2, 2, 10), 1e-6);
		assertEquals(0, PositionalLoop.resumeOffset(5, 1, 0), 1e-6);
	}

	@Test
	void loopHeardAgainCarriesOnInsteadOfRestarting() {
		final long id = startLoopAt(10);
		assertTrue(id <= -2);
		assertEquals(0, this.seeks.size());
		this.listener.setPosition(5000, 0, 0);
		this.context.updateLoops();
		this.now += 23_000_000_000L;
		this.listener.setPosition(0, 0, 0);
		this.context.updateLoops();
		assertEquals(2, this.started.size());
		assertEquals(1, this.seeks.size());
		assertEquals(this.started.get(1), (long) this.seeks.get(0)[0]);
		assertEquals(3, this.seeks.get(0)[1], 1e-3);
	}

	@Test
	void loopWhoseVoiceWasTakenResumesWhereItWouldBe() {
		startLoopAt(10);
		this.now += 4_000_000_000L;
		SoundLoops.voiceLost(this.sound, this.started.get(0));
		for (int frame = 0; frame < 40; frame++) {
			this.now += 20_000_000L;
			this.context.updateLoops();
		}
		assertEquals(2, this.started.size());
		assertEquals(1, this.seeks.size());
		assertTrue(this.seeks.get(0)[1] > 4);
	}

	@Test
	void aLoopIsLiveUntilItIsStopped() {
		final long id = startLoopAt(10);
		assertTrue(SoundLoops.isLive(id));
		assertTrue(SoundLoops.isLive(77));
		SoundLoops.stop(this.sound, id);
		assertFalse(SoundLoops.isLive(id));
	}

	@Test
	void aLoopOfTheDeviceWhoseVoiceWasTakenIsNoLongerFollowed() {
		final CSoundMixer mixer = CSoundMixer.audible();
		mixer.loopStarted(this.sound, 42, 1f, 0);
		SoundLoops.voiceLost(this.sound, 42);
		mixer.stopLoops();
		assertFalse(this.stopped.contains(42L));
	}
}
