package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.LauncherKeys.KEY_ANIMATED;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_CRASH_LOGS;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_MUSIC;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_SKIN;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_UPDATE_CHECK;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_UPDATE_SKIPPED;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.etheller.warsmash.datasources.GameLanguage;
import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.input.touch.ControlScheme;
import com.etheller.warsmash.input.touch.PenButton;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.input.touch.TouchStyle;
import com.etheller.warsmash.launcher.DataFolderCheck;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.LauncherOptions;
import com.etheller.warsmash.launcher.SkinArt;

/** The launcher's own Options: its tabs of settings, most of them the engine's. */
final class OptionsPage extends LauncherViews {
	private boolean sharingLogs;
	static final int TAB_GRAPHICS = 0;
	private static final int TAB_SOUND = 1;
	private static final int TAB_GAMEPLAY = 2;
	private static final int TAB_TOUCH = 3;
	static final int TAB_GAME_DATA = 4;
	private static final int TAB_ABOUT = 5;

	private static final long HINT_MILLIS = 3000;

	int optionsTab = TAB_GRAPHICS;
	private TextView hint;
	final HotkeyEditorPage hotkeys;

	OptionsPage(final LauncherActivity activity) {
		super(activity);
		this.hotkeys = new HotkeyEditorPage(activity, this);
	}

	void showOptions(final int tab) {
		this.optionsTab = tab;
		final LauncherBoard b = this.activity.board;
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_OPTIONS);
		final FrameLayout content = content(view, 0);
		final float top = addTabs(content, new int[] { R.string.options_graphics, R.string.options_sound,
			R.string.options_gameplay, R.string.options_controls, R.string.options_game_data, R.string.options_about },
				tab, this::showOptions);
		final FrameLayout panel = framed(content, 0, top, b.contentW, b.contentH - top);
		final ScrollView scroll = new ScrollView(this.activity);
		scroll.setVerticalScrollBarEnabled(true);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		final LinearLayout rows = new LinearLayout(this.activity);
		rows.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(rows, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));
		place(panel, scroll, b.pageFrame, b.pageFrame, b.contentW - (2 * b.pageFrame),
				b.contentH - top - (2 * b.pageFrame));
		final EngineSettings engine = new EngineSettings(this.activity);
		switch (tab) {
		case TAB_SOUND:
			soundRows(rows, engine);
			break;
		case TAB_GAMEPLAY:
			gameplayRows(rows, engine);
			break;
		case TAB_TOUCH:
			touchRows(rows, engine);
			break;
		case TAB_GAME_DATA:
			gameDataRows(rows);
			break;
		case TAB_ABOUT:
			aboutRows(rows);
			break;
		default:
			graphicsRows(rows, engine);
			break;
		}
		if (b.tablet) {
			// The tablet's panel is as high as its rows, as the design draws it; a longer tab scrolls.
			int rowsH = 0;
			for (int i = 0; i < rows.getChildCount(); i++) {
				final int h = rows.getChildAt(i).getLayoutParams().height;
				rowsH = (h < 0) || (rowsH < 0) ? -1 : rowsH + h;
			}
			if (rowsH >= 0) {
				final int panelH = Math.min(px(b.contentH - top), rowsH + px(2 * b.pageFrame));
				panel.getLayoutParams().height = panelH;
				scroll.getLayoutParams().height = panelH - px(2 * b.pageFrame);
			}
		}
		place(view, button(getString(R.string.launcher_back), b.backH, b.tablet ? 36 : 30,
				v -> this.activity.homePage.showHome()), b.backX,
				b.backY, b.backW, b.backH);
		this.activity.setPage(view, LauncherActivity.PAGE_OPTIONS);
	}

	private void graphicsRows(final LinearLayout rows, final EngineSettings engine) {
		final boolean reforgedHere = SkinCache.hasReforged(this.activity);
		radioRow(rows, R.string.options_interface,
				new String[] { getString(R.string.launcher_classic),
					getString(reforgedHere ? R.string.launcher_reforged : R.string.options_reforged_locked) },
				new boolean[] { true, reforgedHere }, this.activity.cache.reforged() ? 1 : 0,
				choice -> chooseSkin(choice == 1), getString(R.string.options_reforged_locked_note));
		final GraphicsSet[] sets = GraphicsSet.values();
		final String[] setNames = new String[sets.length];
		final boolean[] setsHere = new boolean[sets.length];
		for (int i = 0; i < sets.length; i++) {
			setsHere[i] = SkinCache.hasGraphics(this.activity, sets[i]);
			setNames[i] = getString(stringId(LauncherOptions.GRAPHICS_SETS[i][setsHere[i] ? 0 : 1]));
		}
		// The game falls back to Classic when the data lacks the chosen package, and so does the row.
		final int setChoice = GraphicsSet.fromIniName(engine.graphics(GraphicsSettings.KEY_SET, "")).ordinal();
		radioRow(rows, R.string.options_ingame_graphics, setNames, setsHere, setsHere[setChoice] ? setChoice : 0,
				choice -> engine.setGraphics(GraphicsSettings.KEY_SET, sets[choice].getIniName()),
				getString(R.string.options_graphics_locked_note));
		final String[] sizeNames = new String[LauncherOptions.TEXTURE_SIZES.length];
		for (int i = 0; i < sizeNames.length; i++) {
			sizeNames[i] = getString(stringId(LauncherOptions.TEXTURE_SIZES[i]));
		}
		int textureSize;
		try {
			textureSize = Integer.parseInt(engine.graphics(GraphicsSettings.KEY_TEXTURE_SIZE, ""));
		}
		catch (final NumberFormatException e) {
			textureSize = AndroidLauncher.defaultTextureSize(this.activity);
		}
		// Sizes the device's memory cannot hold are greyed out; one already stored is lowered while playing, and says so.
		final int cap = GraphicsSettings.textureCap(AndroidLauncher.totalMemoryMb(this.activity), GraphicsSettings
				.compressionWanted(engine.graphics(GraphicsSettings.KEY_TEXTURE_COMPRESSION, ""), true));
		final boolean[] sizesHere = new boolean[sizeNames.length];
		for (int i = 0; i < sizesHere.length; i++) {
			sizesHere[i] = GraphicsSettings.limitToCap(GraphicsSettings.TEXTURE_SIZES[i], cap) == GraphicsSettings.TEXTURE_SIZES[i];
		}
		// What the game will use is the one shown as chosen, so a stored size above the cap does not leave the row unmarked.
		radioRow(rows, R.string.options_texture_size, sizeNames, cap > 0 ? sizesHere : null,
				LauncherOptions.textureSizeChoice(GraphicsSettings.limitToCap(textureSize, cap)),
				choice -> engine.setGraphics(GraphicsSettings.KEY_TEXTURE_SIZE,
						Integer.toString(GraphicsSettings.TEXTURE_SIZES[choice])),
				getString(R.string.options_texture_capped, cap));
		if ((cap > 0) && (GraphicsSettings.limitToCap(textureSize, cap) != textureSize)) {
			noteRow(rows, getString(R.string.options_texture_limited,
					textureSize == 0 ? getString(R.string.options_texture_full) : Integer.toString(textureSize), cap));
		}
		final String[] scaleNames = new String[LauncherOptions.RENDER_SCALES.length];
		for (int i = 0; i < scaleNames.length; i++) {
			scaleNames[i] = getString(stringId(LauncherOptions.RENDER_SCALES[i]));
		}
		int scalePercent;
		try {
			scalePercent = Integer
					.parseInt(engine.graphics(GraphicsSettings.KEY_RENDER_SCALE, "100").replace("%", "").trim());
		}
		catch (final NumberFormatException e) {
			scalePercent = 100;
		}
		radioRow(rows, R.string.options_render_scale, scaleNames, null,
				GraphicsSettings.renderScaleChoice(scalePercent),
				choice -> engine.setGraphics(GraphicsSettings.KEY_RENDER_SCALE,
						Integer.toString(GraphicsSettings.RENDER_SCALES[choice])));
		// Android always syncs to the screen, so the way to spare the device is to draw fewer frames.
		final String[] capNames = new String[LauncherOptions.FRAME_CAP_NAMES.length];
		for (int i = 0; i < capNames.length; i++) {
			capNames[i] = getString(stringId(LauncherOptions.FRAME_CAP_NAMES[i]));
		}
		radioRow(rows, R.string.options_frame_cap, capNames, null,
				LauncherOptions.frameCapChoice(engine.number(LauncherOptions.KEY_FRAME_CAP, 0)),
				choice -> engine.setNumber(LauncherOptions.KEY_FRAME_CAP, LauncherOptions.FRAME_CAPS[choice]));
		checkRow(rows, R.string.options_animated, this.activity.preferences.getBoolean(KEY_ANIMATED, true),
				on -> this.activity.preferences.edit().putBoolean(KEY_ANIMATED, on).apply());
		engineRows(rows, engine, LauncherOptions.GRAPHICS);
	}

	/** Rows of engine settings as the shared table lists them. */
	private void engineRows(final LinearLayout rows, final EngineSettings engine,
			final List<LauncherOptions.Row> table) {
		for (final LauncherOptions.Row row : table) {
			final int label = stringId(row.label);
			switch (row.kind) {
			case CHECK:
				checkRow(rows, label, row.value(engine) != 0, on -> row.set(engine, on ? 1 : 0));
				break;
			case RADIO: {
				final String[] choices = new String[row.choices.size()];
				for (int i = 0; i < choices.length; i++) {
					choices[i] = getString(stringId(row.choices.get(i)));
				}
				radioRow(rows, label, choices, null, row.value(engine), choice -> row.set(engine, choice));
				break;
			}
			default:
				sliderRow(rows, label, row.value(engine), getString(stringId(row.choices.get(0))),
						getString(stringId(row.choices.get(1))), (value, done) -> {
							if (done) {
								row.set(engine, value);
							}
						});
				break;
			}
		}
	}

	int stringId(final String name) {
		return this.activity.getResources().getIdentifier(name, "string", this.activity.getPackageName());
	}

	/** The engine's sound settings; their defaults are the engine's (music at 40, as Warcraft III has it). */
	private void soundRows(final LinearLayout rows, final EngineSettings engine) {
		engineRows(rows, engine, LauncherOptions.SOUND);
		checkRow(rows, R.string.options_menu_music, this.activity.preferences.getBoolean(KEY_MUSIC, true), on -> {
			this.activity.preferences.edit().putBoolean(KEY_MUSIC, on).apply();
			this.activity.updateMusic();
		});
	}

	private void gameplayRows(final LinearLayout rows, final EngineSettings engine) {
		engineRows(rows, engine, LauncherOptions.GAMEPLAY);
		infoRow(rows, R.string.options_profile, this.activity.profiles.current(), false);
		final View profileRow = rows.getChildAt(rows.getChildCount() - 1);
		onTap(profileRow, false, v -> this.activity.profilePage.showProfile(null));
		profileRow.setContentDescription(getString(R.string.options_profile));
	}

	/** The [Touch] keys of warsmash.ini with the engine's defaults and a sensible range around them. */
	private void touchRows(final LinearLayout rows, final EngineSettings engine) {
		final boolean mouseScheme = engine.controlScheme(ControlScheme.TOUCH) == ControlScheme.MOUSE_KEYBOARD;
		radioRow(rows, R.string.options_control_scheme,
				new String[] { getString(R.string.options_scheme_mouse), getString(R.string.options_scheme_touch) },
				null, engine.controlScheme(ControlScheme.TOUCH).ordinal(),
				choice -> {
					engine.setControlScheme(ControlScheme.values()[choice]);
					final TouchInputRouter router = TouchInputRouter.installedRouter();
					if (router != null) {
						router.setScheme(ControlScheme.values()[choice]);
					}
					com.etheller.warsmash.lan.LanSessions.onControlSchemeChanged(ControlScheme.values()[choice]);
					// The hotkey editor below the rows belongs to the keyboard and mouse scheme only.
					showOptions(TAB_TOUCH);
				});
		if (mouseScheme) {
			noteRow(rows, R.string.options_mouse_note);
		}
		radioRow(rows, mouseScheme ? R.string.options_touch_style_pen : R.string.options_touch_style,
				new String[] { getString(R.string.options_style_hold), getString(R.string.options_style_double) },
				null, engine.touchStyle().ordinal(), choice -> {
					final TouchStyle style = TouchStyle.values()[choice];
					engine.setTouchStyle(style);
					final TouchInputRouter router = TouchInputRouter.installedRouter();
					if (router != null) {
						router.setStyle(style);
					}
				});
		radioRow(rows, R.string.options_pen_button,
				new String[] { getString(R.string.options_pen_shift), getString(R.string.options_pen_ctrl),
					getString(R.string.options_pen_drag), getString(R.string.options_pen_right) },
				null, engine.penButton().ordinal(), choice -> {
					final PenButton button = PenButton.values()[choice];
					engine.setPenButton(button);
					final TouchInputRouter router = TouchInputRouter.installedRouter();
					if (router != null) {
						router.setPenButton(button);
					}
				});
		touchRow(rows, R.string.options_tap_slop, engine, "TapSlopDp", 10, 4, 24, R.string.options_dp);
		touchRow(rows, R.string.options_long_press, engine, "LongPressMs", 300, 200, 800, R.string.options_ms);
		checkRow(rows, R.string.options_palm_on, !"Off".equalsIgnoreCase(engine.touch("PalmRejection", "")),
				on -> engine.setTouch("PalmRejection", on ? "Auto" : "Off"));
		touchRow(rows, R.string.options_palm, engine, "PalmTouchMajorDp", 96, 20, 400, R.string.options_dp);
		touchRow(rows, R.string.options_tap_reach, engine, "GhostTapRadiusDp", 48, 16, 96, R.string.options_dp);
		checkRow(rows, R.string.options_pen_fingers, "Allow".equalsIgnoreCase(engine.touch("PenFingerPolicy", "")),
				on -> engine.setTouch("PenFingerPolicy", on ? "Allow" : "Ignore"));
		if (mouseScheme) {
			this.hotkeys.addTo(rows);
		}
	}

	/** A line of explanation across the whole row, for settings that mean something else under the other scheme. */
	private void noteRow(final LinearLayout rows, final int note) {
		noteRow(rows, getString(note));
	}

	private void noteRow(final LinearLayout rows, final String note) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = row(rows);
		row.getLayoutParams().height = px(b.rowH * 1.6f);
		final TextView shown = text(note, b.small, this.activity.art.text());
		shown.setGravity(Gravity.CENTER_VERTICAL);
		shown.setSingleLine(false);
		shown.setMaxLines(3);
		shown.setEllipsize(null);
		place(row, shown, b.labelX, 0, b.contentW - (2 * b.pageFrame) - (2 * b.labelX), b.rowH * 1.6f);
	}

	private void touchRow(final LinearLayout rows, final int label, final EngineSettings engine, final String key,
			final int fallback, final int min, final int max, final int unit) {
		int current;
		try {
			current = Math.round(Float.parseFloat(engine.touch(key, Integer.toString(fallback))));
		}
		catch (final NumberFormatException e) {
			current = fallback;
		}
		current = Math.max(min, Math.min(max, current));
		final TextView shown = text(getString(unit, current), this.activity.board.small, this.activity.art.text());
		sliderRow(rows, label, Math.round(((current - min) * 100f) / (max - min)), null, null, (value, done) -> {
			final int next = min + Math.round(((max - min) * value) / 100f);
			shown.setText(getString(unit, next));
			if (done) {
				engine.setTouch(key, Integer.toString(next));
			}
		}, shown);
	}

	private void gameDataRows(final LinearLayout rows) {
		infoRow(rows, R.string.options_source,
				getString(this.activity.homePage.installedGame() ? R.string.options_source_casc
						: R.string.options_source_folder),
				false);
		infoRow(rows, R.string.options_folder, this.activity.homePage.dataFolder(), true);
		final String version = this.activity.homePage.dataVersion();
		infoRow(rows, R.string.options_version, version.isEmpty() ? getString(R.string.options_version_unknown)
				: version.trim().replace("(", "").replace(")", ""), false);
		final DataFolderCheck.Kind kind = this.activity.homePage.installedGame() ? DataFolderCheck.Kind.CASC
				: DataFolderCheck.Kind.FOLDER;
		final File folder = new File(this.activity.homePage.dataFolder());
		final String locale = DataFolderCheck.locale(kind, folder);
		final List<String> languages = DataFolderCheck.locales(kind, folder);
		if (languages.size() > 1) {
			final String[] names = new String[languages.size()];
			for (int i = 0; i < names.length; i++) {
				names[i] = GameLanguage.displayName(languages.get(i));
			}
			final EngineSettings engine = new EngineSettings(this.activity);
			final String chosen = engine.language().isEmpty() ? locale : engine.language();
			radioRow(rows, R.string.options_language, names, null, Math.max(0, languages.indexOf(chosen)),
					choice -> engine.setLanguage(languages.get(choice)));
			noteRow(rows, getString(R.string.options_language_note));
		}
		else {
			infoRow(rows, R.string.options_locale, locale.isEmpty() ? getString(R.string.options_not_stated) : locale,
					false);
		}
		if (!this.activity.homePage.installedGame()) {
			final String files = DataFolderCheck.describe(kind, folder);
			final TextView shown = infoRow(rows, R.string.options_files, files, false);
			final DataFolderCheck.Readability known = DataFolderCheck.lastReadability(folder);
			if (known != null) {
				shown.setText(filesText(files, known));
			}
			else {
				new Thread(() -> {
					final DataFolderCheck.Readability found = DataFolderCheck.readability(folder, Integer.MAX_VALUE,
							0);
					this.activity.runOnUiThread(() -> shown.setText(filesText(files, found)));
				}, "DataFolderReadability").start();
			}
			final String packer = DataFolderCheck.packerTool(folder);
			infoRow(rows, R.string.options_packer, packer.isEmpty() ? getString(R.string.options_not_stated) : packer,
					false);
		}
		infoRow(rows, R.string.options_reforged_ui,
				getString(SkinCache.hasReforged(this.activity) ? R.string.options_found : R.string.options_missing),
				false);
		final boolean hd = SkinCache.hasGraphics(this.activity, GraphicsSet.REFORGED);
		final boolean de = SkinCache.hasGraphics(this.activity, GraphicsSet.DE);
		infoRow(rows, R.string.options_hd_graphics,
				getString(hd ? R.string.options_found : R.string.options_missing_classic), false);
		infoRow(rows, R.string.options_de_graphics,
				getString(de ? R.string.options_found : R.string.options_missing_classic), false);
		final int recordings = this.activity.art.recordings();
		infoRow(rows, R.string.options_backgrounds, recordings == 0 ? getString(R.string.options_backgrounds_missing)
				: getString(R.string.options_backgrounds_count, recordings, SkinArt.SCENES.length), false);
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = row(rows);
		final float buttonW = b.tablet ? 480 : 300;
		final float buttonH = b.tablet ? 112 : 80;
		place(row, button(getString(R.string.options_change), buttonH, b.tablet ? 36 : 26,
				v -> this.activity.openGameData()),
				b.labelX, (b.rowH - buttonH) / 2, buttonW, buttonH);
		place(row, button(getString(R.string.options_recheck), buttonH, b.tablet ? 36 : 26, v -> recheck()),
				b.labelX + buttonW + (b.tablet ? 32 : 24), (b.rowH - buttonH) / 2, buttonW, buttonH);
		customContentRows(rows, buttonW, buttonH);
		profileStorageRows(rows);
	}

	/**
	 * Whether the profiles are kept in the shared storage (where an uninstall leaves them), and where they are now.
	 * The choice takes effect when the app starts next.
	 */
	private void profileStorageRows(final LinearLayout rows) {
		final boolean wanted = ProfileStorageAndroid.wanted(this.activity);
		checkRow(rows, R.string.options_profiles_shared, wanted, on -> {
			ProfileStorageAndroid.setWanted(this.activity, on);
			showOptions(TAB_GAME_DATA);
		});
		final String state = ProfileStorageAndroid.state();
		final File shared = ProfileStorageAndroid.active();
		final boolean blocked = "noaccess".equals(state) || "unwritable".equals(state) || "failed".equals(state);
		final String where;
		if (shared != null) {
			where = shared.getAbsolutePath();
		}
		else if ("noaccess".equals(state)) {
			where = getString(R.string.options_profiles_noaccess);
		}
		else if ("unwritable".equals(state)) {
			where = getString(R.string.options_profiles_unwritable);
		}
		else if ("failed".equals(state)) {
			where = getString(R.string.options_profiles_failed);
		}
		else {
			where = getString(R.string.options_profiles_private);
		}
		final TextView shown = infoRow(rows, R.string.options_profiles_where,
				(wanted == (shared != null)) || blocked ? where
						: getString(R.string.options_profiles_restart, where),
				shared != null);
		if ("noaccess".equals(state) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)) {
			final View row = rows.getChildAt(rows.getChildCount() - 1);
			onTap(row, false, v -> this.activity.startActivity(
					new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
							Uri.parse("package:" + this.activity.getPackageName()))));
			row.setContentDescription(shown.getText());
		}
	}

	/** The player's content folder: where it is and the buttons that choose or leave it. */
	private void customContentRows(final LinearLayout rows, final float buttonW, final float buttonH) {
		final LauncherBoard b = this.activity.board;
		final File custom = this.activity.customContent.folder();
		infoRow(rows, R.string.options_custom_folder,
				custom == null ? getString(R.string.options_custom_none) : custom.getAbsolutePath(), custom != null);
		final FrameLayout row = row(rows);
		// The name of the first button is long.
		final float chooseW = buttonW * 1.6f;
		place(row, button(getString(R.string.options_custom_choose), buttonH, b.tablet ? 32 : 22,
				v -> this.activity.customContent.chooseFolder()),
				b.labelX, (b.rowH - buttonH) / 2, chooseW, buttonH);
		if (custom != null) {
			place(row, button(getString(R.string.options_custom_clear), buttonH, b.tablet ? 36 : 26,
					v -> this.activity.customContent.clear()),
					b.labelX + chooseW + (b.tablet ? 32 : 24), (b.rowH - buttonH) / 2, buttonW, buttonH);
		}
	}

	private void aboutRows(final LinearLayout rows) {
		infoRow(rows, R.string.options_launcher, this.activity.homePage.buildLine(), false);
		infoRow(rows, R.string.options_engine, getString(R.string.options_engine_value), false);
		infoRow(rows, R.string.options_license, getString(R.string.options_license_value), false);
		infoRow(rows, R.string.options_source_code, ProjectLink.shortUrl(), false);
		final View sourceRow = rows.getChildAt(rows.getChildCount() - 1);
		sourceRow.setContentDescription(getString(R.string.options_source_code));
		onTap(sourceRow, false, v -> {
			try {
				this.activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ProjectLink.url())));
			}
			catch (final RuntimeException e) {
				// No browser on the device; the address stays readable in the row.
			}
		});
		infoRow(rows, R.string.options_art, getString(R.string.options_art_value), false);
		checkRow(rows, R.string.options_check_updates, this.activity.preferences.getBoolean(KEY_UPDATE_CHECK, true),
				on -> this.activity.preferences.edit().putBoolean(KEY_UPDATE_CHECK, on).apply());
		final String skipped = this.activity.preferences.getString(KEY_UPDATE_SKIPPED, "");
		if (!skipped.isEmpty()) {
			infoRow(rows, R.string.options_skipped_update, getString(R.string.options_skipped_update_value, skipped),
					false);
			final View skippedRow = rows.getChildAt(rows.getChildCount() - 1);
			onTap(skippedRow, false, v -> {
				this.activity.preferences.edit().remove(KEY_UPDATE_SKIPPED).apply();
				showOptions(TAB_ABOUT);
			});
			skippedRow.setContentDescription(getString(R.string.options_skipped_update));
		}
		debuggingRows(rows);
	}

	/** Crash logs stay on the phone unless the player attaches one to a bug report; the first turn-on asks. */
	private void debuggingRows(final LinearLayout rows) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout heading = row(rows);
		final TextView title = text(getString(R.string.options_debugging), b.body, this.activity.art.text2());
		title.setGravity(Gravity.CENTER_VERTICAL);
		place(heading, title, b.labelX, 0, b.contentW - (2 * b.pageFrame) - (2 * b.labelX), b.rowH);
		askedCheckRow(rows, R.string.options_crash_logs, CrashLogs.enabled(this.activity), on -> {
			if (!on) {
				this.activity.preferences.edit().putBoolean(KEY_CRASH_LOGS, false).apply();
				return true;
			}
			this.activity.dialogs.showQuestion(getString(R.string.crash_logs_title),
					getString(R.string.crash_logs_line, CrashLogs.location(this.activity)),
					new CharSequence[] { glueLabel("CANCEL", getString(R.string.crash_logs_cancel)),
						getString(R.string.crash_logs_turn_on) },
					new Runnable[] { () -> showOptions(TAB_ABOUT), () -> {
						this.activity.preferences.edit().putBoolean(KEY_CRASH_LOGS, true).apply();
						showOptions(TAB_ABOUT);
					} }, null, 12, true);
			return false;
		});
		infoRow(rows, R.string.options_logs_share, getString(R.string.options_logs_share_value), false);
		final View shareRow = rows.getChildAt(rows.getChildCount() - 1);
		shareRow.setContentDescription(getString(R.string.options_logs_share));
		onTap(shareRow, false, v -> shareLogs());
		infoRow(rows, R.string.options_logs_where, getString(R.string.options_logs_where_value,
				CrashLogs.location(this.activity)), true);
	}

	/** The log is read off the main thread; the share sheet opens when the snapshot is written. */
	private void shareLogs() {
		if (this.sharingLogs) {
			return;
		}
		this.sharingLogs = true;
		new Thread(() -> {
			File file = null;
			try {
				file = CrashLogs.snapshot(this.activity);
			}
			catch (final IOException | RuntimeException e) {
				// Reported below as a log that could not be shared.
			}
			final File shared = file;
			this.activity.runOnUiThread(() -> {
				this.sharingLogs = false;
				if (shared == null) {
					this.activity.dialogs.showMessage(getString(R.string.options_logs_share),
							getString(R.string.options_logs_none));
					return;
				}
				try {
					CrashLogs.share(this.activity, shared, "Warsmash log");
				}
				catch (final RuntimeException e) {
					this.activity.dialogs.showMessage(getString(R.string.options_logs_share),
							getString(R.string.options_crash_cannot_share));
				}
			});
		}, "share-logs").start();
	}

	/** An option row: the gold label on the left, a line above every row but the first. */
	FrameLayout row(final LinearLayout rows) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = new FrameLayout(this.activity);
		if (rows.getChildCount() > 0) {
			final View line = new View(this.activity);
			line.setBackgroundColor(Color.argb(0x14, 0xFF, 0xFF, 0xFF));
			row.addView(line, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(2))));
		}
		rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(b.rowH)));
		return row;
	}

	private FrameLayout labelledRow(final LinearLayout rows, final int label) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = row(rows);
		final TextView name = text(getString(label), b.body, this.activity.art.gold());
		name.setGravity(Gravity.CENTER_VERTICAL);
		place(row, name, b.labelX, 0, b.controlX - b.pageFrame - b.labelX - 16, b.rowH);
		return row;
	}

	/** Control positions are given in content coordinates; the rows start inside the panel frame. */
	private float controlX(final float offset) {
		return (this.activity.board.controlX - this.activity.board.pageFrame) + offset;
	}

	private void checkRow(final LinearLayout rows, final int label, final boolean value,
			final Consumer<Boolean> changed) {
		askedCheckRow(rows, label, value, on -> {
			changed.accept(on);
			return true;
		});
	}

	/** @param changed gets the new value; false leaves the box as it was, for a change that waits for an answer */
	private void askedCheckRow(final LinearLayout rows, final int label, final boolean value,
			final Predicate<Boolean> changed) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = labelledRow(rows, label);
		final LauncherKit.CheckDrawable check = new LauncherKit.CheckDrawable(this.activity.art);
		check.setChecked(value);
		final View box = new View(this.activity);
		box.setBackground(check);
		place(row, box, controlX(0), (b.rowH - b.control) / 2, b.control, b.control);
		final boolean[] on = { value };
		// The whole row is the touch target.
		onTap(row, false, v -> {
			if (changed.test(!on[0])) {
				on[0] = !on[0];
				check.setChecked(on[0]);
			}
		});
		row.setContentDescription(getString(label));
	}

	void radioRow(final LinearLayout rows, final int label, final String[] choices, final boolean[] enabled,
			final int selected, final IntConsumer changed) {
		radioRow(rows, label, choices, enabled, selected, changed, null);
	}

	/** @param lockedNote shown beside a greyed-out choice when it is tapped or held, to say why it cannot be chosen */
	private void radioRow(final LinearLayout rows, final int label, final String[] choices, final boolean[] enabled,
			final int selected, final IntConsumer changed, final String lockedNote) {
		final LauncherBoard b = this.activity.board;
		final TextView[] names = new TextView[choices.length];
		final float[] nameWidths = new float[choices.length];
		for (int i = 0; i < choices.length; i++) {
			final boolean active = (enabled == null) || enabled[i];
			names[i] = text(choices[i], b.body, active ? this.activity.art.text() : LauncherKit.DISABLED);
			nameWidths[i] = names[i].getPaint().measureText(choices[i]) / this.activity.scale;
		}
		final LauncherBoard.RadioPlaces places = b.radioPlaces(nameWidths, b.contentW - (2 * b.pageFrame));
		final FrameLayout row = row(rows);
		row.getLayoutParams().height = px(places.height);
		final TextView rowName = text(getString(label), b.body, this.activity.art.gold());
		rowName.setGravity(Gravity.CENTER_VERTICAL);
		place(row, rowName, b.labelX, 0,
				(places.ownLine ? b.contentW - (2 * b.pageFrame) : b.controlX - b.pageFrame) - b.labelX - 16,
				places.labelH);
		final LauncherKit.RadioDrawable[] marks = new LauncherKit.RadioDrawable[choices.length];
		final int[] current = { selected };
		for (int i = 0; i < choices.length; i++) {
			final int choice = i;
			final boolean active = (enabled == null) || enabled[i];
			final LinearLayout item = new LinearLayout(this.activity);
			item.setGravity(Gravity.CENTER_VERTICAL);
			marks[i] = new LauncherKit.RadioDrawable(this.activity.art);
			marks[i].set(i == selected, active);
			final View mark = new View(this.activity);
			mark.setBackground(marks[i]);
			item.addView(mark, new LinearLayout.LayoutParams(px(b.control), px(b.control)));
			final TextView name = names[i];
			// A name cut to share a narrow line ends in an ellipsis instead of running into the next choice.
			final LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
					ViewGroup.LayoutParams.WRAP_CONTENT, 1);
			nameParams.leftMargin = px(LauncherBoard.RADIO_NAME_GAP);
			item.addView(name, nameParams);
			item.setEnabled(active);
			// A disabled view takes no touches, so a locked choice stays enabled and only ignores the selection.
			if (!active && (lockedNote != null)) {
				item.setEnabled(true);
				onTap(item, false, v -> showHint(item, lockedNote));
				item.setOnLongClickListener(v -> {
					showHint(item, lockedNote);
					return true;
				});
			}
			if (active) {
				onTap(item, false, v -> {
					if (current[0] == choice) {
						return;
					}
					marks[current[0]].set(false, true);
					current[0] = choice;
					marks[choice].set(true, true);
					changed.accept(choice);
				});
			}
			place(row, item, places.x[i], places.choicesY, places.w[i], b.rowH);
		}
	}

	private void showHint(final View anchor, final String note) {
		hideHint();
		final LauncherBoard b = this.activity.board;
		final int[] at = new int[2];
		anchor.getLocationInWindow(at);
		final int[] origin = new int[2];
		this.activity.root.getLocationInWindow(origin);
		// The tip stays inside the panel the choice is in.
		View panel = anchor;
		while ((panel.getParent() instanceof View) && !(panel instanceof ScrollView)) {
			panel = (View) panel.getParent();
		}
		final int[] area = new int[2];
		panel.getLocationInWindow(area);
		final int left = area[0] - origin[0];
		final int top = area[1] - origin[1];
		final int width = panel instanceof ScrollView ? panel.getWidth() : px(b.width);
		final int height = panel instanceof ScrollView ? panel.getHeight() : px(b.height);
		final int border = px(b.tablet ? 8 : 6);
		final TextView tip = text(note, b.small, Color.WHITE);
		tip.setSingleLine(false);
		tip.setEllipsize(null);
		tip.setBackground(panelFrame(b.tablet ? 8 : 6, Color.rgb(0x08, 0x08, 0x10)));
		tip.setPadding(border + px(12), border + px(8), border + px(12), border + px(8));
		final int maxWidth = Math.min(px(b.tablet ? 900 : 700), width);
		tip.setMaxWidth(maxWidth);
		tip.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		params.leftMargin = Math.max(left, Math.min(at[0] - origin[0] - px(8), left + width - tip.getMeasuredWidth()));
		final int below = at[1] - origin[1] + anchor.getHeight() + px(6);
		// Above the choice when there is no room below it, so the tip never covers the choice.
		params.topMargin = below + tip.getMeasuredHeight() <= top + height ? below
				: Math.max(top, at[1] - origin[1] - px(6) - tip.getMeasuredHeight());
		this.activity.root.addView(tip, params);
		this.hint = tip;
		tip.postDelayed(this::hideHint, HINT_MILLIS);
	}

	private void hideHint() {
		if (this.hint != null) {
			this.activity.root.removeView(this.hint);
			this.hint = null;
		}
	}

	private void sliderRow(final LinearLayout rows, final int label, final int value, final String low,
			final String high, final LauncherKit.Slider.Listener changed) {
		sliderRow(rows, label, value, low, high, changed, null);
	}

	/** Low and High on either side of the slider, or the slider and its value shown after it. */
	private void sliderRow(final LinearLayout rows, final int label, final int value, final String low,
			final String high, final LauncherKit.Slider.Listener changed, final TextView shown) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = labelledRow(rows, label);
		final float lowW = low != null ? (b.tablet ? 90 : 72) : 0;
		final float sliderW = b.tablet ? 720 : 380;
		if (low != null) {
			final TextView lowText = text(low, b.small, this.activity.art.text());
			lowText.setGravity(Gravity.CENTER_VERTICAL);
			place(row, lowText, controlX(0), 0, lowW, b.rowH);
		}
		final LauncherKit.Slider slider = new LauncherKit.Slider(this.activity, this.activity.art, px(b.control * 0.5f),
				px(b.control));
		slider.setValue(value);
		slider.setListener(changed);
		slider.setContentDescription(getString(label));
		place(row, slider, controlX(lowW), 0, sliderW, b.rowH);
		final TextView after = shown != null ? shown : text(high, b.small, this.activity.art.text());
		after.setGravity(Gravity.CENTER_VERTICAL);
		place(row, after, controlX(lowW + sliderW + (b.tablet ? 20 : 12)), 0, b.tablet ? 240 : 140, b.rowH);
	}

	private String filesText(final String files, final DataFolderCheck.Readability readability) {
		return readability.unreadable == 0 ? files
				: getString(R.string.options_files_unreadable, files, readability.unreadable);
	}

	private TextView infoRow(final LinearLayout rows, final int label, final String value, final boolean path) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = labelledRow(rows, label);
		final TextView shown = text(value, b.body, this.activity.art.text());
		shown.setGravity(Gravity.CENTER_VERTICAL);
		// A long folder keeps its last parts visible.
		shown.setEllipsize(path ? TextUtils.TruncateAt.START : TextUtils.TruncateAt.END);
		place(row, shown, controlX(0), 0, b.contentW - b.controlX - b.pageFrame - 24, b.rowH);
		return shown;
	}

	private void chooseSkin(final boolean reforged) {
		final String skin = reforged ? SkinArt.REFORGED_SKIN : SkinArt.CLASSIC_SKIN;
		this.activity.preferences.edit().putString(KEY_SKIN, skin).apply();
		this.activity.cache = SkinCache.forConfiguration(this.activity, this.activity.ini, skin);
		if (this.activity.cache.isCurrent()) {
			showOptions(TAB_GRAPHICS);
		}
		else {
			this.activity.afterPrep = LauncherActivity.PAGE_OPTIONS;
			this.activity.prepPage.showPrep();
		}
	}

	/** Re-check: the art is prepared again from the game data, as after a change of data. */
	private void recheck() {
		this.activity.cache.invalidate();
		this.activity.afterPrep = LauncherActivity.PAGE_OPTIONS;
		this.optionsTab = TAB_GAME_DATA;
		this.activity.prepPage.showPrep();
	}
}
