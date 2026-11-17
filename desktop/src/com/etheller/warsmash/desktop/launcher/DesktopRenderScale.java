package com.etheller.warsmash.desktop.launcher;

import com.badlogic.gdx.Gdx;
import com.etheller.warsmash.launcher.LauncherKeys;
import com.etheller.warsmash.launcher.RenderScaleStore;

/** The render scale of the launcher's Options page, which the game's own Options menu changes too. */
public final class DesktopRenderScale implements RenderScaleStore {
	private DesktopStore store() {
		return new DesktopStore(Gdx.app.getPreferences(LauncherScreen.PREFERENCES));
	}

	@Override
	public int percent() {
		return store().getInt(LauncherKeys.KEY_RENDER_SCALE, 100);
	}

	@Override
	public void set(final int percent) {
		store().edit().putInt(LauncherKeys.KEY_RENDER_SCALE, percent).apply();
	}
}
