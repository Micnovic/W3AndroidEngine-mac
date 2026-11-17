package com.etheller.warsmash.launcher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.etheller.warsmash.datasources.GameLanguage;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.input.touch.ControlScheme;
import com.etheller.warsmash.input.touch.PenButton;
import com.etheller.warsmash.input.touch.TouchStyle;

/**
 * The engine's own settings as the launcher's Options page changes them, kept where the engine reads them: the Options
 * menu values in Options.dat of the current profile (the file and format of GameOptions, below Gdx.files.local), and
 * the touch thresholds and the in-game graphics in the [Touch] and [Graphics] sections of warsmash.ini, the game
 * language in [Language] and the player's content folder in [CustomContent].
 */
public class EngineSettings {
	private static final String TOUCH = "Touch";
	/** The sections the launcher writes, which a new configuration for other game data keeps. */
	private static final String[] OWN_SECTIONS = { TOUCH, GraphicsSettings.SECTION, CustomContent.SECTION,
			GameLanguage.SECTION };

	private final File options;
	private final File ini;
	private final Map<String, Integer> values = new LinkedHashMap<>();

	/**
	 * @param saves   the engine's Save folder
	 * @param profile the current profile, whose Options.dat the engine reads
	 * @param ini     the engine configuration
	 */
	public EngineSettings(final File saves, final String profile, final File ini) {
		this.options = new File(Profiles.folder(saves, profile), "Options.dat");
		this.ini = ini;
		read();
	}

	private void read() {
		if (!this.options.isFile()) {
			return;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(this.options)))) {
			final int count = in.readInt();
			for (int i = 0; i < count; i++) {
				final String key = in.readUTF();
				this.values.put(key, in.readInt());
			}
		}
		catch (final IOException e) {
			LauncherLog.w("Unable to read " + this.options, e);
		}
	}

	/** Defaults are the engine's own, for a value the player never set. */
	public int number(final String key, final int fallback) {
		final Integer value = this.values.get(key);
		return value == null ? fallback : value;
	}

	public boolean flag(final String key, final boolean fallback) {
		final Integer value = this.values.get(key);
		return value == null ? fallback : value != 0;
	}

	public void setNumber(final String key, final int value) {
		this.values.put(key, value);
		save();
	}

	public void setFlag(final String key, final boolean value) {
		setNumber(key, value ? 1 : 0);
	}

	/** Written beside the file first, as the engine does, so that a full disk cannot take the old settings. */
	private void save() {
		final File partial = new File(this.options.getParentFile(), this.options.getName() + ".tmp");
		try {
			this.options.getParentFile().mkdirs();
			try (DataOutputStream out = new DataOutputStream(
					new BufferedOutputStream(new FileOutputStream(partial)))) {
				out.writeInt(this.values.size());
				for (final Map.Entry<String, Integer> entry : this.values.entrySet()) {
					out.writeUTF(entry.getKey());
					out.writeInt(entry.getValue());
				}
			}
			replace(partial, this.options);
		}
		catch (final IOException e) {
			LauncherLog.e("Unable to write " + this.options, e);
			partial.delete();
		}
	}

	/** A [Touch] value of warsmash.ini, or the fallback when the file leaves it out. */
	public String touch(final String key, final String fallback) {
		return iniValue(TOUCH, key, fallback);
	}

	/** Sets a [Touch] value of warsmash.ini, adding the section if the file has none. */
	public void setTouch(final String key, final String value) {
		setIniValue(TOUCH, key, value);
	}

	/** @param platformScheme what the platform plays with when the file names no scheme */
	public ControlScheme controlScheme(final ControlScheme platformScheme) {
		final String named = touch("ControlScheme", "");
		if (named.isEmpty() && "Off".equalsIgnoreCase(touch("TouchControls", ""))) {
			return ControlScheme.MOUSE_KEYBOARD;
		}
		return ControlScheme.parse(named, platformScheme);
	}

	public void setControlScheme(final ControlScheme scheme) {
		setTouch("ControlScheme", scheme.iniValue);
	}

	public PenButton penButton() {
		return PenButton.parse(touch("PenButton", ""), PenButton.SHIFT);
	}

	public void setPenButton(final PenButton button) {
		setTouch("PenButton", button.iniValue);
	}

	public TouchStyle touchStyle() {
		return TouchStyle.parse(touch("TouchScheme", ""), TouchStyle.DOUBLE_TAP);
	}

	public void setTouchStyle(final TouchStyle style) {
		setTouch("TouchScheme", style.iniValue);
	}

	/** A [Graphics] value of warsmash.ini, or the fallback when the file leaves it out. */
	public String graphics(final String key, final String fallback) {
		return iniValue(GraphicsSettings.SECTION, key, fallback);
	}

	public void setGraphics(final String key, final String value) {
		setIniValue(GraphicsSettings.SECTION, key, value);
	}

	/** The game language warsmash.ini chooses, empty when it leaves the choice to the data. */
	public String language() {
		return GameLanguage.canonical(iniValue(GameLanguage.SECTION, GameLanguage.KEY_LOCALE, ""));
	}

	public void setLanguage(final String locale) {
		setIniValue(GameLanguage.SECTION, GameLanguage.KEY_LOCALE, GameLanguage.canonical(locale));
	}

	/** The player's content folder, or null when none is chosen. */
	public File customFolder() {
		return CustomContent.parse(iniValue(CustomContent.SECTION, CustomContent.KEY_FOLDER, ""));
	}

	/** Names the content folder in warsmash.ini; null leaves none. */
	public void setCustomFolder(final File folder) {
		setIniValue(CustomContent.SECTION, CustomContent.KEY_FOLDER, folder == null ? "" : folder.getAbsolutePath());
	}

	private String iniValue(final String name, final String key, final String fallback) {
		String section = null;
		for (final String line : iniLines()) {
			final String trimmed = line.trim();
			if (trimmed.startsWith("[")) {
				section = trimmed;
			}
			else if (isSection(section, name) && trimmed.startsWith(key + "=")) {
				return trimmed.substring(key.length() + 1).trim();
			}
		}
		return fallback;
	}

	private void setIniValue(final String name, final String key, final String value) {
		final List<String> lines = iniLines();
		int section = -1;
		int end = lines.size();
		for (int i = 0; i < lines.size(); i++) {
			final String trimmed = lines.get(i).trim();
			if (trimmed.startsWith("[")) {
				if (section >= 0) {
					end = i;
					break;
				}
				if (isSection(trimmed, name)) {
					section = i;
				}
			}
		}
		final String entry = key + "=" + value;
		if (section < 0) {
			lines.add("");
			lines.add("[" + name + "]");
			lines.add(entry);
		}
		else {
			boolean replaced = false;
			for (int i = section + 1; i < end; i++) {
				if (lines.get(i).trim().startsWith(key + "=")) {
					lines.set(i, entry);
					replaced = true;
				}
			}
			if (!replaced) {
				int at = end;
				while ((at > (section + 1)) && lines.get(at - 1).trim().isEmpty()) {
					at--;
				}
				lines.add(at, entry);
			}
		}
		final File partial = new File(this.ini.getAbsoluteFile().getParentFile(), this.ini.getName() + ".part");
		try {
			Files.write(partial.toPath(), lines, StandardCharsets.UTF_8);
			replace(partial, this.ini);
		}
		catch (final IOException e) {
			LauncherLog.e("Unable to write " + this.ini, e);
			partial.delete();
		}
	}

	/** File.renameTo does not replace an existing file on Windows, which would lose every change after the first. */
	private static void replace(final File partial, final File target) throws IOException {
		Files.move(partial.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	private static boolean isSection(final String header, final String name) {
		return (header != null) && header.toLowerCase(Locale.ROOT).equals("[" + name.toLowerCase(Locale.ROOT) + "]");
	}

	private static boolean isOwnSection(final String header) {
		for (final String name : OWN_SECTIONS) {
			if (isSection(header, name)) {
				return true;
			}
		}
		return false;
	}

	private List<String> iniLines() {
		try {
			return new ArrayList<>(Files.readAllLines(this.ini.toPath(), StandardCharsets.UTF_8));
		}
		catch (final IOException e) {
			LauncherLog.w("Unable to read " + this.ini, e);
			return new ArrayList<>();
		}
	}

	/** The [Touch] and [Graphics] sections of an engine configuration, so that a new configuration can keep them. */
	public static String ownSections(final File ini) {
		if (!ini.isFile()) {
			return "";
		}
		final StringBuilder section = new StringBuilder();
		boolean inside = false;
		try {
			for (final String line : Files.readAllLines(ini.toPath(), StandardCharsets.UTF_8)) {
				final String trimmed = line.trim();
				if (trimmed.startsWith("[")) {
					inside = isOwnSection(trimmed);
				}
				if (inside && !trimmed.isEmpty()) {
					section.append(line).append('\n');
				}
			}
		}
		catch (final IOException e) {
			LauncherLog.w("Unable to read " + ini, e);
			return "";
		}
		return section.toString();
	}
}
