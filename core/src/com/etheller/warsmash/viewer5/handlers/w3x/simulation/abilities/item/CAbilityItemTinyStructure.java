package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import java.util.List;

import com.badlogic.gdx.math.Vector2;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.AbstractCAbilityBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityBuildInProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.AbilityDisableWhileUnderConstructionVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.BuildOnBuildingIntersector;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Tiny structures (Tiny Castle, Tiny Farm and the others): a structure of the user's race (Unit ID list in the
 * order human, orc, undead, night elf) is started at the target point and finishes after the ability's duration
 * instead of its own build time.
 */
public class CAbilityItemTinyStructure extends CAbilityPointTargetSpellBase {
	private List<String> structureTypes;
	private float buildTime;

	public CAbilityItemTinyStructure(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.structureTypes = worldEditorAbility.getFieldAsList(AbilityFields.UNIT_ID + level);
		this.buildTime = worldEditorAbility.getFieldAsFloat(AbilityFields.DURATION + level, 0);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.itemtinystructures;
	}

	private War3ID structureTypeFor(final CSimulation game, final CUnit caster) {
		final int raceIndex = game.getPlayer(caster.getPlayerIndex()).getRace().ordinal();
		final int index = Math.min(raceIndex, this.structureTypes.size() - 1);
		if (index < 0) {
			return null;
		}
		return War3ID.fromString(this.structureTypes.get(index));
	}

	@Override
	protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
			final AbilityPointTarget target, final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		final War3ID typeId = structureTypeFor(game, unit);
		final CUnitType unitType = typeId == null ? null : game.getUnitData().getUnitType(typeId);
		if (unitType == null) {
			receiver.orderIdNotAccepted();
			return;
		}
		final ArgbImage pathing = unitType.getBuildingPathingPixelMap();
		if (AbstractCAbilityBuild.isBuildLocationObstructed(game, unitType, pathing, unitType.isCanBeBuiltOnThem(),
				target.getX(), target.getY(), unit, BuildOnBuildingIntersector.INSTANCE.reset(target.getX(),
						target.getY()))) {
			receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_BUILD_THERE);
			return;
		}
		receiver.targetOk(target);
	}

	/** Measured on 1.29: the point is cut toward zero onto the 64 grid, then half a cell away from zero for odd sizes. */
	private static float gridPoint(final float value, final float offset) {
		final float point = ((int) (value / 64f)) * 64f;
		return value < 0 ? point - offset : point + offset;
	}

	private static Vector2 snappedPlace(final CSimulation simulation, final CUnit caster, final CUnitType unitType,
			final float x, final float y) {
		final ArgbImage pathing = unitType.getBuildingPathingPixelMap();
		if (pathing == null) {
			return new Vector2(x, y);
		}
		return new Vector2(gridPoint(x, ((pathing.getWidth() / 2) % 2) == 1 ? 32f : 0f),
				gridPoint(y, ((pathing.getHeight() / 2) % 2) == 1 ? 32f : 0f));
	}

	/** Measured on 1.29: the caster walks up to the structure's grid position, not to the point asked for. */
	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		final War3ID typeId = structureTypeFor(game, caster);
		final CUnitType unitType = typeId == null ? null : game.getUnitData().getUnitType(typeId);
		if (unitType == null) {
			return super.begin(game, caster, playerIndex, orderId, autoOrder, point);
		}
		final Vector2 place = snappedPlace(game, caster, unitType, point.getX(), point.getY());
		return super.begin(game, caster, playerIndex, orderId, autoOrder, new AbilityPointTarget(place.x, place.y));
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final War3ID typeId = structureTypeFor(simulation, caster);
		if (typeId == null) {
			return false;
		}
		final CUnitType unitType = simulation.getUnitData().getUnitType(typeId);
		final Vector2 place = snappedPlace(simulation, caster, unitType, target.getX(), target.getY());
		final CUnit structure = simulation.createUnit(typeId, caster.getPlayerIndex(), place.x, place.y,
				simulation.getGameplayConstants().getBuildingAngle(), true);
		if (structure == null) {
			return false;
		}
		AbstractCAbilityBuild.moveControlledUnitsAside(simulation, structure, caster, false);
		structure.setBuildTimeOverride(this.buildTime);
		final CAbilityBuildInProgress buildInProgress = new CAbilityBuildInProgress(
				simulation.getHandleIdAllocator().createId());
		structure.setFoodUsed(structure.getUnitType().getFoodUsed());
		structure.add(simulation, buildInProgress);
		for (final CAbility ability : structure.getAbilities()) {
			ability.visit(AbilityDisableWhileUnderConstructionVisitor.INSTANCE);
		}
		final CPlayer player = simulation.getPlayer(caster.getPlayerIndex());
		player.addTechtreeInProgress(simulation, typeId);
		simulation.unitConstructedEvent(caster, structure);
		structure.setLife(simulation,
				structure.getMaximumLife() * WarsmashConstants.BUILDING_CONSTRUCT_START_LIFE);
		return false;
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
