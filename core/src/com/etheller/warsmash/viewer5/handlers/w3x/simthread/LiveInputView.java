package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import com.badlogic.gdx.Gdx;
import com.etheller.warsmash.parsers.jass.OsKeys;

/** The devices themselves; on the render thread, or where the script runs on it. */
final class LiveInputView implements LocalInputView {
	static final LiveInputView INSTANCE = new LiveInputView();

	private LiveInputView() {
	}

	@Override
	public boolean key(final int gdxKey) {
		return (Gdx.input != null) && (gdxKey >= 0) && Gdx.input.isKeyPressed(gdxKey);
	}

	@Override
	public boolean mouseButton(final int gdxButton) {
		return (Gdx.input != null) && Gdx.input.isButtonPressed(gdxButton);
	}

	@Override
	public int mouseX() {
		return Gdx.input == null ? 0 : Gdx.input.getX();
	}

	@Override
	public int mouseY() {
		return Gdx.input == null ? 0 : Gdx.input.getY();
	}

	@Override
	public int metaKeys() {
		return Gdx.input == null ? 0 : OsKeys.heldMetaKeys(Gdx.input);
	}

	@Override
	public int width() {
		return Gdx.graphics == null ? 0 : Gdx.graphics.getWidth();
	}

	@Override
	public int height() {
		return Gdx.graphics == null ? 0 : Gdx.graphics.getHeight();
	}

	@Override
	public int hoveredUnitHandle() {
		return 0;
	}
}
