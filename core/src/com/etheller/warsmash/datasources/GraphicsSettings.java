package com.etheller.warsmash.datasources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.viewer5.handlers.w3x.CampaignMaps;

/**
 * The [Graphics] section of the engine configuration, which both launchers write from their Options page: the in-game
 * graphics set of custom games and the texture size limit. A campaign mission is played with the graphics of the
 * campaign version it was started in, whatever the set. The menu skin (Classic or Reforged) is the launcher's own
 * choice and changes only the menus.
 */
public final class GraphicsSettings {
	public static final String SECTION = "Graphics";
	public static final String KEY_SET = "Set";
	public static final String KEY_TEXTURE_SIZE = "TextureSize";
	/** "Auto" (the default: compressed on phones and tablets), "On" or "Off"; see {@link #textureCompression}. */
	public static final String KEY_TEXTURE_COMPRESSION = "TextureCompression";
	/**
	 * "Auto" (the default: the shape of each unit's model on the ground, as the Reforged graphics draw it),
	 * "Full" (always) or "Blob" (the round shadow of the Classic graphics).
	 */
	public static final String KEY_UNIT_SHADOWS = "UnitShadows";
	/** The part of the screen's resolution the world is drawn at, in percent; the interface is always at full size. */
	public static final String KEY_RENDER_SCALE = "RenderScale";
	/** The render scales offered, in the order the launchers list them. */
	public static final int[] RENDER_SCALES = { 100, 85, 70, 50 };
	public static final int MIN_RENDER_SCALE = 50;
	/** The texture size limits offered, in the order the launchers list them; 0 is no limit. */
	public static final int[] TEXTURE_SIZES = { 512, 1024, 2048, 0 };
	/** Below this much memory a tablet is treated as a phone. */
	private static final long TABLET_MEMORY_MB = 6 * 1024;
	/** A GPU with less memory of its own gets the smaller default limit. */
	private static final long LOW_VIDEO_MEMORY_MB = 3 * 1024;
	private static final String[] SOFTWARE_RENDERERS = { "llvmpipe", "softpipe", "swiftshader", "microsoft basic",
			"gdi generic" };
	/** Memory a mobile GPU shares with the system, from which a device's largest texture size follows; 0 where unlimited. */
	private static volatile long deviceMemoryMb;
	private static final String LOCALES = "\\_locales\\";

	private GraphicsSettings() {
	}

	public static GraphicsSet graphicsSet(final DataTable ini) {
		final Element section = ini == null ? null : ini.get(SECTION);
		return GraphicsSet.fromIniName(section == null ? null : section.getField(KEY_SET));
	}

	/** The texture size limit the configuration sets, or -1 when it sets none. */
	public static int configuredTextureSize(final DataTable ini) {
		final Element section = ini == null ? null : ini.get(SECTION);
		final String value = section == null ? "" : section.getField(KEY_TEXTURE_SIZE).trim();
		if (value.isEmpty()) {
			return -1;
		}
		try {
			return Math.max(0, Integer.parseInt(value));
		}
		catch (final NumberFormatException e) {
			return -1;
		}
	}

	/** The texture size limit to play with; without a setting no limit. */
	public static int textureSize(final DataTable ini) {
		return Math.max(0, configuredTextureSize(ini));
	}

	/**
	 * The texture size limit of a game with these graphics: the configured one, else the {@code defaultSize} for the HD
	 * and DE graphics, whose textures are four times the size of Classic's, and no limit for Classic.
	 */
	public static int textureSize(final DataTable ini, final GraphicsSet graphics,
			final IntSupplier defaultSize) {
		final int configured = configuredTextureSize(ini);
		if (configured >= 0) {
			return graphics == GraphicsSet.CLASSIC ? configured
					: limitToCap(configured, textureCap(deviceMemoryMb, compressionWanted(ini, true)));
		}
		return graphics == GraphicsSet.CLASSIC ? 0 : Math.max(0, defaultSize.getAsInt());
	}

	/** Tells the settings how much memory the device has, so that texture sizes it cannot hold are lowered. */
	public static void setDeviceMemoryMb(final long memoryMb) {
		deviceMemoryMb = memoryMb;
	}

	public static long deviceMemoryMb() {
		return deviceMemoryMb;
	}

	/**
	 * The largest texture size a mobile device with this memory loads without being killed for memory, 0 for no limit
	 * (no memory known). Textures stay in memory the GPU shares with the system: uncompressed they take four bytes a
	 * pixel, so a size one step up needs about four times as much; as ETC2 blocks they take a fourth.
	 */
	public static int textureCap(final long memoryMb, final boolean compressed) {
		if (memoryMb <= 0) {
			return 0;
		}
		if (memoryMb < TABLET_MEMORY_MB) {
			return compressed ? 1024 : 512;
		}
		return compressed ? 2048 : 1024;
	}

	/** A texture size (0 is no limit) lowered to a cap (0 is no cap). */
	public static int limitToCap(final int size, final int cap) {
		return (cap > 0) && ((size == 0) || (size > cap)) ? cap : size;
	}

	/** Whether the stored TextureCompression value turns ETC2 on, as {@link #textureCompression} reads it. */
	public static boolean compressionWanted(final String value, final boolean mobile) {
		final String v = value == null ? "" : value.trim();
		if (v.equalsIgnoreCase("off") || v.equalsIgnoreCase("false") || v.equals("0")) {
			return false;
		}
		if (v.equalsIgnoreCase("on") || v.equalsIgnoreCase("true") || v.equals("1")) {
			return true;
		}
		return mobile;
	}

	private static boolean compressionWanted(final DataTable ini, final boolean mobile) {
		final Element section = ini == null ? null : ini.get(SECTION);
		return compressionWanted(section == null ? "" : section.getField(KEY_TEXTURE_COMPRESSION), mobile);
	}

	/**
	 * The default limit of a desktop for the HD and DE graphics: 1024 on a GPU that shares the system memory or has
	 * little of its own, else 2048; a full-size texture set takes several gigabytes. A GPU that cannot be told apart
	 * from its name or memory gets 2048.
	 *
	 * @param renderer the OpenGL renderer string, may be null
	 * @param dedicatedMb the memory of the GPU in megabytes when the driver tells it, else 0
	 */
	public static int desktopDefaultTextureSize(final String renderer, final long dedicatedMb) {
		if (dedicatedMb > 0) {
			return dedicatedMb < LOW_VIDEO_MEMORY_MB ? 1024 : 2048;
		}
		if (renderer == null) {
			return 2048;
		}
		final String name = renderer.toLowerCase(Locale.ROOT);
		for (final String software : SOFTWARE_RENDERERS) {
			if (name.contains(software)) {
				return 1024;
			}
		}
		final boolean intel = name.contains("intel") && !name.matches(".*arc\\(tm\\) [ab]\\d{3}.*");
		final boolean radeonIntegrated = name.contains("radeon") && name.trim().endsWith("graphics")
				&& !name.contains(" rx ");
		return intel || radeonIntegrated ? 1024 : 2048;
	}

	/**
	 * The default limit of a device: 512 on phones, 1024 on tablets with enough memory, none on desktops. The game
	 * data are block compressed in formats Android GPUs lack, so a texture is decoded to four bytes a pixel unless
	 * {@link #textureCompression} re-encodes it to ETC2.
	 */
	public static int defaultTextureSize(final boolean mobile, final boolean tablet, final long memoryMb) {
		if (!mobile) {
			return 0;
		}
		return tablet && (memoryMb >= TABLET_MEMORY_MB) ? 1024 : 512;
	}

	/**
	 * Whether the model textures of a game are uploaded as ETC2 blocks. Only the HD and DE graphics are, Classic keeps
	 * its textures as they are. Mobile GPUs read the blocks directly, so "Auto" turns it on there; desktop drivers
	 * mostly expand them again, which saves nothing, so it needs "On".
	 *
	 * @param etc2Supported whether the GPU reads ETC2 (every OpenGL ES 3.0 one does)
	 */
	public static boolean textureCompression(final DataTable ini, final GraphicsSet graphics, final boolean mobile,
			final boolean etc2Supported) {
		if ((graphics == GraphicsSet.CLASSIC) || !etc2Supported) {
			return false;
		}
		return compressionWanted(ini, mobile);
	}

	/** Whether units cast the shape of their models onto the ground, rather than a round blob. */
	public static boolean modelUnitShadows(final DataTable ini, final GraphicsSet graphics, final boolean mobile) {
		final Element section = ini == null ? null : ini.get(SECTION);
		final String value = section == null ? "" : section.getField(KEY_UNIT_SHADOWS).trim();
		if (value.equalsIgnoreCase("Full")) {
			return true;
		}
		if (value.equalsIgnoreCase("Blob")) {
			return false;
		}
		return true;
	}

	/** The render scale of the configuration in percent, 100 (full size) when it sets none or something unreadable. */
	public static int renderScalePercent(final DataTable ini) {
		final Element section = ini == null ? null : ini.get(SECTION);
		return renderScalePercent(section == null ? "" : section.getField(KEY_RENDER_SCALE));
	}

	/** The render scale a stored value names, in percent: 100 for none or something unreadable. */
	public static int renderScalePercent(final String stored) {
		final String value = stored.trim();
		if (value.endsWith("%")) {
			return parseRenderScale(value.substring(0, value.length() - 1));
		}
		return parseRenderScale(value);
	}

	/** Sets the render scale (percent) of a configuration, for this run only. */
	public static void setRenderScalePercent(final DataTable ini, final int percent) {
		section(ini).setField(KEY_RENDER_SCALE, Integer.toString(Math.max(MIN_RENDER_SCALE, Math.min(100, percent))));
	}

	private static int parseRenderScale(final String text) {
		try {
			return Math.max(MIN_RENDER_SCALE, Math.min(100, Integer.parseInt(text.trim())));
		}
		catch (final NumberFormatException e) {
			return 100;
		}
	}

	/** The part of the screen's resolution the world is drawn at, from 0.5 to 1. */
	public static float renderScale(final DataTable ini) {
		return renderScalePercent(ini) / 100f;
	}

	/** Puts a default texture size limit into a configuration that sets none, for this run only. */
	public static void applyDefaultTextureSize(final DataTable ini, final int textureSize) {
		if ((ini == null) || (configuredTextureSize(ini) >= 0)) {
			return;
		}
		section(ini).setField(KEY_TEXTURE_SIZE, Integer.toString(textureSize));
	}

	/**
	 * Sets the graphics of a configuration, for this run only. A negative texture size leaves the limit as the
	 * configuration has it, which may be none, so that {@link #textureSize(DataTable, GraphicsSet, IntSupplier)} still
	 * chooses a default.
	 */
	public static void apply(final DataTable ini, final GraphicsSet graphics, final int textureSize) {
		final Element section = section(ini);
		section.setField(KEY_SET, graphics.getIniName());
		if (textureSize >= 0) {
			section.setField(KEY_TEXTURE_SIZE, Integer.toString(textureSize));
		}
	}

	/** Sets the graphics and the render scale (percent) of a configuration, for this run only; see the above. */
	public static void apply(final DataTable ini, final GraphicsSet graphics, final int textureSize,
			final int renderScalePercent) {
		apply(ini, graphics, textureSize);
		setRenderScalePercent(ini, renderScalePercent);
	}

	/** The choice of a render scale in {@link #RENDER_SCALES}, the nearest one for a value the launcher does not offer. */
	public static int renderScaleChoice(final int percent) {
		int best = 0;
		for (int i = 1; i < RENDER_SCALES.length; i++) {
			if (Math.abs(RENDER_SCALES[i] - percent) < Math.abs(RENDER_SCALES[best] - percent)) {
				best = i;
			}
		}
		return best;
	}

	private static Element section(final DataTable ini) {
		Element section = ini.get(SECTION);
		if (section == null) {
			section = new Element(SECTION, ini);
			ini.put(SECTION, section);
		}
		return section;
	}

	/** The language folder of the configured data ("enus" in "war3.w3mod\_locales\enus.w3mod"). */
	public static String locale(final List<String> prefixes) {
		for (final String prefix : prefixes) {
			final String lower = prefix.trim().toLowerCase(Locale.US).replace('/', '\\');
			final int at = lower.indexOf(LOCALES);
			if ((at >= 0) && lower.endsWith(".w3mod")) {
				return lower.substring(at + LOCALES.length(), lower.length() - ".w3mod".length());
			}
		}
		return "enus";
	}

	/**
	 * The graphics a game is played with, its models, icons and interface alike; the menu skin has no say in them. A
	 * campaign mission has those of its campaign version, any other map those the options set for custom games. A map
	 * made for the Reforged graphics alone is never played in Classic.
	 */
	public static GraphicsSet gameGraphics(final DataTable ini, final String mapFilename, final boolean reforgedCampaign,
			final boolean reforgedMap) {
		if (CampaignMaps.isCampaignMap(mapFilename)) {
			return reforgedCampaign || reforgedMap ? GraphicsSet.REFORGED : GraphicsSet.CLASSIC;
		}
		final GraphicsSet custom = graphicsSet(ini);
		return reforgedMap && (custom == GraphicsSet.CLASSIC) ? GraphicsSet.REFORGED : custom;
	}

	/** The game data as a game with these graphics reads it, its models, icons and interface alike. */
	public static DataSource gameData(final DataSource data, final DataTable ini, final GraphicsSet graphics) {
		return LayeredDataSource.wrap(data, graphics, languages(ini));
	}

	/** The language folders of the graphics packages, the chosen language before the one the data is configured with. */
	private static String[] languages(final DataTable ini) {
		final String base = locale(ini);
		final String chosen = GameLanguage.configured(ini).toLowerCase(Locale.ROOT);
		return chosen.isEmpty() || chosen.equals(base) ? new String[] { base } : new String[] { chosen, base };
	}

	private static String locale(final DataTable ini) {
		final Element sources = ini == null ? null : ini.get("DataSources");
		if (sources == null) {
			return "enus";
		}
		final List<String> prefixes = new ArrayList<>();
		for (int i = 0; i < sources.size(); i++) {
			final String prefixField = sources.getField(String.format(Locale.ROOT, "Prefixes%02d", i));
			if (!prefixField.isEmpty()) {
				prefixes.addAll(Arrays.asList(prefixField.split(",")));
			}
		}
		return locale(prefixes);
	}
}
