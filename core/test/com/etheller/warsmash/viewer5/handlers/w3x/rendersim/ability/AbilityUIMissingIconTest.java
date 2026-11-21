package com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;

import org.junit.jupiter.api.Test;

class AbilityUIMissingIconTest {
	@Test
	void abilityWithoutIconRowsGivesNullIconsNotAnException() {
		final AbilityUI ui = new AbilityUI(null, Collections.emptyList(), Collections.emptyList(),
				Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
				Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, null);
		assertNull(ui.getOnIconUI(3));
		assertNull(ui.getOffIconUI(0));
		assertNull(ui.getLearnIconUI());
		assertNull(ui.getCasterArt(0));
		assertNull(ui.getMissileArt(1));
	}
}
