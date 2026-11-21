package com.etheller.warsmash.parsers.jass;

import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderEvent;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSound;

/**
 * Sound and music natives.
 */
final class SoundNatives extends CommonNatives {
	private final SoundVoices voices;

	SoundNatives(final CommonEnvironment environment) {
		super(environment);
		this.voices = new SoundVoices(this.war3MapViewer, this.meleeUI, this.link);
		this.war3MapViewer.setSoundVoiceRestorer(this.voices::restoreVoices);
	}

	@Override
	void register() {
		final HandleJassType soundType = type("sound");

		// Picks an EAX reverb preset; scripts read nothing back from it, and the audio back ends here (OpenAL through
		// libGDX, Android) have no reverb to set, so the mixer only keeps the name.
		jassProgramVisitor.getJassNativeManager().createNative("NewSoundEnvironment",
				(arguments, globalScope, triggerScope) -> {
					final String environment = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					this.link.send(RenderEvent.FRAME_COMMAND, () -> war3MapViewer.getSoundMixer().setEnvironment(environment));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateMIDISound",
				(arguments, globalScope, triggerScope) -> {
					final String soundLabel = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final int fadeInRate = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int fadeOutRate = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					return newHandle(soundType, this.voices.createMidi(soundLabel, fadeInRate, fadeOutRate));
				});
		jassProgramVisitor.getJassNativeManager().createNative("StartSound",
				(arguments, globalScope, triggerScope) -> {
					final CSound soundHandle = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (soundHandle != null) {
						this.voices.start(soundHandle, this.simulation.getGameTurnTick());
					}
					return null;
				});
		// Fading out is not supported yet; the sound stops right away.
		jassProgramVisitor.getJassNativeManager().createNative("StopSound",
				(arguments, globalScope, triggerScope) -> {
					final CSound soundHandle = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (soundHandle != null) {
						this.voices.apply(soundHandle, CSound::stop);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetMapMusic",
				(arguments, globalScope, triggerScope) -> {
					final String musicName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final boolean random = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final int index = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());

					this.link.send(RenderEvent.FRAME_COMMAND,
							() -> meleeUI.setMapMusic(this.gameUI.trySkinField(musicName), random, index));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PlayMusic",
				(arguments, globalScope, triggerScope) -> {
					final String musicName = arguments.get(0).visit(StringJassValueVisitor.getInstance());

					this.link.send(RenderEvent.FRAME_COMMAND,
							() -> meleeUI.playMusic(this.gameUI.trySkinField(musicName), true, 0));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PlayMusicEx",
				(arguments, globalScope, triggerScope) -> {
					final String musicName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final int frommsecs = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int fadeInMSecs = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());

					this.link.send(RenderEvent.FRAME_COMMAND, () -> meleeUI
							.playMusicEx(this.gameUI.trySkinField(musicName), true, 0, frommsecs, fadeInMSecs));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("StopMusic",
				(arguments, globalScope, triggerScope) -> {
					final boolean fadeOut = arguments.get(0).visit(BooleanJassValueVisitor.getInstance());
					this.link.send(RenderEvent.FRAME_COMMAND, () -> meleeUI.stopMusic(fadeOut));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ResumeMusic",
				(arguments, globalScope, triggerScope) -> {
					this.link.send(RenderEvent.FRAME_COMMAND, () -> meleeUI.resumeMusic());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PlayThematicMusic",
				(arguments, globalScope, triggerScope) -> {
					final String musicName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					this.link.send(RenderEvent.FRAME_COMMAND,
							() -> meleeUI.playMusic(this.gameUI.trySkinField(musicName), true, 0));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PlayThematicMusicEx",
				(arguments, globalScope, triggerScope) -> {
					final String musicName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final int frommsecs = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					this.link.send(RenderEvent.FRAME_COMMAND,
							() -> meleeUI.playMusicEx(this.gameUI.trySkinField(musicName), true, 0, frommsecs, -1));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("EndThematicMusic",
				(arguments, globalScope, triggerScope) -> {
					this.link.send(RenderEvent.FRAME_COMMAND, () -> {
						meleeUI.stopMusic(false);
						meleeUI.playMapMusic();
					});
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetMusicVolume",
				(arguments, globalScope, triggerScope) -> {
					final int volume = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					this.link.send(RenderEvent.FRAME_COMMAND, () -> meleeUI.setMusicVolume(volume));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetMusicPlayPosition",
				(arguments, globalScope, triggerScope) -> {
					final int millisecs = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					this.link.send(RenderEvent.FRAME_COMMAND, () -> meleeUI.setMusicPlayPosition(millisecs));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetThematicMusicPlayPosition",
				(arguments, globalScope, triggerScope) -> {
					final int millisecs = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					this.link.send(RenderEvent.FRAME_COMMAND, () -> meleeUI.setMusicPlayPosition(millisecs));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSoundDuration",
				(arguments, globalScope, triggerScope) -> {
					final CSound sound = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (sound == null) {
						return IntegerJassValue.ZERO;
					}
					return IntegerJassValue.of(Math.round(sound.getDuration() * 1000));
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateSoundFromLabel",
				(arguments, globalScope, triggerScope) -> {
					final String soundLabel = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final boolean looping = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final boolean is3D = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final boolean stopWhenOutOfRange = arguments.get(3)
							.visit(BooleanJassValueVisitor.getInstance());
					final int fadeInRate = arguments.get(4).visit(IntegerJassValueVisitor.getInstance());
					final int fadeOutRate = arguments.get(5).visit(IntegerJassValueVisitor.getInstance());
					return newHandle(soundType, this.voices.createLabel(soundLabel, looping, is3D, stopWhenOutOfRange,
							fadeInRate, fadeOutRate));
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateSound",
				(arguments, globalScope, triggerScope) -> {
					final String fileName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final boolean looping = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final boolean is3D = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final boolean stopWhenOutOfRange = arguments.get(3)
							.visit(BooleanJassValueVisitor.getInstance());
					final int fadeInRate = arguments.get(4).visit(IntegerJassValueVisitor.getInstance());
					final int fadeOutRate = arguments.get(5).visit(IntegerJassValueVisitor.getInstance());
					final String eaxSetting = arguments.get(6).visit(StringJassValueVisitor.getInstance());
					return newHandle(soundType, this.voices.createFile(fileName, looping, is3D, stopWhenOutOfRange,
							fadeInRate, fadeOutRate, eaxSetting, null));
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateSoundFilenameWithLabel",
				(arguments, globalScope, triggerScope) -> {
					final String fileName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final boolean looping = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final boolean is3D = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final boolean stopWhenOutOfRange = arguments.get(3)
							.visit(BooleanJassValueVisitor.getInstance());
					final int fadeInRate = arguments.get(4).visit(IntegerJassValueVisitor.getInstance());
					final int fadeOutRate = arguments.get(5).visit(IntegerJassValueVisitor.getInstance());
					final String label = arguments.get(6).visit(StringJassValueVisitor.getInstance());
					return newHandle(soundType, this.voices.createFile(fileName, looping, is3D, stopWhenOutOfRange,
							fadeInRate, fadeOutRate, "", label == null ? "" : label));
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetSoundDuration",
				(arguments, globalScope, triggerScope) -> {
					final CSound sound = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float seconds = arguments.get(1).visit(IntegerJassValueVisitor.getInstance()) / 1000f;
					if (sound != null) {
						this.voices.apply(sound, target -> target.setDuration(seconds));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitLoopSpellSoundEffect",
				(arguments, globalScope, triggerScope) -> {
					final CUnit onUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int abilityAliasRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					this.simulation.unitLoopSoundEffectEvent(onUnit, new War3ID(abilityAliasRawcode));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitSpellSoundEffect",
				(arguments, globalScope, triggerScope) -> {
					final CUnit onUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int abilityAliasRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					this.simulation.unitSoundEffectEvent(onUnit, new War3ID(abilityAliasRawcode));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitStopSpellSoundEffect",
				(arguments, globalScope, triggerScope) -> {
					final CUnit onUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int abilityAliasRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					this.simulation.unitStopSoundEffectEvent(onUnit, new War3ID(abilityAliasRawcode));
					return null;
				});
	}
}
