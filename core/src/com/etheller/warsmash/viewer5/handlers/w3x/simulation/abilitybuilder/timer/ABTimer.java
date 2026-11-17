package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.timer;

import java.util.List;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABAction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABActionGuard;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

public class ABTimer extends CTimer {
	private CUnit caster;
	private Map<String, Object> localStore;
	private List<ABAction> actions;
	
	private int castId = 0;
	private boolean alive = true;
	private final boolean exactTiming;

	public ABTimer(CUnit caster, Map<String, Object> localStore, List<ABAction> actions, final int castId) {
		this(caster, localStore, actions, castId, false);
	}

	public ABTimer(CUnit caster, Map<String, Object> localStore, List<ABAction> actions, final int castId,
			final boolean exactTiming) {
		super();
		this.caster = caster;
		this.localStore = localStore;
		this.actions = actions;
		this.castId = castId;
		this.exactTiming = exactTiming;
	}

	@Override
	protected boolean isExactTiming() {
		return this.exactTiming;
	}

	public void kill(CSimulation simulation) {
		simulation.unregisterTimer(this);
		this.alive  = false;
		this.caster = null;
		this.localStore = null;
		this.actions = null;
	}
	
	public void onFire(CSimulation simulation) {
		if (alive) {
			localStore.put(ABLocalStoreKeys.FIRINGTIMER, this);
			ABActionGuard.runAll("timer", actions, simulation, caster, localStore, castId);
			if (localStore != null)
				localStore.remove(ABLocalStoreKeys.FIRINGTIMER);
		}
	}
	
}
