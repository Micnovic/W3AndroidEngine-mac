package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractCAbilityPassiveNoOrders;

public class CAbilityItemDefenseBonus extends AbstractCAbilityPassiveNoOrders {
	private int defenseBonus;

	public CAbilityItemDefenseBonus(final int handleId, final War3ID code, final War3ID alias, final int defenseBonus) {
		super(handleId, code, alias);
		this.defenseBonus = defenseBonus;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		unit.setTemporaryDefenseBonus(unit.getTemporaryDefenseBonus() + this.defenseBonus);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		unit.setTemporaryDefenseBonus(unit.getTemporaryDefenseBonus() - this.defenseBonus);
	}

	public void setDefenseBonus(final CSimulation game, final CUnit unit, final int defenseBonus) {
		onRemove(game, unit);
		this.defenseBonus = defenseBonus;
		onAdd(game, unit);
	}

}
