package com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.etheller.warsmash.parsers.fdf.datamodel.FrameTemplateEnvironment;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.BuffUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.IconUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.ItemUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.OrderButtonUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.UnitIconUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.commandbuttons.CommandCardActivationReceiverPreviewCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUpgradeType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.AbstractCAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityDisableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityGenericDoNothing;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.COrderButton;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.COrderButton.JassOrderButtonType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.AbstractCAbilityBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityBuildInProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityHumanBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityNagaBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityNeutralBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityNightElfBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityOrcBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityUndeadBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.combat.CAbilityColdArrows;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbilityGenericSingleIconPassiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.GenericNoIconAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.GenericSingleIconActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.GenericSingleIconPassiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityReturnResources;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.CAbilityItemSpellBook;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilityAwakenHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilityNeutralBuilding;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilitySellItems;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilitySellUnits;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.jass.CAbilityJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.nightelf.root.CAbilityRoot;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityQueue;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityRally;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityReviveHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.upgrade.CAbilityUpgrade;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

/**
 * Computes the command card of one unit for the local player from simulation state alone: the buttons with their
 * positions, icons, hotkeys, costs, cooldowns, autocast and charge numbers and whether each can be used, plus the
 * buff list of the unit. The ability logic is copied from the interface's own card population, so the result is what
 * that interface would show; the differences are only that no texture is read (icons are paths) and nothing is
 * drawn.
 * <p>
 * Writer: the thread that runs simulation steps, at the end of a step. Reads the simulation and the object data
 * tables only; changes nothing in the simulation.
 */
public final class CommandCardModelBuilder implements CAbilityVisitor<Void> {
	private static final int COMMAND_CARD_WIDTH = CommandCardModel.COLUMNS;
	private static final int COMMAND_CARD_HEIGHT = CommandCardModel.ROWS;
	private static final boolean ENABLE_PLACEHOLDERS = false;

	private final AbilityDataUI abilityDataUI;
	private final FrameTemplateEnvironment templates;
	private final CommandCardActivationReceiverPreviewCallback previewCallback = new CommandCardActivationReceiverPreviewCallback();

	private CSimulation game;
	private CUnit unit;
	private int menuBaseOrderId;
	private boolean multiSelect;
	private int localPlayerIndex;
	private boolean hasStop;
	private boolean hasCancel;
	private final Set<String> buffVisibleGroups = new HashSet<>();
	private int kindHint = CommandButtonModel.KIND_ABILITY;
	private final boolean[] occupied = new boolean[CommandCardModel.SLOTS];
	private CommandCardModel card;
	private UnitDetailsModel buffTarget;
	/** Textures of the script button being placed, which has no icon path. */
	private com.badlogic.gdx.graphics.Texture pendingTexture;
	private com.badlogic.gdx.graphics.Texture pendingTextureDisabled;
	/** The timed life bar of the last built unit (summons, illusions); the label is null when it has none. */
	private String timedLifeLabel;
	private float timedLifeRemaining;
	private float timedLifeMax;

	/**
	 * @param templates the interface's string templates ("REQUIRESTOOLTIP" and the like), read-only after loading
	 */
	public CommandCardModelBuilder(final AbilityDataUI abilityDataUI, final FrameTemplateEnvironment templates) {
		this.abilityDataUI = abilityDataUI;
		this.templates = templates;
	}

	/**
	 * Builds the card of a unit.
	 *
	 * @param subMenuOrderId order id of the open sub menu (build menu, hero skills, a spell book), 0 for the main card
	 * @param exitMenuOrderId order id of the menu one level up that the cancel button of a sub menu returns to, 0 for
	 *                       the main card
	 * @param multiSelect    whether more than one unit is selected (a group shows no build menu, no buffs)
	 * @param card           receives the buttons; cleared first
	 * @param buffs          receives the buff list when not null; its other fields are left alone
	 */
	public void build(final CSimulation game, final CUnit unit, final int subMenuOrderId, final int exitMenuOrderId,
			final boolean multiSelect, final int localPlayerIndex, final CommandCardModel card,
			final UnitDetailsModel buffs) {
		build(game, unit, subMenuOrderId, exitMenuOrderId, multiSelect, false, localPlayerIndex, card, buffs);
	}

	/**
	 * @param targeting whether a command waits for its target: the card then shows only the cancel button, which
	 *                  carries the order id of the open sub menu, and no buffs
	 */
	public void build(final CSimulation game, final CUnit unit, final int subMenuOrderId, final int exitMenuOrderId,
			final boolean multiSelect, final boolean targeting, final int localPlayerIndex, final CommandCardModel card,
			final UnitDetailsModel buffs) {
		card.clear();
		card.unitHandle = unit.getHandleId();
		this.card = card;
		begin(game, unit, subMenuOrderId, multiSelect, localPlayerIndex, buffs);
		for (int i = 0; i < this.occupied.length; i++) {
			this.occupied[i] = false;
		}
		final CAbilityHero heroAbility = unit.getHeroData();
		if (heroAbility != null) {
			card.heroAbilityHandle = heroAbility.getHandleId();
			card.heroSkillPoints = heroAbility.getSkillPoints();
			for (final War3ID skill : heroAbility.getSkillsAvailable()) {
				card.addHeroSkill(skill.getValue());
			}
		}
		if (targeting) {
			final IconUI cancelUI = this.abilityDataUI.getCancelUI();
			place(null, cancelUI.getIconPath(), cancelUI.getButtonPositionX(), cancelUI.getButtonPositionY(),
					subMenuOrderId, 0, false, false, true, cancelUI.getToolTip(), cancelUI.getUberTip(),
					cancelUI.getHotkey(), 0, 0, 0, 0, 0, 0, -1, false);
			end();
			return;
		}
		if (subMenuOrderId != 0) {
			final IconUI cancelUI = this.abilityDataUI.getCancelUI();
			place(null, cancelUI.getIconPath(), cancelUI.getButtonPositionX(), cancelUI.getButtonPositionY(),
					exitMenuOrderId, 0, false, false, true, cancelUI.getToolTip(), cancelUI.getUberTip(),
					cancelUI.getHotkey(), 0, 0, 0, 0, 0, 0, -1, false);
		}
		for (final CAbility ability : unit.getAbilities()) {
			if (!unit.isPaused() || (ability instanceof CBuff)
					|| (ability instanceof AbilityGenericSingleIconPassiveAbility)) {
				final int containerMenuOrderId = ability.getContainerMenuOrderId();
				if (containerMenuOrderId == 0) {
					ability.visit(this);
				}
				else if (containerMenuOrderId == subMenuOrderId) {
					// The spells of a spell book are on its menu; they show as if on the main card.
					this.menuBaseOrderId = 0;
					ability.visit(this);
					this.menuBaseOrderId = subMenuOrderId;
				}
			}
		}
		end();
	}

	/** Fills only the buff list of a unit (for the hero bar and the unit under the pointer). */
	public void collectBuffs(final CSimulation game, final CUnit unit, final int localPlayerIndex,
			final UnitDetailsModel buffs) {
		this.card = null;
		begin(game, unit, 0, false, localPlayerIndex, buffs);
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof CBuff) {
				ability.visit(this);
			}
		}
		end();
	}

	private void begin(final CSimulation game, final CUnit unit, final int menuBaseOrderId, final boolean multiSelect,
			final int localPlayerIndex, final UnitDetailsModel buffs) {
		this.game = game;
		this.unit = unit;
		this.menuBaseOrderId = menuBaseOrderId;
		this.multiSelect = multiSelect;
		this.localPlayerIndex = localPlayerIndex;
		this.hasStop = false;
		this.hasCancel = false;
		this.buffVisibleGroups.clear();
		this.buffTarget = buffs;
		this.kindHint = CommandButtonModel.KIND_ABILITY;
		if (this.card != null) {
			this.timedLifeLabel = null;
		}
		if (buffs != null) {
			buffs.buffCount = 0;
			buffs.timedLifeLabel = null;
		}
		this.previewCallback.setup(game.getUnitData(), game.getUpgradeData(), this.templates);
	}

	private void end() {
		this.game = null;
		this.unit = null;
		this.card = null;
		this.buffTarget = null;
		this.previewCallback.setup(null, null, null);
	}

	public String getTimedLifeLabel() {
		return this.timedLifeLabel;
	}

	public float getTimedLifeRemaining() {
		return this.timedLifeRemaining;
	}

	public float getTimedLifeMax() {
		return this.timedLifeMax;
	}

	@Override
	public Void accept(final AbilityBuilderActiveAbility ability) {
		if ((this.menuBaseOrderId == 0 || this.menuBaseOrderId == ability.getIconVisibleMenuId())
				&& ability.isIconShowing()) {
			final AbilityUI ui = this.abilityDataUI.getUI(ability.getAlias());
			if (ui == null) {
				// An ability the object data has no interface row for (a map's own hidden helper) has no button.
				return null;
			}
			final boolean autoCastOn = ability.isAutoCastOn();
			if (ability.isSeparateOnAndOff()) {
				final War3ID onTt = ability.getOnTooltipOverride();
				if ((onTt == null) || (onTt == ability.getAlias())) {
					addCommandButton(ability, ui.getOnIconUI(ability.getLevel() - 1), ability.getHandleId(),
							ability.getBaseOrderId(),
							autoCastOn ? ability.getAutoCastOffOrderId() : ability.getAutoCastOnOrderId(), autoCastOn,
							ability.isMenuAbility(), ability.getUIGoldCost(), ability.getUILumberCost(),
							ability.getUIFoodCost(), ability.getUIManaCost(), ability.getUsesRemaining());
				} else {
					addCommandButton(ability, ui.getOnIconUI(ability.getLevel() - 1),
							resolveUnknownIcon(onTt, false, ability.getLevel() - 1), ability.getHandleId(),
							ability.getBaseOrderId(),
							autoCastOn ? ability.getAutoCastOffOrderId() : ability.getAutoCastOnOrderId(), autoCastOn,
							ability.isMenuAbility(), ability.getUIGoldCost(), ability.getUILumberCost(),
							ability.getUIFoodCost(), ability.getUIManaCost(), ability.getUsesRemaining());
				}

				final War3ID offTt = ability.getOffTooltipOverride();
				if ((offTt == null) || (offTt == ability.getAlias())) {
					addCommandButton(ability, ui.getOffIconUI(ability.getLevel() - 1), ability.getHandleId(),
							ability.getOffOrderId(),
							autoCastOn ? ability.getAutoCastOffOrderId() : ability.getAutoCastOnOrderId(), autoCastOn,
							ability.isMenuAbility(), ability.getUIGoldCost(), ability.getUILumberCost(),
							ability.getUIFoodCost(), ability.getUIManaCost(), -1);
				} else {
					addCommandButton(ability, ui.getOffIconUI(ability.getLevel() - 1),
							resolveUnknownIcon(offTt, true, ability.getLevel() - 1), ability.getHandleId(),
							ability.getOffOrderId(),
							autoCastOn ? ability.getAutoCastOffOrderId() : ability.getAutoCastOnOrderId(), autoCastOn,
							ability.isMenuAbility(), ability.getUIGoldCost(), ability.getUILumberCost(),
							ability.getUIFoodCost(), ability.getUIManaCost(), -1);
				}
			} else {
				final boolean active = ability.isToggleOn();
				War3ID tt = null;
				if (active) {
					tt = ability.getOnTooltipOverride();
				} else {
					tt = ability.getOffTooltipOverride();
				}
				if ((tt == null) || (tt == ability.getAlias())) {
					addCommandButton(ability,
							active ? ui.getOffIconUI(ability.getLevel() - 1) : ui.getOnIconUI(ability.getLevel() - 1),
							ability.getHandleId(), active ? ability.getOffOrderId() : ability.getUICastOrderId(),
							autoCastOn ? ability.getAutoCastOffOrderId() : ability.getAutoCastOnOrderId(), autoCastOn,
							ability.isMenuAbility(), ability.getUIGoldCost(), ability.getUILumberCost(),
							ability.getUIFoodCost(), ability.getUIManaCost(), active ? -1 : ability.getUsesRemaining());
				} else {
					addCommandButton(ability,
							active ? ui.getOffIconUI(ability.getLevel() - 1) : ui.getOnIconUI(ability.getLevel() - 1),
							resolveUnknownIcon(tt, active, ability.getLevel() - 1), ability.getHandleId(),
							active ? ability.getOffOrderId() : ability.getUICastOrderId(),
							autoCastOn ? ability.getAutoCastOffOrderId() : ability.getAutoCastOnOrderId(), autoCastOn,
							ability.isMenuAbility(), ability.getUIGoldCost(), ability.getUILumberCost(),
							ability.getUIFoodCost(), ability.getUIManaCost(), active ? -1 : ability.getUsesRemaining());
				}
			}
		}
		return null;
	}

	private IconUI resolveUnknownIcon(final War3ID id, final boolean active, final int index) {
		final AbilityUI aui = this.abilityDataUI.getUI(id);
		IconUI icon = null;
		if (aui == null) {
			icon = this.abilityDataUI.getUnitUI(id);
			if (icon == null) {
				icon = this.abilityDataUI.getUpgradeUI(id, index);
			}
			if (icon == null) {
				final ItemUI item = this.abilityDataUI.getItemUI(id);
				if (item != null) {
					icon = item.getIconUI();
				}
			}
			if (icon == null) {
				final BuffUI buff = this.abilityDataUI.getBuffUI(id);
				if (buff != null) {
					icon = buff.getOnIconUI();
				}
			}
		} else {
			if (active) {
				icon = aui.getOffIconUI(index);
			} else {
				icon = aui.getOnIconUI(index);
			}
		}
		return icon;
	}

	@Override
	public Void accept(final CAbilityAttack ability) {
		if ((this.menuBaseOrderId == 0) && ability.isIconShowing()) {
			boolean attackGroundEnabled = false;
			boolean showUI = false;
			for (final CUnitAttack attack : this.unit.getCurrentAttacks()) {
				if (attack.getWeaponType().isAttackGroundSupported()) {
					attackGroundEnabled = true;
				}
				if (attack.isShowUI()) {
					showUI = true;
				}
			}
			if (showUI) {
				addCommandButton(ability, this.abilityDataUI.getAttackUI(), ability.getHandleId(), OrderIds.attack, 0,
						false, false);
				if (attackGroundEnabled) {
					addCommandButton(ability, this.abilityDataUI.getAttackGroundUI(), ability.getHandleId(),
							OrderIds.attackground, 0, false, false);
				}
				if (!this.hasStop) {
					this.hasStop = true;
					addCommandButton(ability, this.abilityDataUI.getStopUI(), 0, OrderIds.stop, 0, false, false);
				}
			}
		}
		return null;
	}

	@Override
	public Void accept(final CAbilityMove ability) {
		if ((this.menuBaseOrderId == 0) && ability.isIconShowing()) {
			addCommandButton(ability, this.abilityDataUI.getMoveUI(), ability.getHandleId(), OrderIds.move, 0, false,
					false);
			addCommandButton(ability, this.abilityDataUI.getHoldPosUI(), 0, OrderIds.holdposition, 0, false, false);
			addCommandButton(ability, this.abilityDataUI.getPatrolUI(), ability.getHandleId(), OrderIds.patrol, 0,
					false, false);
			if (!this.hasStop) {
				this.hasStop = true;
				addCommandButton(ability, this.abilityDataUI.getStopUI(), 0, OrderIds.stop, 0, false, false);
			}
		}
		return null;
	}

	@Override
	public Void accept(final CAbilityGenericDoNothing ability) {
		if (ENABLE_PLACEHOLDERS) {
			if ((this.menuBaseOrderId == 0) && ability.isIconShowing()) {
				final AbilityUI abilityUI = this.abilityDataUI.getUI(ability.getAlias());
				if (abilityUI != null) {
					addCommandButton(ability, abilityUI.getOnIconUI(ability.getLevel() - 1), ability.getHandleId(), 0,
							0, false, false);
				} else {
					addCommandButton(ability, this.abilityDataUI.getStopUI(), ability.getHandleId(), 0, 0, false,
							false);
				}
			}
		}
		return null;
	}

	@Override
	public Void accept(final GenericSingleIconActiveAbility ability) {
		if (ability instanceof CAbilityItemSpellBook) {
			// Measured: the book has no button on the command card; its menu opens from the inventory slot.
			return null;
		}
		if ((this.menuBaseOrderId == 0) && ability.isIconShowing()) {
			final AbilityUI ui = this.abilityDataUI.getUI(ability.getAlias());
			if (ui == null) {
				// An ability the object data has no interface row for (a map's own hidden helper) has no button.
				return null;
			}
			final boolean autoCastOn = ability.isAutoCastOn();
			addCommandButton(ability,
					ability.isToggleOn() ? ui.getOffIconUI(ability.getLevel() - 1)
							: ui.getOnIconUI(ability.getLevel() - 1),
					ability.getHandleId(), ability.getBaseOrderId(),
					autoCastOn ? ability.getAutoCastOffOrderId() : ability.getAutoCastOnOrderId(), autoCastOn, false,
					ability.getUIGoldCost(), ability.getUILumberCost(), ability.getUIFoodCost(),
					ability.getUIManaCost(), ability.getUsesRemaining());
		}
		return null;
	}

	@Override
	public Void accept(final GenericSingleIconPassiveAbility ability) {
		// Passives that wait for a research (Pillage, research indicators) stay hidden until it is done.
		if ((ability instanceof AbstractCAbility)
				&& ((AbstractCAbility) ability).isDisabled(CAbilityDisableType.REQUIREMENTS)) {
			return null;
		}
		if ((this.menuBaseOrderId == 0) && ability.isIconShowing()) {
			final AbilityUI abilityUI = this.abilityDataUI.getUI(ability.getAlias());
			if (abilityUI != null) {
				addCommandButton(ability, abilityUI.getOnIconUI(ability.getLevel() - 1), ability.getHandleId(), 0, 0,
						false, false);
			}
		}
		return null;
	}

	@Override
	public Void accept(final CBuff ability) {
		final BuffUI buffUI = this.abilityDataUI.getBuffUI(ability.getAlias());
		if (buffUI != null) {
			addBuffIcon(ability, buffUI.getOnIconUI());
		}
		return null;
	}

	@Override
	public Void accept(final CAbilityRoot ability) {
		return accept((GenericSingleIconActiveAbility) ability);
	}

	@Override
	public Void accept(final GenericNoIconAbility ability) {
		return null;
	}

	@Override
	public Void accept(final CAbilityReturnResources ability) {
		return null;
	}

	@Override
	public Void accept(final CAbilityNeutralBuilding ability) {
		if (this.menuBaseOrderId == 0) {
			if (ability.isShowSelectUnitButton()) {
				addCommandButton(ability, this.abilityDataUI.getNeutralInteractUI(), ability.getHandleId(),
						OrderIds.neutralinteract, 0, false, false);
			}
		}
		return null;
	}

	@Override
	public Void accept(final CAbilityRally ability) {
		if (this.menuBaseOrderId == 0) {
			addCommandButton(ability, this.abilityDataUI.getRallyUI(), ability.getHandleId(), ability.getBaseOrderId(),
					0, false, false);
		}
		return null;
	}

	@Override
	public Void accept(final CAbilityColdArrows ability) {
		if ((this.menuBaseOrderId == 0) && ability.isIconShowing()) {
			final boolean autoCastActive = ability.isAutoCastActive();
			int autoCastId;
			if (autoCastActive) {
				autoCastId = OrderIds.coldarrows;
			} else {
				autoCastId = OrderIds.uncoldarrows;
			}
			final AbilityUI autoCastUI = this.abilityDataUI.getUI(ability.getAlias());
			if (autoCastUI == null) {
				return null;
			}
			final IconUI onIconUI = autoCastUI.getOnIconUI(ability.getLevel() - 1);
			addCommandButton(ability, onIconUI, ability.getHandleId(), OrderIds.coldarrowstarg, autoCastId,
					autoCastActive, false);
		}
		return null;
	}

	@Override
	public Void accept(final CAbilityJass ability) {
		if (ability.isIconShowing()) {
			for (final COrderButton order : ability.getOrderButtons()) {
				if ((this.menuBaseOrderId == order.getContainerMenuOrderId()) && !order.isHidden()) {
					final OrderButtonUI renderPeer = this.abilityDataUI.getRenderPeer(order);
					if (renderPeer == null) {
						continue;
					}
					this.pendingTexture = renderPeer.getIcon();
					this.pendingTextureDisabled = renderPeer.getIconDisabled();
					addCommandButton(ability, null, renderPeer.getTip(),
							renderPeer.getUberTip(), renderPeer.getButtonPositionX(), renderPeer.getButtonPositionY(),
							ability.getHandleId(), order.getOrderId(),
							order.isAutoCastActive() ? order.getAutoCastOrderId() : order.getAutoCastUnOrderId(),
							order.isAutoCastActive(), order.getType() == JassOrderButtonType.MENU, order.getGoldCost(),
							order.getLumberCost(), order.getFoodCost(), order.getManaCost(), order.getCharges(),
							renderPeer.getHotkey());
					this.pendingTexture = null;
					this.pendingTextureDisabled = null;
				}
			}
		}
		return null;
	}

	@Override
	public Void accept(final CAbilityOrcBuild ability) {
		handleBuildMenu(ability, this.abilityDataUI.getBuildOrcUI());
		return null;
	}

	@Override
	public Void accept(final CAbilityHumanBuild ability) {
		handleBuildMenu(ability, this.abilityDataUI.getBuildHumanUI());
		return null;
	}

	@Override
	public Void accept(final CAbilityNightElfBuild ability) {
		handleBuildMenu(ability, this.abilityDataUI.getBuildNightElfUI());
		return null;
	}

	@Override
	public Void accept(final CAbilityUndeadBuild ability) {
		handleBuildMenu(ability, this.abilityDataUI.getBuildUndeadUI());
		return null;
	}

	@Override
	public Void accept(final CAbilityNagaBuild ability) {
		handleBuildMenu(ability, this.abilityDataUI.getBuildNagaUI());
		return null;
	}

	@Override
	public Void accept(final CAbilityNeutralBuild ability) {
		handleBuildMenu(ability, this.abilityDataUI.getBuildNeutralUI());
		return null;
	}

	private void handleBuildMenu(final AbstractCAbilityBuild ability, final IconUI buildUI) {
		if ((this.menuBaseOrderId == ability.getBaseOrderId()) && ability.isIconShowing()) {
			for (final War3ID unitType : ability.getStructuresBuilt()) {
				final IconUI unitUI = this.abilityDataUI.getUnitUI(unitType);
				if (unitUI != null) {
					final CUnitType simulationUnitType = this.game.getUnitData().getUnitType(unitType);
					addCommandButton(ability, unitUI, ability.getHandleId(), unitType.getValue(), 0, false, false,
							simulationUnitType.getGoldCost(), simulationUnitType.getLumberCost(),
							simulationUnitType.getFoodUsed(), 0, -1);
				}
			}
		} else if (this.menuBaseOrderId == 0) {
			if (this.multiSelect) {
				return;
			}
			if (ability.isIconShowing()) {
				addCommandButton(ability, buildUI, ability.getHandleId(), ability.getBaseOrderId(), 0, false, true);
			}
		}
	}

	private void addCommandButton(final CAbility ability, final IconUI iconUI, final int handleId, final int orderId,
			final int autoCastOrderId, final boolean autoCastActive, final boolean menuButton) {
		if (iconUI == null) {
			return;
		}
		addCommandButton(ability, iconUI, handleId, orderId, autoCastOrderId, autoCastActive, menuButton, 0, 0, 0, 0,
				-1);
	}

	private void addCommandButton(final CAbility ability, final IconUI iconUI, final int handleId, final int orderId,
			final int autoCastOrderId, final boolean autoCastActive, final boolean menuButton, final int goldCost,
			final int lumberCost, final int foodCost, final int manaCost, final int numberOverlay) {
		if (iconUI == null) {
			// An ability without an interface row in the object data has no button.
			return;
		}
		addCommandButton(ability, iconUI, iconUI.getToolTip(), iconUI.getButtonPositionX(), iconUI.getButtonPositionY(),
				handleId, orderId, autoCastOrderId, autoCastActive, menuButton, goldCost, lumberCost, foodCost,
				manaCost, numberOverlay);
	}

	private void addCommandButton(final CAbility ability, final IconUI iconUI, final IconUI tooltipOverride,
			final int handleId, final int orderId, final int autoCastOrderId, final boolean autoCastActive,
			final boolean menuButton, final int goldCost, final int lumberCost, final int foodCost, final int manaCost,
			final int numberOverlay) {
		if ((iconUI == null) || (tooltipOverride == null)) {
			return;
		}
		addCommandButton(ability, iconUI.getIconPath(), tooltipOverride.getToolTip(),
				tooltipOverride.getUberTip(), iconUI.getButtonPositionX(), iconUI.getButtonPositionY(), handleId,
				orderId, autoCastOrderId, autoCastActive, menuButton, goldCost, lumberCost, foodCost, manaCost,
				numberOverlay, iconUI.getHotkey());
	}

	private void addCommandButton(final CAbility ability, final IconUI iconUI, final String toolTip,
			final int buttonPosX, final int buttonPosY, final int handleId, final int orderId,
			final int autoCastOrderId, final boolean autoCastActive, final boolean menuButton, final int goldCost,
			final int lumberCost, final int foodCost, final int manaCost, final int numberOverlay) {
		if (iconUI == null) {
			return;
		}
		addCommandButton(ability, iconUI.getIconPath(), toolTip, iconUI.getUberTip(), buttonPosX,
				buttonPosY, handleId, orderId, autoCastOrderId, autoCastActive, menuButton, goldCost, lumberCost,
				foodCost, manaCost, numberOverlay, iconUI.getHotkey());
	}

	private void addCommandButton(final CAbility ability, final String iconPath, final String toolTip,
			String uberTip, final int buttonPosX, final int buttonPosY, final int handleId, final int orderId,
			final int autoCastOrderId, final boolean autoCastActive, final boolean menuButton, int goldCost,
			int lumberCost, int foodCost, int manaCost, final int numberOverlay, final char hotkey) {
		boolean requiresPatron = false;
		if (this.unit.getPlayerIndex() != this.localPlayerIndex) {
			boolean controlShared = this.game.getPlayer(this.unit.getPlayerIndex()).hasAlliance(this.localPlayerIndex,
					CAllianceType.SHARED_CONTROL);
			if (!controlShared) {
				final CAbilityNeutralBuilding neutralBuildingData = this.unit.getNeutralBuildingData();
				if (neutralBuildingData != null) {
					final CUnit selectedPlayerUnit = neutralBuildingData.getSelectedPlayerUnit(this.localPlayerIndex);
					if (selectedPlayerUnit != null) {
						controlShared = true;
					} else {
						requiresPatron = true;
					}
				}
			}
			if (!controlShared && !requiresPatron) {
				return;
			}
		}
		ability.checkCanUse(this.game, this.unit, this.localPlayerIndex, orderId, false, this.previewCallback.reset());
		if (!this.previewCallback.isOmitIconEntirely()) {
			if (requiresPatron) {
				this.previewCallback.missingRequirement(this.templates.getDecoratedString("REQUIRESNEARPATRON"));
			}
			final boolean active = (this.unit.getCurrentBehavior() != null)
					&& (orderId == this.unit.getCurrentBehavior().getHighlightOrderId());
			final boolean disabled = ((ability != null) && ability.isDisabled()) || this.previewCallback.isDisabled();
			final boolean clickDisabled = ((ability != null) && ability.isClickDisabled())
					|| this.previewCallback.isDisabled();
			final float cooldownRemaining = this.previewCallback.getCooldownRemaining();
			final float cooldownMax = this.previewCallback.getCooldownMax();
			if (disabled || this.previewCallback.isOutOfStock()) {
				// dont show these on disabled
				goldCost = 0;
				lumberCost = 0;
				foodCost = 0;
				manaCost = 0;
			}
			if (this.previewCallback.isOutOfStock()) {
				uberTip = "|cffaaaaaa|r" + this.templates.getDecoratedString("COOLDOWNSTOCKTOOLTIP") + "|r|n"
						+ uberTip;
			}
			if (this.previewCallback.isShowingRequirements()) {
				uberTip = this.previewCallback.getRequirementsText() + "|r" + uberTip;
			}
			place(ability, iconPath, buttonPosX, buttonPosY, orderId, autoCastOrderId, active, autoCastActive,
					menuButton, toolTip, uberTip, hotkey, goldCost, lumberCost, foodCost, manaCost, cooldownRemaining,
					cooldownMax, numberOverlay, disabled || clickDisabled);
		}
	}

	/** Where the interface puts a button: its own slot, else the nearest free one before it, else the first free. */
	private void place(final CAbility ability, final String iconPath, final int buttonPositionX,
			final int buttonPositionY, final int orderId, final int autoCastId, final boolean active, final boolean autoCastActive, final boolean menuButton,
			final String tip, final String uberTip, final char hotkey, final int goldCost, final int lumberCost,
			final int foodCost, final int manaCost, final float cooldownRemaining, final float cooldownMax,
			final int numberOverlay, final boolean unusable) {
		if (this.card == null) {
			return;
		}
		if ((buttonPositionX == -11) || (buttonPositionY == -11)) {
			// NOTE some guys said they liked to do this as a hack to hide icons or whatever
			return;
		}
		int x = Math.max(0, Math.min(COMMAND_CARD_WIDTH - 1, buttonPositionX));
		int y = Math.max(0, Math.min(COMMAND_CARD_HEIGHT - 1, buttonPositionY));
		while ((x >= 0) && (y >= 0) && this.occupied[x + (y * COMMAND_CARD_WIDTH)]) {
			x--;
			if ((x < 0) && (y != 0)) {
				x = COMMAND_CARD_WIDTH - 1;
				y--;
			}
		}
		if ((y < 0) || (x < 0)) {
			if (y < 0) {
				y = 0;
			}
			if (x < 0) {
				x = 0;
			}
			while ((x < COMMAND_CARD_WIDTH) && (y < COMMAND_CARD_HEIGHT) && this.occupied[x + (y * COMMAND_CARD_WIDTH)]) {
				x++;
				if (x >= COMMAND_CARD_WIDTH) {
					x = 0;
					y++;
				}
			}
		}
		if ((x >= COMMAND_CARD_WIDTH) || (x < 0) || (y >= COMMAND_CARD_HEIGHT) || (y < 0)) {
			return;
		}
		final CommandButtonModel button = this.card.nextButton();
		if (button == null) {
			return;
		}
		this.occupied[x + (y * COMMAND_CARD_WIDTH)] = true;
		button.slot = x + (y * COMMAND_CARD_WIDTH);
		button.kind = menuButton ? CommandButtonModel.KIND_MENU : kindOf(ability, orderId);
		button.id = orderId;
		button.abilityHandleId = ability == null ? 0 : ability.getHandleId();
		button.abilityAlias = ability == null || ability.getAlias() == null ? 0 : ability.getAlias().getValue();
		button.autoCastOrderId = autoCastId;
		button.iconPath = iconPath;
		button.iconTexture = this.pendingTexture;
		button.iconTextureDisabled = this.pendingTextureDisabled;
		button.enabled = !unusable;
		final boolean notEnoughMana = this.unit.getMana() < manaCost;
		if (unusable) {
			button.disabledReason = this.previewCallback.isShowingRequirements() ? CommandButtonModel.REQUIREMENTS
					: (cooldownRemaining > 0 ? CommandButtonModel.COOLDOWN : CommandButtonModel.DISABLED_OTHER);
		}
		else if (notEnoughMana) {
			button.disabledReason = CommandButtonModel.NOT_ENOUGH_MANA;
		}
		else {
			button.disabledReason = CommandButtonModel.NONE;
		}
		button.autocastable = autoCastId != 0;
		button.autocastOn = autoCastActive;
		button.toggledOn = active;
		button.cooldownRemaining = cooldownRemaining;
		button.cooldownTotal = cooldownMax;
		button.charges = numberOverlay;
		button.goldCost = goldCost;
		button.lumberCost = lumberCost;
		button.foodCost = foodCost;
		button.manaCost = manaCost;
		button.hotkey = hotkey;
		TooltipModelBuilder.button(button.tooltip, tip, uberTip, goldCost, lumberCost, foodCost, manaCost,
				cooldownMax, hotkey);
	}

	private int kindOf(final CAbility ability, final int orderId) {
		if (ability instanceof AbstractCAbilityBuild) {
			return CommandButtonModel.KIND_BUILD;
		}
		if (ability instanceof CAbilityQueue) {
			return orderId == OrderIds.cancel ? CommandButtonModel.KIND_ORDER : this.kindHint;
		}
		if ((ability instanceof CAbilityUpgrade) || (ability instanceof CAbilityReviveHero)
				|| (ability instanceof CAbilityAwakenHero) || (ability instanceof CAbilitySellUnits)) {
			return orderId == OrderIds.cancel ? CommandButtonModel.KIND_ORDER : CommandButtonModel.KIND_TRAIN;
		}
		if (ability instanceof CAbilitySellItems) {
			return CommandButtonModel.KIND_ITEM;
		}
		if ((ability instanceof CAbilityAttack) || (ability instanceof CAbilityMove)
				|| (ability instanceof CAbilityRally) || (ability instanceof CAbilityNeutralBuilding)
				|| (ability instanceof CAbilityBuildInProgress)) {
			return CommandButtonModel.KIND_ORDER;
		}
		return CommandButtonModel.KIND_ABILITY;
	}

	private void addBuff(final CBuff buff, final float remaining, final IconUI iconUI) {
		final UnitDetailsModel target = this.buffTarget;
		if ((target != null) && (target.buffCount < UnitDetailsModel.MAX_BUFFS)) {
			final int index = target.buffCount;
			target.buffIds[index] = buff.getAlias().getValue();
			target.buffRemaining[index] = remaining;
			target.buffIconPaths[index] = iconUI.getIconPath();
			target.buffTips[index] = iconUI.getToolTip();
			target.buffUberTips[index] = iconUI.getUberTip();
			target.buffPositive[index] = buff.isPositive();
			target.buffLeveled[index] = buff.isLeveled();
			target.buffLevels[index] = buff.getLevel();
			target.buffCount++;
		}
	}

	private void addBuffIcon(final CBuff buff, final IconUI iconUI) {
		if (!this.multiSelect) {
			if (buff.isTimedLifeBar()) {
				if (this.unit.getPlayerIndex() == this.localPlayerIndex) {
					final float remaining = buff.getDurationRemaining(this.game, this.unit);
					if (this.card != null) {
						this.timedLifeLabel = iconUI.getToolTip();
						this.timedLifeRemaining = remaining;
						this.timedLifeMax = buff.getDurationMax();
						if (this.buffTarget != null) {
							this.buffTarget.timedLifeLabel = this.timedLifeLabel;
							this.buffTarget.timedLifeRemaining = remaining;
							this.buffTarget.timedLifeMax = this.timedLifeMax;
						}
					}
					if (this.unit.isIllusion() && (iconUI != null)) {
						addBuff(buff, remaining, iconUI);
					}
				}
			} else {
				if (buff.isIconShowing()) {
					String group = buff.getVisibilityGroup();
					if (group != null && this.buffVisibleGroups.contains(group)) {
						return;
					}
					if (iconUI != null) {
						addBuff(buff, 0, iconUI);
					}
					this.buffVisibleGroups.add(group);
				}
			}
		}
	}

	@Override
	public Void accept(final CAbilityBuildInProgress ability) {
		if (this.menuBaseOrderId == 0) {
			addCommandButton(ability, this.abilityDataUI.getCancelBuildUI(), ability.getHandleId(), OrderIds.cancel, 0,
					false, false);
		}
		return null;
	}

	@Override
	public Void accept(final CAbilityReviveHero ability) {
		if ((this.menuBaseOrderId == 0) && ability.isIconShowing()) {
			int heroIndex = 0;
			for (final CUnit playerHero : this.game.getPlayerHeroes(this.unit.getPlayerIndex())) {
				final CAbilityHero heroData = playerHero.getHeroData();
				if (playerHero.isDead() && (heroData != null) && heroData.isAwaitingRevive()
						&& !heroData.isReviving()) {

					final UnitIconUI unitUI = this.abilityDataUI.getUnitUI(playerHero.getTypeId());
					if (unitUI != null) {
						final CUnitType simulationUnitType = playerHero.getUnitType();
						final int goldCost = this.game.getGameplayConstants()
								.getHeroReviveGoldCost(simulationUnitType.getGoldCost(), heroData.getHeroLevel());
						final int lumberCost = this.game.getGameplayConstants()
								.getHeroReviveLumberCost(simulationUnitType.getLumberCost(), heroData.getHeroLevel());
						addCommandButton(ability, unitUI, unitUI.getReviveTip() + " - " + heroData.getProperName(),
								heroIndex++, 0, ability.getHandleId(), playerHero.getHandleId(), 0, false, false,
								goldCost, lumberCost, simulationUnitType.getFoodUsed(), 0,
								playerHero.getHeroData().getHeroLevel());
					}
				}
			}
			if (this.unit.getBuildQueueTypes()[0] != null) {
				if (!this.hasCancel) {
					this.hasCancel = true;
					addCommandButton(ability, this.abilityDataUI.getCancelTrainUI(), ability.getHandleId(),
							OrderIds.cancel, 0, false, false);
				}
			}
		}
		return null;
	}

	@Override
	public Void accept(final CAbilityQueue ability) {
		if ((this.menuBaseOrderId == 0) && ability.isIconShowing()) {
			this.kindHint = CommandButtonModel.KIND_TRAIN;
			for (final War3ID unitType : ability.getUnitsTrained()) {
				final IconUI unitUI = this.abilityDataUI.getUnitUI(unitType);
				if (unitUI != null) {
					final CUnitType simulationUnitType = this.game.getUnitData().getUnitType(unitType);
					int goldCost, lumberCost;
					if (simulationUnitType.isHero()
							&& (this.game.getPlayer(this.unit.getPlayerIndex()).getHeroTokens() > 0)) {
						goldCost = 0;
						lumberCost = 0;
					} else {
						goldCost = simulationUnitType.getGoldCost();
						lumberCost = simulationUnitType.getLumberCost();
					}
					addCommandButton(ability, unitUI, ability.getHandleId(), unitType.getValue(), 0, false, false,
							goldCost, lumberCost, simulationUnitType.getFoodUsed(), 0, -1);
				}
			}
			this.kindHint = CommandButtonModel.KIND_RESEARCH;
			for (final War3ID unitType : ability.getResearchesAvailable()) {
				final CPlayer player = this.game.getPlayer(this.unit.getPlayerIndex());
				final int unlockCount = player.getTechtreeUnlocked(unitType);
				final IconUI unitUI = this.abilityDataUI.getUpgradeUI(unitType, unlockCount);
				if (unitUI != null) {
					final CUpgradeType simulationUpgradeType = this.game.getUpgradeData().getType(unitType);
					final int goldCost = simulationUpgradeType.getGoldCost(unlockCount);
					final int lumberCost = simulationUpgradeType.getLumberCost(unlockCount);
					addCommandButton(ability, unitUI, ability.getHandleId(), unitType.getValue(), 0, false, false,
							goldCost, lumberCost, 0, 0, -1);
				}
			}
			if (this.unit.getBuildQueueTypes()[0] != null) {
				if (!this.hasCancel) {
					this.hasCancel = true;
					addCommandButton(ability, this.abilityDataUI.getCancelTrainUI(), ability.getHandleId(),
							OrderIds.cancel, 0, false, false);
				}
			}
		}
		return null;
	}

	@Override
	public Void accept(final CAbilitySellItems ability) {
		if ((this.menuBaseOrderId == 0) && ability.isIconShowing()) {
			for (final War3ID unitType : ability.getItemsSold()) {
				final ItemUI soldItemUI = this.abilityDataUI.getItemUI(unitType);
				final IconUI unitUI = soldItemUI == null ? null : soldItemUI.getIconUI();
				if (unitUI != null) {
					final CItemType simulationUnitType = this.game.getItemData().getItemType(unitType);
					int goldCost, lumberCost;
					goldCost = simulationUnitType.getGoldCost();
					lumberCost = simulationUnitType.getLumberCost();
					final int scriptStockSlot = ability.getScriptStockSlot(unitType);
					if (scriptStockSlot >= 0) {
						addCommandButton(ability, unitUI, unitUI.getToolTip(), scriptStockSlot % COMMAND_CARD_WIDTH,
								scriptStockSlot / COMMAND_CARD_WIDTH, ability.getHandleId(), unitType.getValue(), 0,
								false, false, goldCost, lumberCost, 0, 0, ability.getStock(unitType));
					}
					else {
						addCommandButton(ability, unitUI, ability.getHandleId(), unitType.getValue(), 0, false, false,
								goldCost, lumberCost, 0, 0, ability.getStock(unitType));
					}
				}
			}
		}
		return null;
	}

	@Override
	public Void accept(final CAbilitySellUnits ability) {
		if ((this.menuBaseOrderId == 0) && ability.isIconShowing()) {
			final CPlayer buyer = this.game.getPlayer(this.localPlayerIndex);
			for (final War3ID unitType : ability.getUnitsSold()) {
				final IconUI unitUI = this.abilityDataUI.getUnitUI(unitType);
				final CUnitType simulationUnitType = this.game.getUnitData().getUnitType(unitType);
				if ((unitUI != null) && (simulationUnitType != null)) {
					final int goldCost = CAbilitySellUnits.getGoldCost(buyer, simulationUnitType);
					final int lumberCost = CAbilitySellUnits.getLumberCost(buyer, simulationUnitType);
					final int scriptStockSlot = ability.getScriptStockSlot(unitType);
					if (scriptStockSlot >= 0) {
						addCommandButton(ability, unitUI, unitUI.getToolTip(), scriptStockSlot % COMMAND_CARD_WIDTH,
								scriptStockSlot / COMMAND_CARD_WIDTH, ability.getHandleId(), unitType.getValue(), 0,
								false, false, goldCost, lumberCost, simulationUnitType.getFoodUsed(), 0,
								ability.getStock(unitType));
					}
					else {
						addCommandButton(ability, unitUI, ability.getHandleId(), unitType.getValue(), 0, false, false,
								goldCost, lumberCost, simulationUnitType.getFoodUsed(), 0, ability.getStock(unitType));
					}
				}
			}
		}
		return null;
	}

	@Override
	public Void accept(final CAbilityAwakenHero ability) {
		if ((this.menuBaseOrderId == 0) && ability.isIconShowing()) {
			// Dead heroes take the top row of the tavern; the heroes for hire use the rows below.
			int heroIndex = 0;
			for (final CUnit playerHero : this.game.getPlayerHeroes(this.localPlayerIndex)) {
				if (CAbilityAwakenHero.isAwakenable(playerHero, this.localPlayerIndex)
						&& (heroIndex < COMMAND_CARD_WIDTH)) {
					final UnitIconUI unitUI = this.abilityDataUI.getUnitUI(playerHero.getTypeId());
					if (unitUI != null) {
						final CAbilityHero heroData = playerHero.getHeroData();
						addCommandButton(ability, unitUI, unitUI.getAwakenTip() + " - " + heroData.getProperName(),
								heroIndex++, 0, ability.getHandleId(), playerHero.getHandleId(), 0, false, false,
								CAbilityAwakenHero.getGoldCost(this.game, playerHero),
								CAbilityAwakenHero.getLumberCost(this.game, playerHero),
								playerHero.getUnitType().getFoodUsed(), 0, heroData.getHeroLevel());
					}
				}
			}
		}
		return null;
	}

	@Override
	public Void accept(final CAbilityUpgrade ability) {
		if ((this.menuBaseOrderId == 0) && ability.isIconShowing()) {
			for (final War3ID unitType : ability.getUpgradesTo()) {
				final IconUI unitUI = this.abilityDataUI.getUnitUI(unitType);
				if (unitUI != null) {
					int relativeOffsetGold;
					int relativeOffsetLumber;
					final CUnitType existingUnitType = this.unit.getUnitType();
					if (this.game.getGameplayConstants().isRelativeUpgradeCosts()) {
						relativeOffsetGold = existingUnitType.getGoldCost();
						relativeOffsetLumber = existingUnitType.getLumberCost();
					} else {
						relativeOffsetGold = 0;
						relativeOffsetLumber = 0;
					}
					final CUnitType simulationUnitType = this.game.getUnitData().getUnitType(unitType);
					addCommandButton(ability, unitUI, ability.getHandleId(), unitType.getValue(), 0, false, false,
							simulationUnitType.getGoldCost() - relativeOffsetGold,
							simulationUnitType.getLumberCost() - relativeOffsetLumber,
							simulationUnitType.getFoodUsed() - existingUnitType.getFoodUsed(), 0, -1);
				}
			}
			if (this.unit.getBuildQueueTypes()[0] != null) {
				if (!this.hasCancel) {
					this.hasCancel = true;
					addCommandButton(ability, this.abilityDataUI.getCancelTrainUI(), ability.getHandleId(),
							OrderIds.cancel, 0, false, false);
				}
			}
		}
		return null;
	}

	@Override
	public Void accept(final CAbilityHero ability) {
		if ((this.menuBaseOrderId == OrderIds.skillmenu) && ability.isIconShowing()) {
			for (final War3ID unitType : ability.getSkillsAvailable()) {
				final AbilityUI abilityUI = this.abilityDataUI.getUI(unitType);
				if (abilityUI != null) {
					final int nextLevel = this.unit.getAbilityLevel(unitType) + 1;
					final IconUI learnIconUI = abilityUI.getLearnIconUI();
					if (learnIconUI == null) {
						continue;
					}
					addCommandButton(ability, learnIconUI, String.format(Locale.ROOT, learnIconUI.getToolTip(), nextLevel),
							learnIconUI.getButtonPositionX(), learnIconUI.getButtonPositionY(), ability.getHandleId(),
							unitType.getValue(), 0, false, false, 0, 0, 0, 0, nextLevel);
				}
			}
		} else {
			// In a group the button only shows while there are points to spend, so a touch player can level the
			// active hero without first selecting it alone.
			if (this.multiSelect && (ability.getSkillPoints() == 0)) {
				return null;
			}
			if (this.menuBaseOrderId == 0 && ability.isIconShowing()) {
				final int skillPoints = ability.getSkillPoints();
				addCommandButton(ability, this.abilityDataUI.getSelectSkillUI(), ability.getHandleId(),
						OrderIds.skillmenu, 0, false, true, 0, 0, 0, 0, skillPoints != 0 ? skillPoints : -1);
			}
		}
		return null;
	}
}
