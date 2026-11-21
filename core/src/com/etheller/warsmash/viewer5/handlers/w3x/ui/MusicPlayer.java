package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.badlogic.gdx.audio.Music;

public interface MusicPlayer {
	public void update();

	public void setMusicPosition(int positionMSecs);

	public void stopMusic();

	public Music setDefaultMusic(String musicField, boolean random, int index);

	public Music playDefaultMusic();

	public Music playMusicEx(String musicField, boolean random, int index, int fromMSecs, int fadeInMSecs);

	public void resumeMusic();

	/** Plays the track once, not remembered by the game; the default music follows it. */
	default void playMusicOnce(final String musicField) {
	}

	public void setVolume(int volume);

	public void optionsChanged();

	/** Holds the music still while the window has no focus, for a map that asked for it. */
	default void setPausedByFocus(final boolean paused) {
	}

	MusicPlayer DO_NOTHING = new MusicPlayer() {
		@Override
		public void update() {
		}

		@Override
		public void stopMusic() {
		}

		@Override
		public void setMusicPosition(int positionMSecs) {
		}

		@Override
		public Music setDefaultMusic(String musicField, boolean random, int index) {
			return null;
		}

		@Override
		public Music playDefaultMusic() {
			return null;
		}

		@Override
		public Music playMusicEx(String musicField, boolean random, int index, int fromMSecs, int fadeInMSecs) {
			return null;
		}

		@Override
		public void resumeMusic() {
		}

		@Override
		public void setVolume(int volume) {
		}

		@Override
		public void optionsChanged() {
		}
	};
}
