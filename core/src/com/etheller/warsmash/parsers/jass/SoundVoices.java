package com.etheller.warsmash.parsers.jass;

import java.util.List;
import java.util.function.Consumer;

import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.LocalUiLink;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CMIDISound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFilename;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFromLabel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.SimSound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.SoundLabelInfo;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.SoundLabelParams;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;

/**
 * How the sound natives make and change sounds: the simulation owns the {@link SimSound} a script holds and answers
 * what it reads, and what it does to the sound reaches the voice on the render side as an event, in the order the
 * script did it. Only a label or file the render side has not looked at yet is asked of it (once, when the sound is
 * made).
 */
final class SoundVoices {
	private final War3MapViewer viewer;
	private final WarsmashUI ui;
	private final LocalUiLink link;

	SoundVoices(final War3MapViewer viewer, final WarsmashUI ui, final LocalUiLink link) {
		this.viewer = viewer;
		this.ui = ui;
		this.link = link;
	}

	private float fileDuration(final String fileName) {
		UnitSound.ScriptFile file = this.viewer.cachedScriptSoundFile(fileName);
		if (file == null) {
			file = this.link.onRender("CreateSound", () -> this.viewer.getScriptSoundFile(fileName));
		}
		return file.getDuration();
	}

	/** The length scripts see for a file, as GetSoundFileDuration reads it. */
	float lengthOfFile(final String fileName) {
		return fileDuration(fileName);
	}

	private SoundLabelInfo labelInfo(final String label) {
		final SoundLabelInfo info = this.viewer.cachedSoundLabelInfo(label);
		return info != null ? info : this.link.onRender("SoundLabelInfo", () -> this.viewer.getSoundLabelInfo(label));
	}

	private SoundLabelParams labelParams(final String label) {
		final SoundLabelParams params = this.viewer.cachedSoundLabelParams(label);
		return params != null ? params
				: this.link.onRender("SoundLabelParams", () -> this.viewer.getSoundLabelParams(label));
	}

	SimSound createFile(final String fileName, final boolean looping, final boolean is3D,
			final boolean stopWhenOutOfRange, final int fadeInRate, final int fadeOutRate, final String eaxSetting,
			final String paramsLabel) {
		final SimSound sound = new SimSound(SimSound.Kind.FILE, fileName, looping, is3D, stopWhenOutOfRange,
				fadeInRate, fadeOutRate, eaxSetting, paramsLabel, null, fileDuration(fileName));
		if (paramsLabel != null) {
			labelParams(paramsLabel).applyTo(sound);
		}
		return made(sound);
	}

	SimSound createLabel(final String label, final boolean looping, final boolean is3D,
			final boolean stopWhenOutOfRange, final int fadeInRate, final int fadeOutRate) {
		final SoundLabelInfo info = labelInfo(label);
		return made(new SimSound(SimSound.Kind.LABEL, label, looping, is3D, stopWhenOutOfRange, fadeInRate,
				fadeOutRate, "", null, info, info.getFirstDuration()));
	}

	SimSound createMidi(final String label, final int fadeInRate, final int fadeOutRate) {
		final SoundLabelInfo info = labelInfo(label);
		if (info.getFileCount() == 0) {
			return made(new SimSound(SimSound.Kind.MIDI, label, false, false, false, fadeInRate, fadeOutRate, "",
					null, info, 0));
		}
		return made(new SimSound(SimSound.Kind.LABEL, label, true, false, false, fadeInRate, fadeOutRate, "", null,
				info, info.getFirstDuration()));
	}

	private SimSound made(final SimSound sound) {
		this.link.send(RenderEvent.FRAME_COMMAND, () -> voice(sound));
		return sound;
	}

	/** Render thread: the voice of the sound, made when the sound's first event arrives. */
	private CSound voice(final SimSound sound) {
		CSound voice = sound.getVoice();
		if (voice == null) {
			voice = build(sound);
			sound.setVoice(voice);
		}
		return voice;
	}

	private CSound build(final SimSound sound) {
		final AudioContext audio = sound.is3D() ? this.viewer.worldScene.audioContext
				: this.ui.getUiScene().audioContext;
		switch (sound.getKind()) {
		case FILE: {
			final String fileName = sound.getSource();
			final UnitSound.ScriptFile file = this.viewer.getScriptSoundFile(fileName);
			final CSoundFilename voice = new CSoundFilename(null, file.getDuration(), audio, sound.getLooping(),
					sound.isStopWhenOutOfRange(), sound.getFadeInRate(), sound.getFadeOutRate(),
					sound.getEaxSetting());
			voice.setFileName(fileName);
			voice.setOwner(this.viewer.getFileSoundOwner());
			voice.awaitSound();
			this.viewer.getFileSoundLater(fileName,
					made -> voice.bindSound(made, this.viewer.getPresentedTick()));
			if (sound.getParamsLabel() != null) {
				this.viewer.getSoundLabelParams(sound.getParamsLabel()).applyTo(voice);
			}
			return voice;
		}
		case LABEL: {
			final UnitSound labelSound = this.viewer.getUiSounds().getSound(sound.getSource());
			if ((labelSound != null) && (sound.getLooping() || labelSound.isLooping())) {
				// a loop's sound is wanted whole when it starts, so it is made while the script goes on
				labelSound.preload();
			}
			return new CSoundFromLabel(labelSound, audio, sound.getLooping(), sound.is3D(), sound.isStopWhenOutOfRange(), sound.getFadeInRate(),
					sound.getFadeOutRate());
		}
		default:
			return new CMIDISound(sound.getSource(), sound.getFadeInRate(), sound.getFadeOutRate());
		}
	}

	/**
	 * A change of the sound: the sound a script holds takes it at once, its voice when the event is played. A sound
	 * from a game saved before sounds had voices is the object that plays, so the render side does it.
	 */
	void apply(final CSound sound, final Consumer<CSound> change) {
		if (sound instanceof SimSound) {
			change.accept(sound);
			toVoice((SimSound) sound, change);
		}
		else {
			this.link.onRender("sound", () -> {
				change.accept(sound);
				return null;
			});
		}
	}

	/** A change that only the voice has (a stacked sound, the unit a sound follows). */
	void render(final CSound sound, final Consumer<CSound> change) {
		if (sound instanceof SimSound) {
			toVoice((SimSound) sound, change);
		}
		else {
			this.link.onRender("sound", () -> {
				change.accept(sound);
				return null;
			});
		}
	}

	private void toVoice(final SimSound sound, final Consumer<CSound> change) {
		this.link.send(RenderEvent.FRAME_COMMAND, () -> change.accept(voice(sound)));
	}

	void start(final CSound sound, final int tick) {
		apply(sound, target -> {
			target.start(tick);
			if ((this.ui != null) && (target instanceof CSoundFilename)) {
				this.ui.dialogueSoundStarted(((CSoundFilename) target).getFileName());
			}
		});
	}

	void attach(final CSound sound, final CUnit unit) {
		if (sound instanceof SimSound) {
			((SimSound) sound).setAttachedUnit(unit);
		}
		render(sound, target -> target.attachToUnit(this.viewer.getRenderPeer(unit)));
	}

	/** Render thread, after a saved game was read: the sounds it holds get the voices that continue them. */
	void restoreVoices(final List<SimSound> sounds) {
		for (final SimSound sound : sounds) {
			final CSound restored = build(sound);
			sound.restoreVoice(restored);
			if (sound.getAttachedUnit() != null) {
				restored.attachToUnit(this.viewer.getRenderPeer(sound.getAttachedUnit()));
			}
		}
	}
}
