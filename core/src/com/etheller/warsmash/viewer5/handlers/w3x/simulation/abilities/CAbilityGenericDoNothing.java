package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractCAbilityDoNothing;

/**
 * Represents an ability from the object data
 */
public class CAbilityGenericDoNothing extends AbstractCAbilityDoNothing {

	public CAbilityGenericDoNothing(final War3ID code, final War3ID alias, final int handleId) {
		super(handleId, code, alias);
	}

	@Override
	public <T> T visit(final CAbilityVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public CAbilityCategory getAbilityCategory() {
		return CAbilityCategory.CORE;
	}

}
