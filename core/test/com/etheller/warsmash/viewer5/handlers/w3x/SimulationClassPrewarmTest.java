package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SimulationClassPrewarmTest {
	@Test
	void everyNamedClassExists() throws ClassNotFoundException {
		for (final String name : SimulationClassPrewarm.names()) {
			assertNotNull(Class.forName(SimulationClassPrewarm.qualified(name), false,
					SimulationClassPrewarm.class.getClassLoader()), name);
		}
	}

	@Test
	void noClassIsNamedTwice() {
		final Set<String> names = new HashSet<>(Arrays.asList(SimulationClassPrewarm.names()));
		assertEquals(SimulationClassPrewarm.names().length, names.size());
	}
}
