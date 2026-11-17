package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.booleancallbacks.ABBooleanCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.floatcallbacks.ABFloatCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.idcallbacks.ABIDCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.integercallbacks.ABIntegerCallback;

public class AbilityBuilderSpecialDisplayFields {
	
	private ABBooleanCallback showOnAndOffIcons;
	
	private ABIntegerCallback foodCost;
	private ABIntegerCallback goldCost;
	private ABIntegerCallback lumberCost;
	
	private ABBooleanCallback hideAreaCursor;
	private ABFloatCallback areaCursorOverride;
	private ABBooleanCallback instantCast;
	private ABBooleanCallback castlessNoTarget;
	
	private ABBooleanCallback toggleable;
	private ABBooleanCallback castToggleOff;
	private ABBooleanCallback separateOnAndOff;
	private ABIDCallback alternateUnitId;

	private ABBooleanCallback isMenu;
	private ABIntegerCallback menuId;

	public void updateFromParent(AbilityBuilderSpecialDisplayFields parent) {
		if (this.showOnAndOffIcons == null)
			this.showOnAndOffIcons = parent.showOnAndOffIcons;

		if (this.foodCost == null)
			this.foodCost = parent.foodCost;
		if (this.goldCost == null)
			this.goldCost = parent.goldCost;
		if (this.lumberCost == null)
			this.lumberCost = parent.lumberCost;

		if (this.hideAreaCursor == null)
			this.hideAreaCursor = parent.hideAreaCursor;
		if (this.areaCursorOverride == null)
			this.areaCursorOverride = parent.areaCursorOverride;
		if (this.instantCast == null)
			this.instantCast = parent.instantCast;
		if (this.castlessNoTarget == null)
			this.castlessNoTarget = parent.castlessNoTarget;

		if (this.toggleable == null)
			this.toggleable = parent.toggleable;
		if (this.castToggleOff == null)
			this.castToggleOff = parent.castToggleOff;
		if (this.separateOnAndOff == null)
			this.separateOnAndOff = parent.separateOnAndOff;
		if (this.alternateUnitId == null)
			this.alternateUnitId = parent.alternateUnitId;
	}
	
	public ABIntegerCallback getFoodCost() {
		return foodCost;
	}
	public void setFoodCost(ABIntegerCallback foodCost) {
		this.foodCost = foodCost;
	}
	public ABIntegerCallback getGoldCost() {
		return goldCost;
	}
	public void setGoldCost(ABIntegerCallback goldCost) {
		this.goldCost = goldCost;
	}
	public ABIntegerCallback getLumberCost() {
		return lumberCost;
	}
	public void setLumberCost(ABIntegerCallback lumberCost) {
		this.lumberCost = lumberCost;
	}
	public ABBooleanCallback getHideAreaCursor() {
		return hideAreaCursor;
	}
	public ABFloatCallback getAreaCursorOverride() {
		return areaCursorOverride;
	}
	public ABBooleanCallback getInstantCast() {
		return instantCast;
	}
	public ABBooleanCallback getCastlessNoTarget() {
		return castlessNoTarget;
	}
	public ABBooleanCallback getToggleable() {
		return toggleable;
	}
	public ABBooleanCallback getCastToggleOff() {
		return castToggleOff;
	}
	public ABBooleanCallback getSeparateOnAndOff() {
		return separateOnAndOff;
	}
	public ABIDCallback getAlternateUnitId() {
		return alternateUnitId;
	}

	public ABBooleanCallback getIsMenu() {
		return this.isMenu;
	}
	public ABIntegerCallback getMenuId() {
		return this.menuId;
	}

}
