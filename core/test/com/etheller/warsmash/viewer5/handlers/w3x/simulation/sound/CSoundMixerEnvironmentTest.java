package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CSoundMixerEnvironmentTest {
	@Test
	void environmentAndCinematicSwitchSurviveVolumeGroupReset() {
		final CSoundMixer mixer = new CSoundMixer();
		assertEquals("", mixer.getEnvironment());
		mixer.setEnvironment("DefaultEAXON");
		mixer.setCinematicAudio(true);
		mixer.reset();
		assertEquals("DefaultEAXON", mixer.getEnvironment());
		assertTrue(mixer.isCinematicAudio());
		mixer.setEnvironment(null);
		mixer.setCinematicAudio(false);
		assertEquals("", mixer.getEnvironment());
		assertFalse(mixer.isCinematicAudio());
	}
}
