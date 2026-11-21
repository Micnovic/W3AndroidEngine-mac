package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The sound a script holds answers what it reads by itself, the same as the voice that plays it does for the same
 * calls.
 */
class SimSoundTest {
	private static final float EPSILON = 1e-4f;

	private static SimSound file(final float duration) {
		return new SimSound(SimSound.Kind.FILE, "a.wav", false, false, false, 0, 0, "", null, null, duration);
	}

	@Test
	void timingMatchesTheVoice() {
		final SimSound sim = file(2f);
		final CSoundFilename voice = new CSoundFilename(null, 2f, null, false, false, 0, 0, "");
		assertFalse(sim.isPlaying(0));
		sim.start(10);
		voice.start(10);
		sim.setDuration(1.5f);
		voice.setDuration(1.5f);
		for (int tick = 10; tick < 60; tick++) {
			assertEquals(voice.isPlaying(tick), sim.isPlaying(tick), "tick " + tick);
			assertEquals(voice.getRemainingTime(tick), sim.getRemainingTime(tick), EPSILON);
		}
		sim.killWhenDone();
		voice.killWhenDone();
		assertEquals(voice.isKilled(40), sim.isKilled(40));
		assertTrue(sim.isKilled(60));
	}

	@Test
	void labelPicksTheFileTheVoiceDoes() {
		final float[] durations = { 1f, 2f, 3f, 4f };
		final SimSound sim = new SimSound(SimSound.Kind.LABEL, "Label", false, false, false, 0, 0, "", null,
				new SoundLabelInfo(durations), 1f);
		int starts = 0;
		for (int tick = 5; tick < 200; tick += 7) {
			sim.start(tick);
			final int variant = CSoundFromLabel.variant(tick, starts++, durations.length);
			assertEquals(durations[variant], sim.getDuration(), EPSILON);
		}
	}

	@Test
	void settingsAreKeptAndAnswered() {
		final SimSound sim = file(1f);
		sim.setDialogueTextKey("TEXT");
		sim.setDialogueSpeakerNameKey("SPEAKER");
		assertEquals("TEXT", sim.getDialogueTextKey());
		assertEquals("SPEAKER", sim.getDialogueSpeakerNameKey());
		assertFalse(sim.isLoading());
	}

	@Test
	void restoredVoiceContinuesTheScriptState() {
		final SimSound sim = file(3f);
		sim.start(20);
		sim.setPlayPosition(500);
		sim.setVolume(60);
		final CSoundFilename voice = new CSoundFilename(null, 3f, null, false, false, 0, 0, "");
		sim.restoreVoice(voice);
		assertSame(voice, sim.getVoice());
		assertEquals(sim.isPlaying(30), voice.isPlaying(30));
		assertEquals(sim.getRemainingTime(30), voice.getRemainingTime(30), EPSILON);
		assertEquals(60, voice.getParameters().volume);
	}
}
