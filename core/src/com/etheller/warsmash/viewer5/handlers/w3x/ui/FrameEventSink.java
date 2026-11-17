package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.etheller.warsmash.parsers.fdf.frames.UIFrame;

/** What this machine's player does to the interface's frames, told to the map script's Frame API. */
public interface FrameEventSink {
	void mouseEnter(UIFrame frame);

	void mouseLeave(UIFrame frame);

	void mouseDown(UIFrame frame);

	void mouseUp(UIFrame frame);

	/** The frame was clicked: pressed and released on it. */
	void click(UIFrame frame);

	/** A turn of the mouse wheel over the frame; positive is away from the player. */
	void mouseWheel(UIFrame frame, float amount);
}
