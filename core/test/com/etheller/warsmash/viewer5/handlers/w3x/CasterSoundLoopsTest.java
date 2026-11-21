package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CasterSoundLoopsTest {
	@Test
	void stoppingOneCasterKeepsTheLoopsOfAnother() {
		final CasterSoundLoops<String> loops = new CasterSoundLoops<>();
		final Object first = new Object();
		final Object second = new Object();
		loops.add(first, "AHbz", "Loop", -2);
		loops.add(second, "AHbz", "Loop", -3);
		final List<CasterSoundLoops.Loop> taken = loops.take(first, "AHbz");
		assertEquals(1, taken.size());
		assertEquals(-2, taken.get(0).id);
		assertEquals(1, loops.size());
		assertEquals(-3, loops.take(second, "AHbz").get(0).id);
		assertEquals(0, loops.size());
	}

	@Test
	void aCasterWithTheSameSoundTwiceStopsBoth() {
		final CasterSoundLoops<String> loops = new CasterSoundLoops<>();
		final Object caster = new Object();
		loops.add(caster, "A", "Loop", 5);
		loops.add(caster, "A", "Loop", 6);
		loops.add(caster, "B", "Other", 7);
		assertEquals(2, loops.take(caster, "A").size());
		assertEquals(1, loops.size());
	}

	@Test
	void endedLoopIsForgotten() {
		final CasterSoundLoops<String> loops = new CasterSoundLoops<>();
		final Object caster = new Object();
		loops.add(caster, "A", "Loop", 5);
		loops.remove(caster, "A", 5);
		assertEquals(0, loops.size());
		assertTrue(loops.take(caster, "A").isEmpty());
	}

	@Test
	void removingACasterTakesAllItsLoops() {
		final CasterSoundLoops<String> loops = new CasterSoundLoops<>();
		final Object caster = new Object();
		final Object other = new Object();
		loops.add(caster, "A", "Loop", 5);
		loops.add(caster, "B", "Other", 7);
		loops.add(other, "A", "Loop", 8);
		assertEquals(2, loops.takeAll(caster).size());
		assertEquals(1, loops.size());
		assertTrue(loops.takeAll(caster).isEmpty());
	}

	@Test
	void mapEndTakesEveryLoopInStartOrder() {
		final CasterSoundLoops<String> loops = new CasterSoundLoops<>();
		for (int i = 0; i < 20; i++) {
			loops.add(new Object(), "A", "Loop", i);
		}
		final List<CasterSoundLoops.Loop> all = loops.takeEvery();
		assertEquals(20, all.size());
		for (int i = 0; i < 20; i++) {
			assertEquals(i, all.get(i).id);
		}
		assertEquals(0, loops.size());
	}

	@Test
	void stoppedLoopIsNotReportedTwiceAndSoundsAreFound() {
		final CasterSoundLoops<String> loops = new CasterSoundLoops<>();
		final Object caster = new Object();
		loops.add(caster, "A", "Loop", 5);
		assertTrue(loops.hasSound("Loop"));
		loops.take(caster, "A");
		assertTrue(!loops.hasSound("Loop"));
		assertTrue(!loops.remove(caster, "A", 5));
	}

	@Test
	void prunedLoopsOfEndedSoundsReleaseTheirCaster() {
		final CasterSoundLoops<String> loops = new CasterSoundLoops<>();
		final Object first = new Object();
		final Object second = new Object();
		loops.add(first, "A", "Loop", -2);
		loops.add(first, "B", "Loop", -3);
		loops.add(second, "A", "Loop", -4);
		assertEquals(2, loops.prune(id -> id == -4));
		assertEquals(1, loops.size());
		assertTrue(loops.take(first, "A").isEmpty());
		assertEquals(1, loops.takeAll(second).size());
		assertEquals(0, loops.prune(id -> false));
	}
}
