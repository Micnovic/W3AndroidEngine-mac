package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.StringBundle;

class GraphicsSettingsTextureCapTest {
	private static final long LOW_MB = 3700;
	private static final long PHONE_MB = 5 * 1024;
	private static final long JUST_BELOW_TABLET_MB = (6 * 1024) - 1;
	private static final long TABLET_MB = 6 * 1024;
	private static final long S8U_MB = 7400;

	@AfterEach
	void forgetTheDevice() {
		GraphicsSettings.setDeviceMemoryMb(0);
		System.clearProperty(GraphicsSettings.MEMORY_TIER_PROPERTY);
	}

	private static DataTable ini(final String size, final String compression) {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		if (size != null) {
			GraphicsSettings.apply(ini, GraphicsSet.REFORGED, Integer.parseInt(size));
		}
		if (compression != null) {
			GraphicsSettings.apply(ini, GraphicsSet.REFORGED, -1);
			final com.etheller.warsmash.units.Element section = ini.get(GraphicsSettings.SECTION);
			section.setField(GraphicsSettings.KEY_TEXTURE_COMPRESSION, compression);
		}
		return ini;
	}

	private static int size(final DataTable ini, final GraphicsSet graphics, final int defaultSize,
			final boolean etc2) {
		return GraphicsSettings.textureSize(ini, graphics, () -> defaultSize, etc2);
	}

	@Test
	void capFollowsMemoryTierAndCompression() {
		assertEquals(0, GraphicsSettings.textureCap(0, true));
		assertEquals(0, GraphicsSettings.textureCap(-1, false));
		assertEquals(512, GraphicsSettings.textureCap(LOW_MB, true));
		assertEquals(512, GraphicsSettings.textureCap(LOW_MB, false));
		assertEquals(1024, GraphicsSettings.textureCap(PHONE_MB, true));
		assertEquals(512, GraphicsSettings.textureCap(PHONE_MB, false));
		assertEquals(1024, GraphicsSettings.textureCap(JUST_BELOW_TABLET_MB, true));
		assertEquals(512, GraphicsSettings.textureCap(JUST_BELOW_TABLET_MB, false));
		assertEquals(2048, GraphicsSettings.textureCap(TABLET_MB, true));
		assertEquals(1024, GraphicsSettings.textureCap(TABLET_MB, false));
		assertEquals(2048, GraphicsSettings.textureCap(S8U_MB, true));
		assertEquals(1024, GraphicsSettings.textureCap(S8U_MB, false));
	}

	@Test
	void uncompressedCapIsOneStepBelowCompressed() {
		// A size one step up takes four times the memory, as does leaving ETC2 off.
		for (final long mb : new long[] { PHONE_MB, TABLET_MB, S8U_MB, 16384 }) {
			assertEquals(GraphicsSettings.textureCap(mb, true) / 2, GraphicsSettings.textureCap(mb, false));
		}
	}

	@Test
	void limitToCapLowersOnlyWhatExceedsIt() {
		assertEquals(1024, GraphicsSettings.limitToCap(2048, 1024));
		assertEquals(1024, GraphicsSettings.limitToCap(0, 1024));
		assertEquals(512, GraphicsSettings.limitToCap(512, 1024));
		assertEquals(1024, GraphicsSettings.limitToCap(1024, 1024));
		assertEquals(2048, GraphicsSettings.limitToCap(2048, 0));
		assertEquals(0, GraphicsSettings.limitToCap(0, 0));
	}

	@Test
	void savedValueIsLoweredAtLoadWhateverItIs() {
		GraphicsSettings.setDeviceMemoryMb(S8U_MB);
		// ETC2 off (the case that was killed on a 7.4 GB tablet): cap 1024.
		for (final String saved : new String[] { "512", "1024", "2048", "0", "4096", " 2048 ", "-7" }) {
			final DataTable off = ini(null, "Off");
			GraphicsSettings.apply(off, GraphicsSet.REFORGED, -1);
			off.get(GraphicsSettings.SECTION).setField(GraphicsSettings.KEY_TEXTURE_SIZE, saved);
			final int expected = saved.equals("512") ? 512 : 1024;
			assertEquals(expected, size(off, GraphicsSet.REFORGED, 512, true), "off, saved " + saved);
			assertEquals(expected, size(off, GraphicsSet.DE, 512, true), "off DE, saved " + saved);
			// The stored value is not rewritten.
			assertEquals(Math.max(0, Integer.parseInt(saved.trim())), GraphicsSettings.configuredTextureSize(off));
		}
		// ETC2 on: cap 2048.
		for (final String saved : new String[] { "512", "1024", "2048", "0", "8192" }) {
			final DataTable on = ini(null, "On");
			on.get(GraphicsSettings.SECTION).setField(GraphicsSettings.KEY_TEXTURE_SIZE, saved);
			final int expected = saved.equals("512") ? 512 : saved.equals("1024") ? 1024 : 2048;
			assertEquals(expected, size(on, GraphicsSet.REFORGED, 512, true), "on, saved " + saved);
		}
	}

	@Test
	void phoneTiersLowerEverything() {
		GraphicsSettings.setDeviceMemoryMb(PHONE_MB);
		final DataTable auto = ini("2048", null);
		assertEquals(1024, size(auto, GraphicsSet.REFORGED, 512, true));
		final DataTable off = ini("2048", "Off");
		assertEquals(512, size(off, GraphicsSet.REFORGED, 512, true));
		assertEquals(512, size(ini("0", "Off"), GraphicsSet.DE, 512, true));
	}

	@Test
	void tabletBoundaryAt6Gb() {
		final DataTable saved = ini("2048", null);
		GraphicsSettings.setDeviceMemoryMb(JUST_BELOW_TABLET_MB);
		assertEquals(1024, size(saved, GraphicsSet.REFORGED, 512, true));
		GraphicsSettings.setDeviceMemoryMb(TABLET_MB);
		assertEquals(2048, size(saved, GraphicsSet.REFORGED, 512, true));
	}

	@Test
	void gpuWithoutEtc2IsUncompressedWhateverTheSetting() {
		GraphicsSettings.setDeviceMemoryMb(S8U_MB);
		assertEquals(1024, size(ini("2048", "On"), GraphicsSet.REFORGED, 512, false));
		assertEquals(2048, size(ini("2048", "On"), GraphicsSet.REFORGED, 512, true));
	}

	@Test
	void autoCompressionCountsAsOnOnAMobileDevice() {
		GraphicsSettings.setDeviceMemoryMb(S8U_MB);
		assertEquals(2048, size(ini("2048", "Auto"), GraphicsSet.REFORGED, 512, true));
		assertEquals(2048, size(ini("2048", null), GraphicsSet.REFORGED, 512, true));
		assertEquals(1024, size(ini("2048", "false"), GraphicsSet.REFORGED, 512, true));
		assertEquals(1024, size(ini("2048", "0"), GraphicsSet.REFORGED, 512, true));
	}

	@Test
	void theDefaultIsCappedToo() {
		GraphicsSettings.setDeviceMemoryMb(S8U_MB);
		final DataTable none = ini(null, "Off");
		assertEquals(1024, size(none, GraphicsSet.REFORGED, 2048, true));
		assertEquals(512, size(none, GraphicsSet.REFORGED, 512, true));
	}

	@Test
	void classicAndUnknownMemoryAreNotCapped() {
		GraphicsSettings.setDeviceMemoryMb(PHONE_MB);
		assertEquals(0, size(ini(null, "Off"), GraphicsSet.CLASSIC, 2048, true));
		assertEquals(2048, size(ini("2048", "Off"), GraphicsSet.CLASSIC, 512, true));
		// A desktop never tells its memory, so nothing is lowered.
		GraphicsSettings.setDeviceMemoryMb(0);
		assertEquals(0, size(ini("0", "Off"), GraphicsSet.REFORGED, 2048, true));
		assertEquals(4096, size(ini("4096", "Off"), GraphicsSet.REFORGED, 2048, true));
	}

	@Test
	void memoryTiers() {
		assertEquals(GraphicsSettings.MemoryTier.UNLIMITED, GraphicsSettings.MemoryTier.of(0));
		assertEquals(GraphicsSettings.MemoryTier.LOW, GraphicsSettings.MemoryTier.of(LOW_MB));
		assertEquals(GraphicsSettings.MemoryTier.MEDIUM, GraphicsSettings.MemoryTier.of(PHONE_MB));
		assertEquals(GraphicsSettings.MemoryTier.MEDIUM, GraphicsSettings.MemoryTier.of(JUST_BELOW_TABLET_MB));
		assertEquals(GraphicsSettings.MemoryTier.HIGH, GraphicsSettings.MemoryTier.of(TABLET_MB));
		assertEquals(GraphicsSettings.MemoryTier.HIGH, GraphicsSettings.MemoryTier.of(S8U_MB));
		// A device with 6 GB and more and a desktop keep every level and have no budget.
		for (final GraphicsSettings.MemoryTier tier : new GraphicsSettings.MemoryTier[] {
				GraphicsSettings.MemoryTier.HIGH, GraphicsSettings.MemoryTier.UNLIMITED }) {
			assertEquals(0, tier.skippedLevels());
			assertEquals(0, tier.textureBudget());
		}
		assertEquals(1, GraphicsSettings.MemoryTier.LOW.skippedLevels());
		assertEquals(0, GraphicsSettings.MemoryTier.MEDIUM.skippedLevels());
		assertTrue(GraphicsSettings.MemoryTier.LOW.textureBudget() < GraphicsSettings.MemoryTier.MEDIUM.textureBudget());
	}

	@Test
	void aNamedTierPlaysAsADeviceOfThatTier() {
		GraphicsSettings.setDeviceMemoryMb(S8U_MB);
		assertEquals(GraphicsSettings.MemoryTier.HIGH, GraphicsSettings.memoryTier());
		System.setProperty(GraphicsSettings.MEMORY_TIER_PROPERTY, "low");
		assertEquals(GraphicsSettings.MemoryTier.LOW, GraphicsSettings.memoryTier());
		assertEquals(512, size(ini("2048", null), GraphicsSet.REFORGED, 512, true));
		System.setProperty(GraphicsSettings.MEMORY_TIER_PROPERTY, "Medium");
		assertEquals(GraphicsSettings.MemoryTier.MEDIUM, GraphicsSettings.memoryTier());
		assertEquals(1024, size(ini("2048", null), GraphicsSet.REFORGED, 512, true));
		System.setProperty(GraphicsSettings.MEMORY_TIER_PROPERTY, "high");
		assertEquals(S8U_MB, GraphicsSettings.deviceMemoryMb());
		// A desktop named high is capped as a device would be.
		GraphicsSettings.setDeviceMemoryMb(0);
		assertEquals(GraphicsSettings.MemoryTier.HIGH, GraphicsSettings.memoryTier());
		System.setProperty(GraphicsSettings.MEMORY_TIER_PROPERTY, "nonsense");
		assertEquals(GraphicsSettings.MemoryTier.UNLIMITED, GraphicsSettings.memoryTier());
	}
}
