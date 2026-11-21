package com.etheller.warsmash.viewer5.handlers.w3x.save;

import java.util.Arrays;

import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.LiveRenderObject;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ScriptEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

/**
 * Takes the place of a lasting effect in a saved game: what is needed to show it again. The model instance is not
 * saved, so a loaded effect starts over from its stand animation.
 */
public final class SavedEffect extends AllocatedHandle implements SimulationRenderComponentModel, ScriptEffect {
	private String modelName;
	private CWidget widget;
	private String[] attachPoints;
	private float x;
	private float y;
	private float z;
	private float yaw;
	private float pitch;
	private float roll;
	@SavedDefault("1")
	private float scaleX = 1;
	@SavedDefault("1")
	private float scaleY = 1;
	@SavedDefault("1")
	private float scaleZ = 1;
	private boolean ended;
	private LiveRenderObject<RenderSpellEffect> liveObject;

	private RenderSpellEffect live() {
		return this.liveObject == null ? null : this.liveObject.value;
	}

	static SavedEffect of(final RenderSpellEffect effect) {
		SavedEffect saved = (SavedEffect) effect.getSavedForm();
		if (saved == null) {
			saved = new SavedEffect();
			saved.modelName = effect.getModelName();
			saved.setHandleId(effect.getHandleId());
			saved.widget = effect.getWidget();
			saved.attachPoints = effect.getAttachPoints() == null ? null
					: effect.getAttachPoints().toArray(new String[0]);
			saved.x = effect.getPointX();
			saved.y = effect.getPointY();
			saved.yaw = effect.getYaw();
			saved.liveObject = new LiveRenderObject<>(effect);
			effect.setSavedForm(saved);
		}
		saved.z = effect.getLocalZ();
		if (saved.widget == null) {
			// Triggers may have moved or turned it since it was made.
			saved.x = effect.getPlacedX();
			saved.y = effect.getPlacedY();
			saved.yaw = effect.getYaw();
		}
		saved.pitch = effect.getScriptPitch();
		saved.roll = effect.getScriptRoll();
		saved.scaleX = effect.getSavedScaleX();
		saved.scaleY = effect.getSavedScaleY();
		saved.scaleZ = effect.getSavedScaleZ();
		saved.ended = !effect.isLasting();
		return saved;
	}

	void show(final War3MapViewer viewer) {
		if (this.ended || (live() != null) || (this.modelName == null)) {
			return;
		}
		if (this.widget instanceof CUnit) {
			if (viewer.getRenderPeer((CUnit) this.widget) == null) {
				return;
			}
		}
		final RenderSpellEffect effect;
		if (this.widget != null) {
			effect = viewer.addSpecialEffectTarget(this.modelName, this.widget, Arrays.asList(this.attachPoints));
		}
		else {
			effect = viewer.addSpecialEffect(this.modelName, this.x, this.y, this.yaw);
		}
		if (effect != null) {
			this.liveObject = new LiveRenderObject<>(effect);
			effect.setSavedForm(this);
			effect.setAnimations(RenderSpellEffect.STAND_THEN_DEATH, false);
			if (this.widget == null) {
				effect.setHeight(this.z);
				if ((this.pitch != 0) || (this.roll != 0)) {
					effect.setScriptOrientation(this.yaw, this.pitch, this.roll);
				}
			}
			if ((this.scaleX != 1) || (this.scaleY != 1) || (this.scaleZ != 1)) {
				effect.setScriptMatrixScale(this.scaleX, this.scaleY, this.scaleZ);
			}
		}
	}

	@Override
	public void remove() {
		this.ended = true;
		if (live() != null) {
			live().setAnimations(RenderSpellEffect.DEATH_ONLY, true);
		}
	}

	@Override
	public void setHeight(final float height) {
		this.z = height;
		if (live() != null) {
			live().setHeight(height);
		}
	}

	@Override
	public void setKillWhenDone(final boolean killWhenDone) {
		this.ended = killWhenDone;
		if (live() != null) {
			live().setKillWhenDone(killWhenDone);
		}
	}

	@Override
	public RenderSpellEffect getLiveEffect() {
		return live();
	}

	@Override
	public void removeWithoutDeath() {
		this.ended = true;
		if (live() != null) {
			live().removeWithoutDeath();
		}
	}
}
