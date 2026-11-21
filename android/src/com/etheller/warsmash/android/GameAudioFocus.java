package com.etheller.warsmash.android;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

/**
 * Holds the system's audio focus while the game is in front, so that a call or another player's app silences the game
 * and music apps resume once it leaves. A short interruption that lets the game play quieter is left to the system,
 * which lowers the volume by itself.
 */
final class GameAudioFocus implements AudioManager.OnAudioFocusChangeListener {
	private final AudioManager manager;
	private final AudioFocusRequest request;
	private boolean held;
	private boolean lost;

	GameAudioFocus(final Context context) {
		this.manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		this.request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
				.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
						.setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
				.setOnAudioFocusChangeListener(this, new Handler(Looper.getMainLooper())).build();
	}

	/** Asks for focus; the game plays on even when it is refused, as it did before there was a request. */
	void acquire() {
		if (this.manager == null) {
			return;
		}
		this.lost = false;
		this.held = this.manager.requestAudioFocus(this.request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
	}

	void release() {
		this.lost = false;
		if ((this.manager != null) && this.held) {
			this.manager.abandonAudioFocusRequest(this.request);
		}
		this.held = false;
	}

	/** @return whether another app took the focus and the game must stay silent */
	boolean isLost() {
		return this.lost;
	}

	@Override
	public void onAudioFocusChange(final int change) {
		switch (change) {
		case AudioManager.AUDIOFOCUS_LOSS:
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			this.lost = true;
			PreparedMusic.pauseAll();
			PooledSounds.pauseAll();
			break;
		case AudioManager.AUDIOFOCUS_GAIN:
			if (this.lost) {
				this.lost = false;
				PreparedMusic.resumeAll();
				PooledSounds.resumeAll();
			}
			break;
		default:
			break;
		}
	}
}
