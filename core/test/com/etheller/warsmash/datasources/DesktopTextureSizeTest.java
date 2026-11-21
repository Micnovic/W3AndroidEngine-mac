package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.launcher.LauncherOptions;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.StringBundle;

class DesktopTextureSizeTest {
	@Test
	void sharedMemoryAndSmallGpusGetTheSmallerDefault() {
		assertEquals(1024, GraphicsSettings.desktopDefaultTextureSize("Intel(R) UHD Graphics 630", 0));
		assertEquals(1024, GraphicsSettings.desktopDefaultTextureSize("Mesa Intel(R) Iris(R) Xe Graphics (TGL GT2)", 0));
		assertEquals(1024, GraphicsSettings.desktopDefaultTextureSize("Intel(R) Arc(TM) Graphics", 0));
		assertEquals(1024, GraphicsSettings.desktopDefaultTextureSize("AMD Radeon(TM) Graphics", 0));
		assertEquals(1024, GraphicsSettings.desktopDefaultTextureSize("AMD Radeon(TM) Vega 8 Graphics", 0));
		assertEquals(1024, GraphicsSettings.desktopDefaultTextureSize("llvmpipe (LLVM 15.0.7, 256 bits)", 0));
		assertEquals(1024, GraphicsSettings.desktopDefaultTextureSize("NVIDIA GeForce GTX 1050", 2048));
	}

	@Test
	void discreteAndUnknownGpusGet2048() {
		assertEquals(2048, GraphicsSettings.desktopDefaultTextureSize("NVIDIA GeForce RTX 3060/PCIe/SSE2", 0));
		assertEquals(2048, GraphicsSettings.desktopDefaultTextureSize("NVIDIA GeForce RTX 3060", 12288));
		assertEquals(2048, GraphicsSettings.desktopDefaultTextureSize("AMD Radeon RX 6700 XT", 0));
		assertEquals(2048, GraphicsSettings.desktopDefaultTextureSize("Intel(R) Arc(TM) A770 Graphics", 0));
		assertEquals(2048, GraphicsSettings.desktopDefaultTextureSize("Intel(R) Arc(TM) A380 Graphics", 6144));
		assertEquals(2048, GraphicsSettings.desktopDefaultTextureSize("Apple M2", 0));
		assertEquals(2048, GraphicsSettings.desktopDefaultTextureSize(null, 0));
		assertEquals(2048, GraphicsSettings.desktopDefaultTextureSize("", 0));
	}

	@Test
	void theDefaultAppliesToHdAndDeWithoutAChoice() {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		assertEquals(0, GraphicsSettings.textureSize(ini, GraphicsSet.CLASSIC, () -> 2048));
		assertEquals(2048, GraphicsSettings.textureSize(ini, GraphicsSet.REFORGED, () -> 2048));
		assertEquals(1024, GraphicsSettings.textureSize(ini, GraphicsSet.DE, () -> 1024));
		// A launcher that has no choice leaves the limit unset.
		GraphicsSettings.apply(ini, GraphicsSet.DE, -1);
		assertEquals(-1, GraphicsSettings.configuredTextureSize(ini));
		assertEquals(2048, GraphicsSettings.textureSize(ini, GraphicsSet.DE, () -> 2048));
	}

	@Test
	void aDeviceDefaultLimitsHdAndDeButNotClassic() {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		GraphicsSettings.applyDefaultTextureSize(ini, 512);
		GraphicsSettings.applyDefaultTextureSize(ini, 1024);
		assertEquals(-1, GraphicsSettings.configuredTextureSize(ini));
		assertEquals(0, GraphicsSettings.textureSize(ini, GraphicsSet.CLASSIC, () -> 2048));
		assertEquals(512, GraphicsSettings.textureSize(ini, GraphicsSet.DE, () -> 2048));
		GraphicsSettings.apply(ini, GraphicsSet.CLASSIC, 1024);
		assertEquals(1024, GraphicsSettings.textureSize(ini, GraphicsSet.CLASSIC, () -> 2048));
		assertEquals(1024, GraphicsSettings.textureSize(ini, GraphicsSet.DE, () -> 2048));
	}

	@Test
	void aChoiceAlwaysWins() {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		GraphicsSettings.apply(ini, GraphicsSet.DE, 0);
		assertEquals(0, GraphicsSettings.textureSize(ini, GraphicsSet.DE, () -> {
			throw new AssertionError("the default is not asked for");
		}));
		GraphicsSettings.apply(ini, GraphicsSet.DE, 512);
		assertEquals(512, GraphicsSettings.textureSize(ini, GraphicsSet.DE, () -> 2048));
		GraphicsSettings.apply(ini, GraphicsSet.DE, -1);
		assertEquals(512, GraphicsSettings.configuredTextureSize(ini));
	}

	@Test
	void theOptionsOfferEveryDefault() {
		assertEquals(GraphicsSettings.TEXTURE_SIZES.length, LauncherOptions.TEXTURE_SIZES.length);
		assertEquals(1, LauncherOptions.textureSizeChoice(1024));
		assertEquals(2, LauncherOptions.textureSizeChoice(2048));
		assertEquals(3, LauncherOptions.textureSizeChoice(0));
	}
}
