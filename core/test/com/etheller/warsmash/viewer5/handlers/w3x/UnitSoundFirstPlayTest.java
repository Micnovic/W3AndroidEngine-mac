package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.gl.AudioExtension;
import com.etheller.warsmash.viewer5.gl.Extensions;

/**
 * A sound played for the first time while the backend reads and decodes its file on another thread starts when the
 * sound is given to the game thread: every play asked for meanwhile starts then, none is lost.
 */
class UnitSoundFirstPlayTest {
	private final List<Consumer<Sound>> pending = new ArrayList<>();
	private final List<Sound> played = new ArrayList<>();
	private AudioExtension saved;
	private AudioContext context;
	private Sound sound;
	private boolean later = true;

	@BeforeEach
	void setUp() {
		this.saved = Extensions.audio;
		this.context = new AudioContext(new AudioContext.Listener.Placed(), null);
		this.sound = (Sound) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Sound.class },
				(proxy, method, args) -> null);
		Extensions.audio = new AudioExtension() {
			@Override
			public AudioContext createContext(final boolean world) {
				return null;
			}

			@Override
			public boolean newSoundLater(final FileHandle file, final Consumer<Sound> ready) {
				if (UnitSoundFirstPlayTest.this.later) {
					UnitSoundFirstPlayTest.this.pending.add(ready);
				}
				return UnitSoundFirstPlayTest.this.later;
			}

			@Override
			public Sound newSound(final FileHandle file) {
				return UnitSoundFirstPlayTest.this.sound;
			}

			@Override
			public long play(final Sound buffer, final float volume, final float pitch, final float x, final float y,
					final float z, final boolean is3DSound, final float maxDistance, final float refDistance,
					final boolean looping) {
				UnitSoundFirstPlayTest.this.played.add(buffer);
				return UnitSoundFirstPlayTest.this.played.size();
			}
		};
	}

	@AfterEach
	void tearDown() {
		Extensions.audio = this.saved;
	}

	@SuppressWarnings("unchecked")
	private static UnitSound label(final boolean looping) {
		final UnitSound sound = new UnitSound(null, 1, 1, 0, 0, 0, 0, looping);
		((List<String>) TestObjects.get(sound, "soundPaths")).add("Sound/Test.wav");
		((List<Sound>) TestObjects.get(sound, "sounds")).add(null);
		((List<FileHandle>) TestObjects.get(sound, "handles")).add(new FileHandle("Sound/Test.wav"));
		((List<Float>) TestObjects.get(sound, "durations")).add(0.5f);
		return sound;
	}

	@Test
	void playsAskedForWhileDecodingStartWhenItIsDone() {
		final UnitSound unitSound = label(false);
		assertEquals(-1, unitSound.play(this.context, 0, 0, 0, 0));
		assertEquals(-1, unitSound.play(this.context, 0, 0, 0, 0));
		assertEquals(1, this.pending.size(), "one decoding for the file");
		assertEquals(0, this.played.size());
		this.pending.get(0).accept(this.sound);
		assertEquals(2, this.played.size());
		assertSame(this.played.get(0), this.played.get(1));
		// the sound is kept: the next play starts at once
		assertEquals(3, unitSound.play(this.context, 0, 0, 0, 0));
		assertEquals(1, this.pending.size());
	}

	@Test
	void backendThatDecodesInlineStartsAtOnce() {
		this.later = false;
		final UnitSound unitSound = label(false);
		assertEquals(1, unitSound.play(this.context, 0, 0, 0, 0));
		assertEquals(0, this.pending.size());
	}

	@Test
	void unreadableFileIsAskedForAgainAtTheNextPlay() {
		final UnitSound unitSound = label(false);
		assertEquals(-1, unitSound.play(this.context, 0, 0, 0, 0));
		this.pending.get(0).accept(null);
		assertEquals(0, this.played.size());
		assertEquals(-1, unitSound.play(this.context, 0, 0, 0, 0));
		assertEquals(2, this.pending.size(), "asked again, as a play of a file that could not be read was before");
	}
}
