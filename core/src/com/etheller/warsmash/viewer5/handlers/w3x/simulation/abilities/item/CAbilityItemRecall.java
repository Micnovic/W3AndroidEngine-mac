package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * Amulet of Recall and Diamond of Summoning: up to Data A of the player's units within the area around the target
 * point go to the user, once the casting delay (Diamond of Summoning, Data B) has passed. Measured on 1.29: only a
 * point is a target, a unit is not.
 */
public class CAbilityItemRecall extends CAbilityPointTargetSpellBase {
	private final String clusteringField;
	private final String delayField;
	private final int baseOrderId;
	private int numberOfUnits;
	private boolean useClustering;
	private float castingDelay;
	private float areaOfEffect;
	private float missileSpeed;

	private int teleportTick;

	public CAbilityItemRecall(final int handleId, final War3ID alias, final int baseOrderId,
			final String clusteringField, final String delayField) {
		super(handleId, alias);
		this.baseOrderId = baseOrderId;
		this.clusteringField = clusteringField;
		this.delayField = delayField;
	}

	@Override
	public int getBaseOrderId() {
		return this.baseOrderId;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.numberOfUnits = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
		this.useClustering = worldEditorAbility.getFieldAsBoolean(this.clusteringField + level, 0);
		this.castingDelay = this.delayField == null ? 0
				: worldEditorAbility.getFieldAsFloat(this.delayField + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.missileSpeed = worldEditorAbility.getFieldAsFloat(AbilityFields.PROJECTILE_SPEED, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		if (this.castingDelay <= 0) {
			recall(simulation, caster, target);
			return false;
		}
		this.teleportTick = simulation.getGameTurnTick() + CTimer.ceilSteps(this.castingDelay);
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		if (simulation.getGameTurnTick() < this.teleportTick) {
			return true;
		}
		recall(simulation, caster, target);
		return false;
	}

	private void recall(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final float targetX = target.getX();
		final float targetY = target.getY();
		final List<CUnit> units = new ArrayList<>();
		simulation.getWorldCollision().enumUnitsInRange(targetX, targetY, this.areaOfEffect, (enumUnit) -> {
			if ((enumUnit != caster) && (enumUnit.getPlayerIndex() == caster.getPlayerIndex())
					&& !enumUnit.isBuilding() && !enumUnit.isDead() && !enumUnit.isHidden()
					&& !enumUnit.isMovementDisabled()) {
				units.add(enumUnit);
			}
			return false;
		});
		units.sort(Comparator.comparingInt(CUnit::getHandleId));
		// Measured on 1.29: the units are taken in a seeded random order, not in creation order.
		Collections.shuffle(units, simulation.getSeededRandom());
		while (units.size() > this.numberOfUnits) {
			units.remove(units.size() - 1);
		}
		final float casterX = caster.getX();
		final float casterY = caster.getY();
		simulation.spawnSpellEffectOnPoint(casterX, casterY, 0, getAlias(), CEffectType.CASTER, 0).remove();
		for (final CUnit unit : units) {
			simulation.spawnSpellEffectOnPoint(unit.getX(), unit.getY(), 0, getAlias(), CEffectType.SPECIAL, 0)
					.remove();
			final float startX = unit.getX();
			final float startY = unit.getY();
			final float offsetX = unit.getX() - targetX;
			final float offsetY = unit.getY() - targetY;
			unit.order(simulation, OrderIds.stop, null);
			if (this.useClustering) {
				unit.setPointAndCheckUnstuck(casterX, casterY, simulation);
			}
			else {
				unit.setPointAndCheckUnstuck(casterX + offsetX, casterY + offsetY, simulation);
			}
			simulation.unitRepositioned(unit);
			simulation.spawnSpellEffectOnPoint(unit.getX(), unit.getY(), 0, getAlias(), CEffectType.SPECIAL, 0)
					.remove();
			if (this.missileSpeed > 0) {
				// Measured on 1.29 (Diamond of Summoning): the units are hidden while their missile flies to the user.
				final float travel = (float) Math.sqrt((startX - casterX) * (startX - casterX) + (startY - casterY) * (startY - casterY));
				final CTimer arrival = new CTimer() {
					@Override
					public void onFire(final CSimulation game) {
						if (!unit.isDead()) {
							unit.setHidden(false);
						}
					}
				};
				unit.setHidden(true);
				arrival.setTimeoutTime(Math.max(0.05f, travel / this.missileSpeed));
				arrival.start(simulation);
			}
		}
	}

	@Override
	public boolean isMagic() {
		return false;
	}

	@Override
	public CAbilityCategory getAbilityCategory() {
		return CAbilityCategory.ITEM;
	}
}
