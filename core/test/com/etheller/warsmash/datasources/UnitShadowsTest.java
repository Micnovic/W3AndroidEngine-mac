package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.StringBundle;

class UnitShadowsTest {
	@Test
	void classicDefaultsToTheBlob() {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		assertFalse(GraphicsSettings.modelUnitShadows(ini, GraphicsSet.CLASSIC, true));
		assertFalse(GraphicsSettings.modelUnitShadows(null, GraphicsSet.CLASSIC, false));
	}

	@Test
	void reforgedDefaultsToModelShapes() {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		assertTrue(GraphicsSettings.modelUnitShadows(ini, GraphicsSet.REFORGED, true));
		assertTrue(GraphicsSettings.modelUnitShadows(null, GraphicsSet.REFORGED, false));
	}

	@Test
	void settingOverridesTheGraphics() {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		GraphicsSettings.apply(ini, GraphicsSet.CLASSIC, 0);
		ini.get(GraphicsSettings.SECTION).setField(GraphicsSettings.KEY_UNIT_SHADOWS, "Full");
		assertTrue(GraphicsSettings.modelUnitShadows(ini, GraphicsSet.CLASSIC, true));
		ini.get(GraphicsSettings.SECTION).setField(GraphicsSettings.KEY_UNIT_SHADOWS, "Blob");
		assertFalse(GraphicsSettings.modelUnitShadows(ini, GraphicsSet.REFORGED, true));
	}
}
