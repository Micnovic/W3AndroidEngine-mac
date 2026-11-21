package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.StringBundle;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.demonhunter.CAbilityImmolation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.CAbilityTypeDefinitionImmolation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;

/** An ability built on the level data of its type takes the numbers a script set on it, and no other ability does. */
class AbilityTypeScriptFieldsTest {
	private static final String[] COLUMNS = { "alias", "code", "levels", "targs1", "targs2", "DataA1", "DataA2",
			"DataB1", "DataB2", "DataC1", "DataC2", "Dur1", "Dur2", "Cost1", "Cost2", "Area1", "Area2", "BuffID1",
			"BuffID2" };
	private static final String[] VALUES = { "\"AEim\"", "\"AEim\"", "2", "\"\"", "\"\"", "25", "40", "10", "10", "5",
			"5", "1", "1", "30", "30", "200", "250", "\"\"", "\"\"" };

	private static GameObject data() throws Exception {
		final StringBuilder slk = new StringBuilder("ID;PWXL;N;E\r\nB;X" + COLUMNS.length + ";Y2;D0\r\n");
		for (int i = 0; i < COLUMNS.length; i++) {
			slk.append("C;X").append(i + 1).append(";Y1;K\"").append(COLUMNS[i]).append("\"\r\n");
		}
		for (int i = 0; i < VALUES.length; i++) {
			slk.append("C;X").append(i + 1).append(";Y2;K").append(VALUES[i]).append("\r\n");
		}
		slk.append("E");
		final DataTable table = new DataTable(StringBundle.EMPTY);
		table.readSLK(new ByteArrayInputStream(slk.toString().getBytes(StandardCharsets.UTF_8)));
		return table.get("AEim");
	}

	@Test
	void aScriptValueChangesOnlyTheAbilityItWasSetOn() throws Exception {
		final CAbilityType<?> type = new CAbilityTypeDefinitionImmolation().createAbilityType(War3ID.fromString("AEim"),
				data());
		assertTrue(type.appliesScriptFields());
		final CAbilityImmolation changed = (CAbilityImmolation) type.createAbility(1);
		final CAbilityImmolation other = (CAbilityImmolation) type.createAbility(2);
		assertEquals(25f, changed.getDamagePerInterval(), 0f);

		changed.setScriptField("DataA1#0", "77");
		type.setLevel(null, null, changed, 1);
		type.setLevel(null, null, other, 1);

		assertEquals(77f, changed.getDamagePerInterval(), 0f);
		assertEquals(25f, other.getDamagePerInterval(), 0f);
		assertEquals(25f, ((CAbilityImmolation) type.createAbility(3)).getDamagePerInterval(), 0f);
		// the other numbers of the level stay the data's
		assertEquals(200f, changed.getAreaOfEffect(), 0f);
	}

	@Test
	void aSetOnOneLevelDoesNotTouchTheOthers() throws Exception {
		final CAbilityType<?> type = new CAbilityTypeDefinitionImmolation().createAbilityType(War3ID.fromString("AEim"),
				data());
		final CAbilityImmolation ability = (CAbilityImmolation) type.createAbility(1);
		ability.setScriptField("DataA1#0", "77");
		ability.setScriptField("Area2#0", "999");
		type.setLevel(null, null, ability, 2);
		assertEquals(40f, ability.getDamagePerInterval(), 0f);
		assertEquals(999f, ability.getAreaOfEffect(), 0f);
		assertEquals(2, ((CLevelingAbility) ability).getLevel());
		type.setLevel(null, null, ability, 1);
		assertEquals(77f, ability.getDamagePerInterval(), 0f);
		assertEquals(200f, ability.getAreaOfEffect(), 0f);
		assertFalse(ability.getScriptFields().isEmpty());
	}
}
