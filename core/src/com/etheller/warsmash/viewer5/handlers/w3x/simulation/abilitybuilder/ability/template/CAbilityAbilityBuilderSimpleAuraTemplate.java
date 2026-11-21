package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.types.impl.CAbilityTypeAbilityBuilderLevelData;

public class CAbilityAbilityBuilderSimpleAuraTemplate extends AbstractCAbilityAbilityBuilderAuraTemplate {
	private Map<Integer,List<War3ID>> abilityIdsToAddPerLevel;
	private List<War3ID> levellingAbilityIdsToAdd;

	/** Each unit in the aura gets its own ability instances, keyed by the unit's handle id (lookup only). */
	private final Map<Integer, UnitAuraAbilities> abilitiesByUnit = new HashMap<>();

	private static final class UnitAuraAbilities {
		private final Map<Integer, List<CAbility>> perLevel = new HashMap<>();
		private final List<CLevelingAbility> leveling = new ArrayList<>();
	}

	public CAbilityAbilityBuilderSimpleAuraTemplate(int handleId, War3ID code, War3ID alias,
			List<CAbilityTypeAbilityBuilderLevelData> levelData, Map<String, Object> localStore,
			Map<Integer,List<War3ID>> abilityIdsToAddPerLevel, List<War3ID> levellingAbilityIdsToAdd) {
		super(handleId, code, alias, levelData, localStore);
		this.abilityIdsToAddPerLevel = abilityIdsToAddPerLevel;
		this.levellingAbilityIdsToAdd = levellingAbilityIdsToAdd;
	}

	@Override
	public void setLevel(CSimulation game, CUnit unit, int level) {
		super.setLevel(game, unit, level);
		targetsAllowed = levelData.get(getLevel() - 1).getTargetsAllowed();
		range = levelData.get(getLevel() - 1).getCastRange();
	}

	@Override
	protected void beforeAuraStarts(CSimulation game, CUnit unit) {
		game.getAbilityData().createAbility(getAlias(), game.getHandleIdAllocator().createId());
	}

	@Override
	protected boolean tracksLevelChanges() {
		return true;
	}

	private List<CAbility> levelAbilities(CSimulation game, UnitAuraAbilities held, int level) {
		List<CAbility> list = held.perLevel.get(level);
		if (list == null) {
			list = new ArrayList<>();
			held.perLevel.put(level, list);
			List<War3ID> ids = abilityIdsToAddPerLevel == null ? null : abilityIdsToAddPerLevel.get(level);
			if (ids != null) {
				for (War3ID abilityId : ids) {
					list.add(game.getAbilityData().getAbilityType(abilityId)
							.createAbility(game.getHandleIdAllocator().createId()));
				}
			}
		}
		return list;
	}

	@Override
	public void addUnitToAura(CSimulation game, CUnit unit) {
		auraGroup.add(unit);
		UnitAuraAbilities held = new UnitAuraAbilities();
		abilitiesByUnit.put(unit.getHandleId(), held);
		for (CAbility ability : levelAbilities(game, held, getLevel())) {
			unit.add(game, ability);
		}
		if (levellingAbilityIdsToAdd != null) {
			for (War3ID abilityId : levellingAbilityIdsToAdd) {
				CAbility abil = game.getAbilityData().getAbilityType(abilityId)
						.createAbility(game.getHandleIdAllocator().createId());
				if (abil instanceof CLevelingAbility) {
					held.leveling.add((CLevelingAbility) abil);
					unit.add(game, abil);
				}
			}
		}
	}

	@Override
	public void updateLevelOfAura(CSimulation game, CUnit unit, int prevLevel, int curLevel) {
		UnitAuraAbilities held = abilitiesByUnit.get(unit.getHandleId());
		if (held == null) {
			return;
		}
		for (CAbility ability : levelAbilities(game, held, prevLevel)) {
			unit.remove(game, ability);
		}
		for (CAbility ability : levelAbilities(game, held, curLevel)) {
			unit.add(game, ability);
		}
		for (CLevelingAbility ability : held.leveling) {
			ability.setLevel(game, unit, curLevel);
		}
	}

	@Override
	public void removeUnitFromAura(CSimulation game, CUnit unit) {
		UnitAuraAbilities held = abilitiesByUnit.remove(unit.getHandleId());
		if (held != null) {
			for (CAbility ability : levelAbilities(game, held, getLevel())) {
				unit.remove(game, ability);
			}
			for (CAbility ability : held.leveling) {
				unit.remove(game, ability);
			}
		}
		auraGroup.remove(unit);
	}
}
