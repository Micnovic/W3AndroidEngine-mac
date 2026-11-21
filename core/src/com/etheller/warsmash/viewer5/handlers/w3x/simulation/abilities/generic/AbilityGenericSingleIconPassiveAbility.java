package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityVisitor;

/**
 * Represents an ability from the object data
 */
public class AbilityGenericSingleIconPassiveAbility extends AbstractCAbilityDoNothing
		implements GenericSingleIconPassiveAbility {

	public AbilityGenericSingleIconPassiveAbility(final War3ID code, final War3ID alias, final int handleId) {
		super(handleId, code, alias);
	}

	@Override
	public <T> T visit(final CAbilityVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public CAbilityCategory getAbilityCategory() {
		return CAbilityCategory.PASSIVE;
	}

}
