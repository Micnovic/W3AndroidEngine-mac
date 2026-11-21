package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.graphics.Texture;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.IconTexture;

/**
 * The textures of the icons the panels show, by the path the frame names them with; each is read on first use and
 * kept. Render thread only.
 */
final class PanelIcons {
	private final Map<String, IconTexture> textures = new HashMap<>();
	private String disabledPrefix;

	Texture get(final GameUI gameUI, final String path) {
		if (path == null) {
			return null;
		}
		IconTexture texture = this.textures.get(path);
		if (texture == null) {
			texture = new IconTexture(gameUI, path);
			this.textures.put(path, texture);
		}
		return texture.get();
	}

	Texture getDisabled(final GameUI gameUI, final String path) {
		if (path == null) {
			return null;
		}
		if (this.disabledPrefix == null) {
			this.disabledPrefix = gameUI.getSkinField("CommandButtonDisabledArtPath");
		}
		return get(gameUI, AbilityDataUI.disable(path, this.disabledPrefix));
	}
}
