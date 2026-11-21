package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbilityGenericSingleIconPassiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABPermanentPassiveBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABDataText;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.template.StatBuffFromDataField;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.types.impl.CAbilityTypeAbilityBuilderLevelData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;

public class CAbilityAbilityBuilderStatPassiveTemplate extends AbilityGenericSingleIconPassiveAbility {

	private List<CAbilityTypeAbilityBuilderLevelData> levelData;
	private Map<String, Object> localStore;

	private War3ID buffId;
	private CBuff buff;

	private CUnit caster;

	private String auraStackingKey;

	private List<StatBuffFromDataField> statBuffDataFields;

	public CAbilityAbilityBuilderStatPassiveTemplate(int handleId, War3ID code, War3ID alias,
			List<CAbilityTypeAbilityBuilderLevelData> levelData, Map<String, Object> localStore,
			List<StatBuffFromDataField> statBuffDataFields) {
		super(code, alias, handleId);
		this.levelData = levelData;
		this.localStore = localStore;
		// Own copies: each instance keeps its own buffs in them, and the parser's list is shared by every instance of
		// the ability (two units holding the same item).
		this.statBuffDataFields = new ArrayList<>();
		for (StatBuffFromDataField statBuff : statBuffDataFields) {
			this.statBuffDataFields.add(new StatBuffFromDataField(statBuff));
		}
		this.auraStackingKey = "";

		if (!this.levelData.get(getLevel() - 1).getBuffs().isEmpty()) {
			buffId = this.levelData.get(getLevel() - 1).getBuffs().get(0);
			this.auraStackingKey = buffId.asStringValue();
		}

		for (StatBuffFromDataField statBuff : this.statBuffDataFields) {
			createNewBuffs(statBuff);
		}
	}

	private void removeExistingBuffs(CSimulation game, StatBuffFromDataField statBuff) {
		this.caster.removeNonStackingStatBuff(game, statBuff.getBuff());
		if (statBuff.getSecondAtkBuff() != null) {
			this.caster.removeNonStackingStatBuff(game, statBuff.getSecondAtkBuff());
		}
	}

	private void replaceBuffs(CSimulation game, StatBuffFromDataField statBuff) {
		if (this.caster == null) {
			// Not added yet: onAdd attaches whatever is current.
			createNewBuffs(statBuff);
			return;
		}
		removeExistingBuffs(game, statBuff);
		createNewBuffs(statBuff);
		this.caster.addNonStackingStatBuff(game, statBuff.getBuff());
		if (statBuff.getSecondAtkBuff() != null) {
			this.caster.addNonStackingStatBuff(game, statBuff.getSecondAtkBuff());
		}
	}

	private void createNewBuffs(StatBuffFromDataField parsedBuff) {
		parsedBuff.setSecondAtkBuff(null);
		NonStackingStatBuffType type = parsedBuff.convertToNonStackingType(levelData.get(getLevel() - 1));
		NonStackingStatBuff newBuff = null;
		switch (type) {
		case RNGDATK:
		case RNGDATKPCT:
			newBuff = new NonStackingStatBuff(type, this.auraStackingKey, ABDataText
					.parseFloat(levelData.get(getLevel() - 1).getData().get(parsedBuff.getDataField().getIndex())));
			parsedBuff.setBuff(newBuff);
			break;
		case MELEEATK:
		case MELEEATKPCT:
			newBuff = new NonStackingStatBuff(type, this.auraStackingKey, ABDataText
					.parseFloat(levelData.get(getLevel() - 1).getData().get(parsedBuff.getDataField().getIndex())));
			parsedBuff.setBuff(newBuff);
			break;
		case ALLATK:
			newBuff = new NonStackingStatBuff(NonStackingStatBuffType.MELEEATK, this.auraStackingKey, ABDataText
					.parseFloat(levelData.get(getLevel() - 1).getData().get(parsedBuff.getDataField().getIndex())));
			parsedBuff.setBuff(newBuff);
			newBuff = new NonStackingStatBuff(NonStackingStatBuffType.RNGDATK, this.auraStackingKey, ABDataText
					.parseFloat(levelData.get(getLevel() - 1).getData().get(parsedBuff.getDataField().getIndex())));
			parsedBuff.setSecondAtkBuff(newBuff);
			break;
		case ALLATKPCT:
			newBuff = new NonStackingStatBuff(NonStackingStatBuffType.MELEEATKPCT, this.auraStackingKey, ABDataText
					.parseFloat(levelData.get(getLevel() - 1).getData().get(parsedBuff.getDataField().getIndex())));
			parsedBuff.setBuff(newBuff);
			newBuff = new NonStackingStatBuff(NonStackingStatBuffType.RNGDATKPCT, this.auraStackingKey, ABDataText
					.parseFloat(levelData.get(getLevel() - 1).getData().get(parsedBuff.getDataField().getIndex())));
			parsedBuff.setSecondAtkBuff(newBuff);
			break;
		default:
			newBuff = new NonStackingStatBuff(type, this.auraStackingKey, ABDataText
					.parseFloat(levelData.get(getLevel() - 1).getData().get(parsedBuff.getDataField().getIndex())));
			parsedBuff.setBuff(newBuff);
		}
	}

	@Override
	public void setLevel(CSimulation game, CUnit unit, int level) {
		super.setLevel(game, unit, level);
		localStore.put(ABLocalStoreKeys.CURRENTLEVEL, level);
		for (StatBuffFromDataField statBuff : this.statBuffDataFields) {
			NonStackingStatBuffType type = statBuff.convertToNonStackingType(levelData.get(getLevel() - 1));
			if (type == null) {
				continue;
			}
			switch (type) {
			case ALLATK:
				if (statBuff.getSecondAtkBuff() == null) {
					replaceBuffs(game, statBuff);
				} else if (statBuff.getBuff().getBuffType() != NonStackingStatBuffType.MELEEATK) {
					replaceBuffs(game, statBuff);
				} else {
					statBuff.getBuff().setValue(ABDataText.parseFloat(
							levelData.get(getLevel() - 1).getData().get(statBuff.getDataField().getIndex())));
				}
				break;
			case ALLATKPCT:
				if (statBuff.getSecondAtkBuff() == null) {
					replaceBuffs(game, statBuff);
				} else if (statBuff.getBuff().getBuffType() != NonStackingStatBuffType.MELEEATKPCT) {
					replaceBuffs(game, statBuff);
				} else {
					statBuff.getBuff().setValue(ABDataText.parseFloat(
							levelData.get(getLevel() - 1).getData().get(statBuff.getDataField().getIndex())));
				}
				break;
			default:
				if (statBuff.getSecondAtkBuff() != null) {
					replaceBuffs(game, statBuff);
				} else if (type != statBuff.getBuff().getBuffType()) {
					replaceBuffs(game, statBuff);
				} else {
					statBuff.getBuff().setValue(ABDataText.parseFloat(
							levelData.get(getLevel() - 1).getData().get(statBuff.getDataField().getIndex())));
				}
			}
			if (caster == null) {
				continue;
			}
			if (statBuff.getBuff().getBuffType().isHeroStat()) {
				caster.computeDerivedHeroFields(game, statBuff.getBuff().getBuffType());
			} else {
				caster.computeDerivedFields(statBuff.getBuff().getBuffType());
			}
		}
		if (this.buff != null) {
			this.buff.setLevel(game, unit, level);
		}
	}

	@Override
	public void onAdd(CSimulation game, CUnit unit) {
		if (this.buffId != null) {
			this.buff = new ABPermanentPassiveBuff(game.getHandleIdAllocator().createId(), this.buffId, this, unit,
					localStore, null, null, 0, false, true);
			unit.addNonStackingDisplayBuff(game, auraStackingKey, buff);
		}
		this.caster = unit;
		game.getAbilityData().createAbility(getAlias(), game.getHandleIdAllocator().createId());

		for (StatBuffFromDataField statBuff : this.statBuffDataFields) {
			unit.addNonStackingStatBuff(game, statBuff.getBuff());
			if (statBuff.getSecondAtkBuff() != null) {
				unit.addNonStackingStatBuff(game, statBuff.getSecondAtkBuff());
			}
		}
	}

	@Override
	public void onRemove(CSimulation game, CUnit unit) {
		removeBuffFromUnit(game, unit);
	}

	@Override
	public void onTick(CSimulation game, CUnit unit) {
	}

	public void removeBuffFromUnit(CSimulation game, CUnit unit) {
		if (this.buff != null) {
			unit.removeNonStackingDisplayBuff(game, auraStackingKey, buff);
		}
		for (StatBuffFromDataField statBuff : this.statBuffDataFields) {
			unit.removeNonStackingStatBuff(game, statBuff.getBuff());
			if (statBuff.getSecondAtkBuff() != null) {
				unit.removeNonStackingStatBuff(game, statBuff.getSecondAtkBuff());
			}
		}
	}

	@Override
	public void onDeath(CSimulation game, CUnit unit) {
		// The bonus stays through death: nothing gives it back on revival or reincarnation, only the ability's removal
		// takes it away (as the other item stat bonuses).
	}

}
