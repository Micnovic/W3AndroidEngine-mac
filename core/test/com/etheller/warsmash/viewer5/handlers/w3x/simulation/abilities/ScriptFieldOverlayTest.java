package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.StringBundle;
import com.etheller.warsmash.util.War3ID;

/** What a script set on one ability reads in front of the data, and nothing else does. */
class ScriptFieldOverlayTest {
	private static GameObject data() throws Exception {
		final DataTable table = new DataTable(StringBundle.EMPTY);
		table.readSLK(new ByteArrayInputStream(String.join("\r\n", "ID;PWXL;N;E", "B;X4;Y2;D0", //
				"C;X1;Y1;K\"alias\"", "C;X2;K\"DataA1\"", "C;X3;K\"Cost1\"", "C;X4;K\"UnitID1\"", //
				"C;X1;Y2;K\"AHtb\"", "C;X2;K75", "C;X3;K90", "C;X4;K\"hfoo,hpea,hrif\"", //
				"E").getBytes(StandardCharsets.UTF_8)));
		return table.get("AHtb");
	}

	private static AbstractCAbility ability() {
		return new CAbilityGenericDoNothing(War3ID.fromString("AHtb"), War3ID.fromString("AHtb"), 1);
	}

	@Test
	void anAbilityWithoutScriptValuesReadsTheDataItself() throws Exception {
		final GameObject base = data();
		assertSame(base, ScriptFieldOverlay.of(ability(), base));
	}

	@Test
	void aScriptValueWinsAndOnlyForThatColumn() throws Exception {
		final GameObject base = data();
		final AbstractCAbility ability = ability();
		ability.setScriptField("DataA1#0", "125.5");
		final GameObject read = ScriptFieldOverlay.of(ability, base);
		assertEquals(125.5f, read.getFieldAsFloat("dataa1", 0), 0f);
		assertEquals(125, read.getFieldAsInteger("DataA1", 0));
		assertEquals(90, read.getFieldAsInteger("Cost1", 0));
		assertEquals(75, base.getFieldAsInteger("DataA1", 0));
	}

	@Test
	void listsTakeAnElementOrAWholeReplacement() throws Exception {
		final GameObject base = data();
		final AbstractCAbility ability = ability();
		ability.setScriptField("UnitID1#1", "hkni");
		GameObject read = ScriptFieldOverlay.of(ability, base);
		assertEquals("hfoo,hkni,hrif", read.getField("UnitID1"));
		assertEquals("hkni", read.getField("UnitID1", 1));
		// The data's lists repeat their last element for an index past the end.
		assertEquals("hrif", read.getField("UnitID1", 9));

		ability.setScriptField("UnitID1#list", "hmpr,hsor");
		read = ScriptFieldOverlay.of(ability, base);
		assertEquals("hmpr,hkni", read.getField("UnitID1"));
		assertEquals(2, read.getFieldAsList("UnitID1").size());
	}

	@Test
	void aGapBeforeAHigherIndexReadsAsTheData() throws Exception {
		final AbstractCAbility ability = ability();
		ability.setScriptField("UnitID1#5", "hkni");
		final GameObject read = ScriptFieldOverlay.of(ability, data());
		assertEquals("hfoo,hpea,hrif,hrif,hrif,hkni", read.getField("UnitID1"));
	}

	@Test
	void integersAreNotRoundedThroughFloat() throws Exception {
		final AbstractCAbility ability = ability();
		ability.setScriptField("DataA1#0", "16777217");
		assertEquals(16777217, ScriptFieldOverlay.of(ability, data()).getFieldValue("DataA1", 0));
	}
}
