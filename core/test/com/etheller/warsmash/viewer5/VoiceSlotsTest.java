package com.etheller.warsmash.viewer5;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class VoiceSlotsTest {
	private static final class Voice implements VoiceSlots.Holder {
		private final boolean looping;
		private final boolean dialogue;

		private Voice(final boolean looping, final boolean dialogue) {
			this.looping = looping;
			this.dialogue = dialogue;
		}

		@Override
		public boolean isLooping() {
			return this.looping;
		}

		@Override
		public boolean isDialogue() {
			return this.dialogue;
		}
	}

	private static List<Voice> voices(final int loops, final int dialogues, final int shorts) {
		final List<Voice> list = new ArrayList<>();
		for (int i = 0; i < loops; i++) {
			list.add(new Voice(true, false));
		}
		for (int i = 0; i < dialogues; i++) {
			list.add(new Voice(false, true));
		}
		for (int i = 0; i < shorts; i++) {
			list.add(new Voice(false, false));
		}
		return list;
	}

	@Test
	void freeVoiceIsGranted() {
		final VoiceSlots.Grant<Voice> grant = VoiceSlots.request(voices(2, 1, 1), 8, 2, false, true);
		assertTrue(grant.granted);
		assertNull(grant.victim);
	}

	@Test
	void loopsLeaveVoicesForOneShots() {
		assertTrue(VoiceSlots.request(voices(5, 0, 0), 8, 2, true, false).granted);
		assertFalse(VoiceSlots.request(voices(6, 0, 0), 8, 2, true, false).granted);
		final VoiceSlots.Grant<Voice> dialogue = VoiceSlots.request(voices(6, 0, 0), 8, 2, false, true);
		assertTrue(dialogue.granted);
		assertNull(dialogue.victim);
	}

	@Test
	void fullTakesOldestShortSound() {
		final List<Voice> active = voices(2, 2, 4);
		final VoiceSlots.Grant<Voice> grant = VoiceSlots.request(active, 8, 2, false, false);
		assertTrue(grant.granted);
		assertSame(active.get(4), grant.victim);
	}

	@Test
	void dialogueIsNeverCutOffForAShortSound() {
		assertFalse(VoiceSlots.request(voices(0, 8, 0), 8, 2, false, false).granted);
		assertFalse(VoiceSlots.request(voices(6, 2, 0), 8, 2, false, false).granted);
	}

	@Test
	void dialogueTakesOverALoopWhenNothingElseGives() {
		final List<Voice> active = voices(6, 2, 0);
		final VoiceSlots.Grant<Voice> grant = VoiceSlots.request(active, 8, 2, false, true);
		assertTrue(grant.granted);
		assertSame(active.get(0), grant.victim);
		assertFalse(VoiceSlots.request(voices(0, 8, 0), 8, 2, false, true).granted);
	}
}
