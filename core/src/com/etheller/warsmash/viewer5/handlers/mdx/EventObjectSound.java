package com.etheller.warsmash.viewer5.handlers.mdx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.gl.Extensions;

/**
 * A sound file of an event object. A model is built in one go, and decoding all of its sounds then held the game up,
 * so a file not decoded ahead is read and decoded on a helper thread where the backend can, when it is first played.
 */
final class EventObjectSound implements Disposable {
	private final FileHandle backgroundHandle;
	private final FileHandle ownHandle;
	private Sound sound;
	// The plays that wait for the sound, null while none is being made.
	private List<Consumer<Sound>> waiting;
	private boolean disposed;

	/**
	 * @param backgroundHandle names the file as a read ahead of it does, so that it is taken over
	 * @param ownHandle        reads the file where the calling thread has to
	 */
	EventObjectSound(final FileHandle backgroundHandle, final FileHandle ownHandle) {
		this.backgroundHandle = backgroundHandle;
		this.ownHandle = ownHandle;
		this.sound = Extensions.audio.newSoundIfReady(backgroundHandle != null ? backgroundHandle : ownHandle);
	}

	/**
	 * Runs the play with the sound: now when it is made, else in a frame after it is done, so that the first play of
	 * the sound is not lost. The play is given null when the sound cannot be made.
	 */
	void whenReady(final Consumer<Sound> play) {
		if (this.sound != null) {
			play.accept(this.sound);
			return;
		}
		if (this.waiting != null) {
			this.waiting.add(play);
			return;
		}
		final List<Consumer<Sound>> plays = new ArrayList<>();
		plays.add(play);
		boolean started = false;
		try {
			started = Extensions.audio.newSoundLater(this.backgroundHandle, made -> {
				this.waiting = null;
				if (this.disposed) {
					if (made != null) {
						made.dispose();
					}
					return;
				}
				if (made == null) {
					Log.warn("Sound file not read: " + this.backgroundHandle.path());
				}
				else {
					this.sound = made;
				}
				for (final Consumer<Sound> waitingPlay : plays) {
					waitingPlay.accept(made);
				}
			});
		}
		catch (final RuntimeException exc) {
			started = false;
		}
		if (started) {
			this.waiting = plays;
			return;
		}
		try {
			this.sound = Extensions.audio.newSound(this.ownHandle);
		}
		catch (final RuntimeException exc) {
			Log.error(exc);
		}
		play.accept(this.sound);
	}

	@Override
	public void dispose() {
		this.disposed = true;
		if (this.sound != null) {
			this.sound.dispose();
			this.sound = null;
		}
	}
}
