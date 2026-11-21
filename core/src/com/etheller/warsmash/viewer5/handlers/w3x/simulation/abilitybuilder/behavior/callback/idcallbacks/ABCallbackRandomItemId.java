package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.idcallbacks;

import java.util.Map;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

public class ABCallbackRandomItemId extends ABIDCallback {

	@Override
	public War3ID callback(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
			final int castId) {
		final CItemType type = game.getRandomTypes().getRandomItemType(game);
		return type == null ? War3ID.NONE : type.getTypeId();
	}

}
