package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineSettingsTempNameTest {
	@TempDir
	File temp;

	@Test
	void everyWriteGetsItsOwnTemporaryFileBesideTheTarget() {
		final File target = new File(this.temp, "warsmash.ini");
		final File first = EngineSettings.partialOf(target);
		final File second = EngineSettings.partialOf(target);
		assertNotEquals(first, second);
		assertEquals(target.getAbsoluteFile().getParentFile(), first.getParentFile());
	}

	@Test
	void settingsSurviveRepeatedWrites() {
		final File ini = new File(this.temp, "warsmash.ini");
		final EngineSettings settings = new EngineSettings(new File(this.temp, "Save"), "p", ini);
		settings.setTouch("A", "1");
		settings.setTouch("A", "2");
		settings.setNumber("x", 3);
		assertEquals("2", settings.touch("A", ""));
		assertEquals(3, new EngineSettings(new File(this.temp, "Save"), "p", ini).number("x", 0));
		assertEquals(0, this.temp.list((d, n) -> n.endsWith(".part")).length);
	}
}
