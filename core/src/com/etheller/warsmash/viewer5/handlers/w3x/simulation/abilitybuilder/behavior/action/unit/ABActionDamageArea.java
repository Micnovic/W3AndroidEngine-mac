package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.unit;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.floatcallbacks.ABFloatCallback;

public class ABActionDamageArea extends ABActionDamageSplashBase {

	private ABFloatCallback radius;
	private ABFloatCallback damage;

	@Override
	protected ABFloatCallback baseDamageCallback() {
		return damage;
	}

	@Override
	protected ABFloatCallback fullRadiusCallback() {
		return radius;
	}

	@Override
	protected int tierCount() {
		return 0;
	}

	@Override
	protected ABFloatCallback tierRadiusCallback(final int tier) {
		return null;
	}

	@Override
	protected ABFloatCallback tierValueCallback(final int tier) {
		return null;
	}

	@Override
	protected float tierDamage(final float tierValue, final float baseDamage) {
		return baseDamage;
	}

	@Override
	protected float tierRatio(final float tierValue, final float baseDamage) {
		return 1;
	}
}
