package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.template.DataFieldLetter;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.template.StatBuffFromDataField;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.template.StatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.types.impl.CAbilityTypeAbilityBuilderLevelData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffs;

/**
 * A stat passive of an item (Boots of Speed): every instance has its own bonus, so dropping one item takes off what
 * that item gave even when another unit holds the same item, and the bonus stays through the holder's death.
 */
class StatPassiveTemplateTest {
	private static final War3ID ID = War3ID.fromString("AIms");

	private static StatBuffFromDataField parsedMoveSpeed() {
		final StatBuffFromDataField parsed = TestObjects.allocate(StatBuffFromDataField.class);
		TestObjects.set(parsed, "type", StatBuffType.MVSPD);
		TestObjects.set(parsed, "dataField", DataFieldLetter.A);
		TestObjects.set(parsed, "percentageOverride", Boolean.FALSE);
		return parsed;
	}

	private static CAbilityAbilityBuilderStatPassiveTemplate create(final int handleId,
			final List<StatBuffFromDataField> parsed) {
		final CAbilityTypeAbilityBuilderLevelData level = new CAbilityTypeAbilityBuilderLevelData(
				EnumSet.noneOf(CTargetType.class), 0, 0, 0, 0, 0, 0, Collections.<War3ID>emptyList(),
				Collections.<War3ID>emptyList(), 0, Collections.singletonList("60"), null, Collections.emptyList());
		return new CAbilityAbilityBuilderStatPassiveTemplate(handleId, ID, ID, Collections.singletonList(level),
				new HashMap<>(), parsed);
	}

	@SuppressWarnings("unchecked")
	private static NonStackingStatBuff buffOf(final CAbilityAbilityBuilderStatPassiveTemplate ability) {
		return ((List<StatBuffFromDataField>) TestObjects.get(ability, "statBuffDataFields")).get(0).getBuff();
	}

	@Test
	void instancesDoNotShareTheirBonus() {
		final List<StatBuffFromDataField> parsed = Collections.singletonList(parsedMoveSpeed());
		final CAbilityAbilityBuilderStatPassiveTemplate first = create(1, parsed);
		final NonStackingStatBuff firstBuff = buffOf(first);
		final CAbilityAbilityBuilderStatPassiveTemplate second = create(2, parsed);
		assertNull(parsed.get(0).getBuff());
		assertSame(firstBuff, buffOf(first));
		assertNotSame(firstBuff, buffOf(second));
		assertEquals(NonStackingStatBuffType.MVSPD, firstBuff.getBuffType());
		assertEquals(60f, firstBuff.getValue());
	}

	@Test
	void deathKeepsTheBonus() {
		final CAbilityAbilityBuilderStatPassiveTemplate ability = create(1,
				Collections.singletonList(parsedMoveSpeed()));
		final CUnit unit = TestObjects.allocate(CUnit.class);
		final NonStackingStatBuffs unitBuffs = new NonStackingStatBuffs();
		TestObjects.set(unit, "nonStackingStatBuffs", unitBuffs);
		final NonStackingStatBuff buff = buffOf(ability);
		unitBuffs.add(buff);
		ability.onDeath(null, unit);
		final List<NonStackingStatBuff> held = unitBuffs.get(NonStackingStatBuffType.MVSPD).get("");
		assertNotNull(held);
		assertEquals(Collections.singletonList(buff), held);
	}
}
