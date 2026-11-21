package com.etheller.warsmash.parsers.fdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.parsers.fdf.datamodel.FrameClass;
import com.etheller.warsmash.parsers.fdf.datamodel.FrameDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FrameTemplateEnvironment;

/** A screen that is not showing lets go of its frame templates; they are made again from the files when asked for. */
class FrameTemplateEnvironmentTest {
	@Test
	void releasedTemplatesComeBackOnTheNextLookup() {
		final FrameTemplateEnvironment templates = new FrameTemplateEnvironment();
		final int[] loads = { 0 };
		final Runnable load = () -> {
			loads[0]++;
			templates.put("Frame", new FrameDefinition(FrameClass.Frame, "FRAME", "Frame"));
		};
		load.run();
		templates.addDecoratedString("KEY", "text");
		final FrameDefinition first = templates.getFrame("Frame");

		templates.release(load);
		assertEquals(1, loads[0]);
		assertEquals("text", templates.getDecoratedString("KEY"));
		assertEquals(1, loads[0]);

		final FrameDefinition second = templates.getFrame("Frame");
		assertEquals(2, loads[0]);
		assertNotNull(second);
		assertNotSame(first, second);
		assertSame(second, templates.getFrame("Frame"));
		assertNull(templates.getFrame("Missing"));
		assertEquals(2, loads[0]);
	}

	@Test
	void restoreMakesThemOnceAndAReleaseOfNothingIsIgnored() {
		final FrameTemplateEnvironment templates = new FrameTemplateEnvironment();
		final int[] loads = { 0 };
		templates.release(() -> loads[0]++);
		templates.restore();
		assertEquals(0, loads[0]);

		templates.put("Frame", new FrameDefinition(FrameClass.Frame, "FRAME", "Frame"));
		templates.release(() -> {
			loads[0]++;
			templates.put("Frame", new FrameDefinition(FrameClass.Frame, "FRAME", "Frame"));
		});
		templates.restore();
		templates.restore();
		assertEquals(1, loads[0]);
		assertNotNull(templates.getFrame("Frame"));
	}
}
