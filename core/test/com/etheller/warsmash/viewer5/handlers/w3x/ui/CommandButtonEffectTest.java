package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

class CommandButtonEffectTest {
	private static int id(final String code) {
		return War3ID.fromString(code).getValue();
	}

	@Test
	void anAbilityEffectWithAPlainOrderNeedsTheAbilityAndTheOrder() {
		final CommandButtonEffect effect = CommandButtonEffect.ability(id("AHbz"), OrderIds.blizzard);
		assertTrue(effect.matches(id("AHbz"), OrderIds.blizzard));
		assertFalse(effect.matches(id("AHbz"), OrderIds.attack));
		assertFalse(effect.matches(id("AHwe"), OrderIds.blizzard));
	}

	@Test
	void anAbilityEffectWithoutAnOrderShinesOnEveryButtonOfTheAbility() {
		final CommandButtonEffect effect = CommandButtonEffect.ability(id("AHbz"), 0);
		assertTrue(effect.matches(id("AHbz"), OrderIds.blizzard));
		assertTrue(effect.matches(id("AHbz"), 0));
		assertFalse(effect.matches(id("AHwe"), OrderIds.blizzard));
	}

	@Test
	void trainingAndBuildingAreTheSameButtonWhateverAbilityIsBehindIt() {
		final CommandButtonEffect effect = CommandButtonEffect.ability(id("Aque"), id("hfoo"));
		assertTrue(effect.matches(id("Aque"), id("hfoo")));
		assertTrue(effect.matches(id("AHbu"), id("hfoo")));
		assertFalse(effect.matches(id("Aque"), id("hrif")));
	}

	@Test
	void upgradeAndLearnEffectsFollowTheCodeOfTheButton() {
		final CommandButtonEffect upgrade = CommandButtonEffect.upgrade(id("Rhme"));
		assertTrue(upgrade.matches(id("Aque"), id("Rhme")));
		assertFalse(upgrade.matches(id("Aque"), id("Rhar")));
		assertFalse(upgrade.matches(id("Aque"), 0));
		final CommandButtonEffect learn = CommandButtonEffect.learn(id("AHbz"));
		assertTrue(learn.matches(id("AHer"), id("AHbz")));
		assertFalse(learn.matches(id("AHer"), OrderIds.skillmenu));
	}
}
