package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser;

import java.util.List;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.booleancallbacks.ABBooleanCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.enumcallbacks.ABAutocastTypeCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.enumcallbacks.ABTargetTypeCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.floatcallbacks.ABFloatCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.idcallbacks.ABIDCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.integercallbacks.ABIntegerCallback;

public class AbilityBuilderOverrideFields {
	
	private ABFloatCallback areaOverride;
	private ABFloatCallback rangeOverride;
	private ABFloatCallback castTimeOverride;
	private ABFloatCallback cooldownOverride;
	private ABIntegerCallback manaCostOverride;

	private ABBooleanCallback ignoreCastTime;
	private ABBooleanCallback repeatCast;
	
	private ABAutocastTypeCallback autocastTypeOverride;
	
	private ABIDCallback onTooltipOverride;
	private ABIDCallback offTooltipOverride;
	
	private ABBooleanCallback physicalSpell;
	private ABBooleanCallback magicSpell;
	private ABBooleanCallback universalSpell;
	private ABBooleanCallback dispel;
	
	List<ABTargetTypeCallback> extraTargetsAllowed;
	List<ABTargetTypeCallback> excludedTargetsAllowed;

	public void updateFromParent(AbilityBuilderOverrideFields parent) {
		if (this.areaOverride == null)
			this.areaOverride = parent.areaOverride;
		if (this.rangeOverride == null)
			this.rangeOverride = parent.rangeOverride;
		if (this.castTimeOverride == null)
			this.castTimeOverride = parent.castTimeOverride;
		if (this.cooldownOverride == null)
			this.cooldownOverride = parent.cooldownOverride;
		if (this.manaCostOverride == null)
			this.manaCostOverride = parent.manaCostOverride;

		if (this.ignoreCastTime == null)
			this.ignoreCastTime = parent.ignoreCastTime;
		if (this.repeatCast == null)
			this.repeatCast = parent.repeatCast;

		if (this.autocastTypeOverride == null)
			this.autocastTypeOverride = parent.autocastTypeOverride;

		if (this.onTooltipOverride == null)
			this.onTooltipOverride = parent.onTooltipOverride;
		if (this.offTooltipOverride == null)
			this.offTooltipOverride = parent.offTooltipOverride;

		if (this.physicalSpell == null)
			this.physicalSpell = parent.physicalSpell;
		if (this.magicSpell == null)
			this.magicSpell = parent.magicSpell;
		if (this.universalSpell == null)
			this.universalSpell = parent.universalSpell;
	}
	
	public ABFloatCallback getAreaOverride() {
		return areaOverride;
	}
	public ABFloatCallback getRangeOverride() {
		return rangeOverride;
	}
	public ABFloatCallback getCastTimeOverride() {
		return castTimeOverride;
	}
	public ABFloatCallback getCooldownOverride() {
		return cooldownOverride;
	}
	public ABIntegerCallback getManaCostOverride() {
		return manaCostOverride;
	}
	public ABBooleanCallback getIgnoreCastTime() {
		return ignoreCastTime;
	}
	public ABBooleanCallback getRepeatCast() {
		return repeatCast;
	}
	public ABAutocastTypeCallback getAutocastTypeOverride() {
		return autocastTypeOverride;
	}
	public ABIDCallback getOnTooltipOverride() {
		return onTooltipOverride;
	}
	public ABIDCallback getOffTooltipOverride() {
		return offTooltipOverride;
	}
	public ABBooleanCallback getPhysicalSpell() {
		return physicalSpell;
	}
	public ABBooleanCallback getMagicSpell() {
		return magicSpell;
	}
	public ABBooleanCallback getUniversalSpell() {
		return universalSpell;
	}
	public ABBooleanCallback getDispel() {
		return dispel;
	}

	public List<ABTargetTypeCallback> getExtraTargetsAllowed() {
		return extraTargetsAllowed;
	}
	public List<ABTargetTypeCallback> getExcludedTargetsAllowed() {
		return excludedTargetsAllowed;
	}

}
