package com.etheller.warsmash.viewer5.handlers.w3x;

/** The switches of the game interface a map script flips with the Reforged natives; local to this machine. */
public final class ScriptUiState {
	private boolean cursorEnabled = true;
	private boolean selectionEnabled = true;
	private boolean selectionCircleEnabled = true;
	private boolean targetIndicatorEnabled = true;
	private boolean originFramesHidden;
	private boolean uiAutoPosition = true;
	private Runnable frameFlush;

	/** Called by the interface every frame, for the Frame API to lay out the frames a script changed. */
	public void setFrameFlush(final Runnable frameFlush) {
		this.frameFlush = frameFlush;
	}

	public void flushFrames() {
		if (this.frameFlush != null) {
			this.frameFlush.run();
		}
	}

	public boolean isCursorEnabled() {
		return this.cursorEnabled;
	}

	public void setCursorEnabled(final boolean cursorEnabled) {
		this.cursorEnabled = cursorEnabled;
	}

	public boolean isSelectionEnabled() {
		return this.selectionEnabled;
	}

	public void setSelectionEnabled(final boolean selectionEnabled) {
		this.selectionEnabled = selectionEnabled;
	}

	public boolean isSelectionCircleEnabled() {
		return this.selectionCircleEnabled;
	}

	public void setSelectionCircleEnabled(final boolean selectionCircleEnabled) {
		this.selectionCircleEnabled = selectionCircleEnabled;
	}

	public boolean isTargetIndicatorEnabled() {
		return this.targetIndicatorEnabled;
	}

	public void setTargetIndicatorEnabled(final boolean targetIndicatorEnabled) {
		this.targetIndicatorEnabled = targetIndicatorEnabled;
	}

	public boolean isOriginFramesHidden() {
		return this.originFramesHidden;
	}

	public void setOriginFramesHidden(final boolean originFramesHidden) {
		this.originFramesHidden = originFramesHidden;
	}

	public boolean isUiAutoPosition() {
		return this.uiAutoPosition;
	}

	public void setUiAutoPosition(final boolean uiAutoPosition) {
		this.uiAutoPosition = uiAutoPosition;
	}
}
