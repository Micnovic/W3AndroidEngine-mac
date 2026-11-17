package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffSpellArt;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * Rune of Rebirth: a new unit of the type the rune was dropped by (its drop id) comes to life next to the unit that took
 * it, under that unit's control. Heroes and runes without a drop id give nothing.
 */
public class CAbilityItemRebirth extends AbstractCAbilityItemInstantUse {
	private War3ID buffId;

	public CAbilityItemRebirth(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.itemruneofrebirth;
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if (orderId != getBaseOrderId()) {
			return true;
		}
		final CItem item = getItem();
		game.unitSoundEffectEvent(caster, getAlias());
		final War3ID dropId = item == null ? null : item.getDropId();
		final CUnitType dropType = (dropId == null) ? null : game.getUnitData().getUnitType(dropId);
		if ((dropType != null) && !dropType.isHero()) {
			final CUnit reborn = game.createUnit(dropId, caster.getPlayerIndex(), caster.getX(), caster.getY(),
					caster.getFacing());
			if (reborn != null) {
				reborn.add(game, new CBuffSpellArt(game.getHandleIdAllocator().createId(), this.buffId, true, true));
				game.createTemporarySpellEffectOnUnit(reborn, getAlias(), CEffectType.TARGET);
			}
		}
		return false;
	}
}
