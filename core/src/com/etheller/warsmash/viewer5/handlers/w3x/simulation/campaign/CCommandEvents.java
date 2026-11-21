package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import java.util.ArrayList;
import java.util.List;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;

/**
 * The triggers a script registered for a command card button: one ability with one order, or the research of one
 * upgrade. They fire when a player uses the button, through that player's commands, so the order is also the one the
 * unit receives.
 */
public final class CCommandEvents {
	private final List<Registration> registrations = new ArrayList<>();

	public RemovableTriggerEvent addCommandEvent(final Trigger trigger, final War3ID abilityId, final int orderId) {
		return add(new Registration(trigger, abilityId, orderId, false));
	}

	public RemovableTriggerEvent addUpgradeEvent(final Trigger trigger, final War3ID upgradeId) {
		return add(new Registration(trigger, upgradeId, 0, true));
	}

	private RemovableTriggerEvent add(final Registration registration) {
		this.registrations.add(registration);
		return new EventRegistration(registration, this.registrations);
	}

	/**
	 * @param abilityId the alias of the ability the button belongs to
	 * @param orderId   the order the button issues; a research order carries the upgrade's id
	 */
	public void fire(final GlobalScope globalScope, final CUnit unit, final War3ID abilityId, final int orderId) {
		for (final Registration registration : new ArrayList<>(this.registrations)) {
			if (registration.matches(abilityId, orderId)) {
				final CommonTriggerExecutionScope scope = CommonTriggerExecutionScope.unitOrderScope(
						JassGameEventsWar3.EVENT_UNIT_ISSUED_ORDER, registration.trigger, unit, orderId);
				globalScope.queueTrigger(null, null, registration.trigger, scope, scope);
			}
		}
	}

	public boolean isEmpty() {
		return this.registrations.isEmpty();
	}

	private static final class Registration {
		private final Trigger trigger;
		private final War3ID id;
		private final int orderId;
		private final boolean upgrade;

		private Registration(final Trigger trigger, final War3ID id, final int orderId, final boolean upgrade) {
			this.trigger = trigger;
			this.id = id;
			this.orderId = orderId;
			this.upgrade = upgrade;
		}

		private boolean matches(final War3ID abilityId, final int issuedOrderId) {
			if (this.upgrade) {
				return this.id.getValue() == issuedOrderId;
			}
			// An order of 0 is a script that named no order: any order of the ability counts.
			return this.id.equals(abilityId) && ((this.orderId == 0) || (this.orderId == issuedOrderId));
		}
	}

	private static final class EventRegistration extends RemovableTriggerEvent {
		private final Registration registration;
		private final List<Registration> registrations;

		private EventRegistration(final Registration registration, final List<Registration> registrations) {
			super(registration.trigger);
			this.registration = registration;
			this.registrations = registrations;
		}

		@Override
		public void remove() {
			this.registrations.remove(this.registration);
		}
	}
}
