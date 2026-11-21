package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability;

import java.util.List;
import java.util.Map;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.Aliased;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.AbilityBuilderConfiguration;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.types.impl.CAbilityTypeAbilityBuilderLevelData;

public interface AbilityBuilderAbility extends CLevelingAbility, Aliased {
	public List<CAbilityTypeAbilityBuilderLevelData> getLevelData();

	public AbilityBuilderConfiguration getConfig();

	public Map<String, Object> getLocalStore();

	/** Reads the level data from the given list from now on, for the ability and its callbacks. */
	void setLevelData(List<CAbilityTypeAbilityBuilderLevelData> levelData);

	/** Fills the numbers taken from the level data (cost, cooldown, range, area, cast time) again. */
	void refreshSpellFields(CSimulation game, CUnit unit);
	
	public float getArea();
	
	public float getCooldown();

	public float getCastRange();

	public float getCastTime();

	public void startCooldown(CSimulation game, CUnit unit);

	public void resetCooldown(CSimulation game, CUnit unit);

	public float getCooldownRemainingTicks(CSimulation game, CUnit unit);
	
	War3ID getOnTooltipOverride();
	
	public int getAbilityIntField(String field);
	public float getAbilityFloatField(String field);
	public String getAbilityStringField(String field);
	public boolean getAbilityBooleanField(String field);

	boolean hasUniqueFlag(String flag);
	void addUniqueFlag(String flag);
	void removeUniqueFlag(String flag);

	public int getIconVisibleMenuId();
	void setIconVisibleMenuId(int menu);

}
