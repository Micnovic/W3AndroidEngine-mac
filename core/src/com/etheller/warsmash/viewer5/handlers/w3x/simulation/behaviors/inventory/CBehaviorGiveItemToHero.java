package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.inventory;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CShopEvents;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilityShopPurhaseItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetStillAliveVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CAbstractRangedBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ResourceType;

public class CBehaviorGiveItemToHero extends CAbstractRangedBehavior {
	private final CAbilityInventory inventory;
	private CItem targetItem;
	private CUnit targetHero;

	public CBehaviorGiveItemToHero(final CUnit unit, final CAbilityInventory inventory) {
		super(unit);
		this.inventory = inventory;
	}

	public CBehavior reset(final CSimulation game, final CItem targetItem, final CUnit targetHero) {
		this.targetItem = targetItem;
		this.targetHero = targetHero;
		return innerReset(game, targetHero);
	}

	@Override
	public boolean isWithinRange(final CSimulation simulation) {
		if (receivesItems(simulation)) {
			// Measured on 1.29: the hand-over counts from the centers and needs the distance below the range.
			return distanceBetweenCenters() < simulation.getGameplayConstants().getGiveItemRange();
		}
		if (this.targetHero.getFirstAbilityOfType(CAbilityShopPurhaseItem.class) != null) {
			return this.unit.canReach(this.target, simulation.getGameplayConstants().getPawnItemRange());
		}
		// Measured on 1.29: a unit without an inventory or a shop gets the item dropped at its feet, from the drop range.
		return distanceBetweenCenters() <= simulation.getGameplayConstants().getDropItemRange();
	}

	private boolean receivesItems(final CSimulation simulation) {
		final CAbilityInventory targetInventory = this.targetHero.getInventoryData();
		return (targetInventory != null) && targetInventory.isCanGetItems()
				&& targetInventory.isRequirementsMet(simulation, this.targetHero);
	}

	private double distanceBetweenCenters() {
		final double dx = this.target.getX() - this.unit.getX();
		final double dy = this.target.getY() - this.unit.getY();
		return StrictMath.sqrt((dx * dx) + (dy * dy));
	}

	@Override
	public void endMove(final CSimulation game, final boolean interrupted) {
	}

	@Override
	public void begin(final CSimulation game) {
	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {
	}

	@Override
	public int getHighlightOrderId() {
		return OrderIds.dropitem;
	}

	@Override
	protected CBehavior update(final CSimulation simulation, final boolean withinFacingWindow) {
		if (receivesItems(simulation)) {
			// Measured on 1.29: the item keeps the position it had, it does not move to the new holder.
			this.inventory.dropItem(simulation, this.unit, this.targetItem, this.targetItem.getX(),
					this.targetItem.getY(), true);
			this.targetHero.getInventoryData().giveItem(simulation, this.targetHero, this.targetItem, false);
		}
		else {
			final CAbilityShopPurhaseItem shopPurchaseItemAbility = this.targetHero
					.getFirstAbilityOfType(CAbilityShopPurhaseItem.class);
			/*
			 * note: below: pawnable check is not enforced by ability targeting because of
			 * the dual targets "drop item @ unit" concept, which is a bit of a hack
			 */
			if ((shopPurchaseItemAbility != null) && !this.targetItem.isPawnable()) {
				simulation.getCommandErrorListener().showInterfaceError(this.unit.getPlayerIndex(),
						CommandStringErrorKeys.ITEM_CANNOT_BE_PAWNED);
			}
			else if (shopPurchaseItemAbility != null) {
				this.inventory.dropItem(simulation, this.unit, this.targetItem, this.target.getX(),
						this.target.getY(), true);
				final CPlayer player = simulation.getPlayer(this.unit.getPlayerIndex());

				final int goldCost = this.targetItem.getItemType().getGoldCost();
				if (goldCost != 0) {
					final int goldGained = simulation.getGameplayConstants().getPawnItemValue(goldCost);
					player.addGold(goldGained);
					simulation.unitGainResourceEvent(this.targetHero, player.getId(), ResourceType.GOLD, goldGained);
				}
				final int lumberCost = this.targetItem.getItemType().getLumberCost();
				if (lumberCost != 0) {
					final int lumberGained = simulation.getGameplayConstants().getPawnItemValue(lumberCost);
					player.addLumber(lumberGained);
					simulation.unitGainResourceEvent(this.targetHero, player.getId(), ResourceType.LUMBER,
							lumberGained);
				}
				CShopEvents.firePawnItemEvents(simulation, this.unit, this.targetItem, this.targetHero);
				simulation.removeItem(this.targetItem);
				simulation.unitSoundEffectEvent(this.targetHero, shopPurchaseItemAbility.getAlias());
			}
			else {
				this.inventory.dropItem(simulation, this.unit, this.targetItem, this.target.getX(),
						this.target.getY(), true);
			}
		}
		return this.unit.pollNextOrderBehavior(simulation);
	}

	@Override
	protected CBehavior updateOnInvalidTarget(final CSimulation simulation) {
		return this.unit.pollNextOrderBehavior(simulation);
	}

	@Override
	protected boolean checkTargetStillValid(final CSimulation simulation) {
		return this.target.visit(AbilityTargetStillAliveVisitor.INSTANCE);
	}

	@Override
	protected void resetBeforeMoving(final CSimulation simulation) {

	}

	@Override
	public boolean interruptable() {
		return true;
	}

	@Override
	public CBehaviorCategory getBehaviorCategory() {
		return CBehaviorCategory.MOVEMENT;
	}

}
