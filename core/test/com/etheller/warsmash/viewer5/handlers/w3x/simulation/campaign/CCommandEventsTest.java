package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.warsmash.util.War3ID;

class CCommandEventsTest {
	private static final War3ID BLIZZARD = War3ID.fromString("AHbz");
	private static final War3ID STORM_BOLT = War3ID.fromString("AHtb");
	private static final War3ID UPGRADE = War3ID.fromString("Rhme");
	private static final int ORDER_A = 852089;
	private static final int ORDER_B = 852090;

	private final JassProgram program = new JassProgram();
	private final CCommandEvents events = new CCommandEvents();
	private int fired;

	private Trigger counting() {
		final Trigger trigger = new Trigger();
		trigger.addAction((arguments, globalScope, triggerScope) -> {
			this.fired++;
			return null;
		});
		return trigger;
	}

	@Test
	void commandEventNeedsTheSameAbilityAndOrder() {
		this.events.addCommandEvent(counting(), BLIZZARD, ORDER_A);
		this.events.fire(this.program.getGlobalScope(), null, BLIZZARD, ORDER_A);
		assertEquals(1, this.fired);
		this.events.fire(this.program.getGlobalScope(), null, BLIZZARD, ORDER_B);
		this.events.fire(this.program.getGlobalScope(), null, STORM_BOLT, ORDER_A);
		assertEquals(1, this.fired);
	}

	@Test
	void commandEventWithoutOrderTakesAnyOrderOfTheAbility() {
		this.events.addCommandEvent(counting(), BLIZZARD, 0);
		this.events.fire(this.program.getGlobalScope(), null, BLIZZARD, ORDER_A);
		this.events.fire(this.program.getGlobalScope(), null, BLIZZARD, ORDER_B);
		this.events.fire(this.program.getGlobalScope(), null, STORM_BOLT, ORDER_B);
		assertEquals(2, this.fired);
	}

	@Test
	void upgradeEventFiresOnTheResearchOrder() {
		this.events.addUpgradeEvent(counting(), UPGRADE);
		this.events.fire(this.program.getGlobalScope(), null, BLIZZARD, UPGRADE.getValue());
		this.events.fire(this.program.getGlobalScope(), null, BLIZZARD, ORDER_A);
		assertEquals(1, this.fired);
	}

	@Test
	void removedRegistrationStopsFiring() {
		final RemovableTriggerEvent event = this.events.addCommandEvent(counting(), BLIZZARD, ORDER_A);
		event.remove();
		this.events.fire(this.program.getGlobalScope(), null, BLIZZARD, ORDER_A);
		assertEquals(0, this.fired);
	}
}
