package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

/** What a script may ask of this machine's keyboard, mouse and window; see {@link LocalUiLink#input()}. */
public interface LocalInputView {
	/** Whether the libGDX key is held. */
	boolean key(int gdxKey);

	/** Whether the libGDX mouse button (0 left, 1 right, 2 middle) is held. */
	boolean mouseButton(int gdxButton);

	int mouseX();

	int mouseY();

	/** The meta keys held, as the mask of the key event natives. */
	int metaKeys();

	int width();

	int height();

	/** Handle of the unit under the pointer, 0 for none. */
	int hoveredUnitHandle();
}
