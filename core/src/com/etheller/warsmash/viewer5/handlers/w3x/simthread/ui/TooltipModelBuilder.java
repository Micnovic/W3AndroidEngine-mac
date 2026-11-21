package com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui;

import java.util.List;
import java.util.Locale;

import com.etheller.warsmash.parsers.fdf.datamodel.FrameTemplateEnvironment;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.units.ObjectData;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.ItemUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CGameplayConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUpgradeType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUpgradeType.UpgradeLevel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CPrimaryAttribute;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityGoldMinable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityOverlayedMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDefenseType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CUpgradeClass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

/**
 * Resolves the texts and costs of tool tips from simulation state: the tip of a command card button, the tips of the
 * resource bar, the clock and the unit statistics, and the one-line tip over a unit, an item or a tree. The text
 * logic is copied from the interface's own tool tip code, so the strings are the ones that interface shows.
 * <p>
 * Writer: the thread that runs simulation steps. Everything is read-only on the simulation.
 */
public final class TooltipModelBuilder {
	public static final int RESOURCE_GOLD = 0;
	public static final int RESOURCE_LUMBER = 1;
	public static final int RESOURCE_SUPPLY = 2;
	public static final int RESOURCE_UPKEEP = 3;
	public static final int TIME_OF_DAY = 4;
	public static final int ATTACK_ONE = 5;
	public static final int ATTACK_TWO = 6;
	public static final int ARMOR = 7;
	public static final int HERO_ATTRIBUTES = 8;
	public static final int RANK = 9;
	public static final int FOOD = 10;
	public static final int PANEL_TIP_COUNT = 11;

	public static final String CASTER_RANK_NAMES = "Casterupgradename";
	public static final String CASTER_RANK_SPELLS = "Casterupgradetip";
	public static final String CASTER_RANK_ART = "Casterupgradeart";

	private static final String GREEN = "|cff00ff00";
	private static final String RED = "|cffff0000";

	private final FrameTemplateEnvironment templates;
	private final DataTable miscData;
	private final ObjectData unitData;
	private final AbilityDataUI abilityDataUI;

	/**
	 * @param templates     the interface's string templates, read-only after loading
	 * @param miscData      the game's misc data (the InfoPanel limits), may be null
	 * @param unitData      the unit object data (caster rank fields), may be null
	 * @param abilityDataUI the interface data of items, for the name of an item under the pointer, may be null
	 */
	public TooltipModelBuilder(final FrameTemplateEnvironment templates, final DataTable miscData,
			final ObjectData unitData, final AbilityDataUI abilityDataUI) {
		this.templates = templates;
		this.miscData = miscData;
		this.unitData = unitData;
		this.abilityDataUI = abilityDataUI;
	}

	/** The tip of a command card button: name, description and the costs the button carries. */
	static void button(final TooltipModel tip, final String toolTip, final String uberTip, final int goldCost,
			final int lumberCost, final int foodCost, final int manaCost, final float cooldown, final int hotkey) {
		tip.title = toolTip;
		tip.body = uberTip;
		tip.extra = null;
		tip.goldCost = goldCost;
		tip.lumberCost = lumberCost;
		tip.foodCost = foodCost;
		tip.manaCost = manaCost;
		tip.cooldown = cooldown;
		tip.hotkey = hotkey;
	}

	/**
	 * Fills the tip of a part of the console. Returns false, leaving the tip cleared, when the part has no tip now (no
	 * such attack, a unit that is not a hero, a tip number out of range).
	 *
	 * @param tip    one of the constants of this class
	 * @param unit   the unit whose statistics are asked for, may be null for the resource tips
	 * @param player the player whose resources are shown
	 */
	public boolean panelTip(final int tip, final CSimulation simulation, final CPlayer player, final CUnit unit,
			final TooltipModel out) {
		out.clear();
		switch (tip) {
		case RESOURCE_GOLD:
			set(out, decorated("COLON_GOLD") + " " + player.getGold(), null, decorated("RESOURCE_UBERTIP_GOLD"));
			return true;
		case RESOURCE_LUMBER:
			set(out, decorated("COLON_LUMBER") + " " + player.getLumber(), null, decorated("RESOURCE_UBERTIP_LUMBER"));
			return true;
		case RESOURCE_SUPPLY: {
			final int foodCap = player.getFoodCap();
			final String food = foodCap == 0 ? Integer.toString(player.getFoodUsed())
					: ((player.getFoodUsed() > foodCap ? RED : "") + player.getFoodUsed() + "/" + foodCap);
			set(out, decorated("COLON_FOOD") + " " + food, null, decorated("RESOURCE_UBERTIP_SUPPLY"));
			return true;
		}
		case RESOURCE_UPKEEP:
			upkeepTip(simulation.getGameplayConstants(), player, out);
			return true;
		case TIME_OF_DAY: {
			final int minutes = (int) (simulation.getGameTimeOfDay() * 60);
			final String time = String.format(Locale.ROOT, "%d:%02d", (minutes / 60) % 24, minutes % 60);
			set(out, String.format(Locale.ROOT, decorated("TIME_OF_DAY_TOOLTIP"), time), null,
					decorated("TIME_OF_DAY_UBERTIP"));
			return true;
		}
		default:
			return unit != null && unitStatTip(tip, simulation, unit, out);
		}
	}

	private void set(final TooltipModel out, final String title, final String info, final String uberTip) {
		out.title = title;
		out.extra = info;
		out.body = uberTip;
	}

	private String decorated(final String key) {
		return this.templates.getDecoratedString(key);
	}

	/** The current upkeep level and its income rate, then every level up to the food ceiling. */
	private void upkeepTip(final CGameplayConstants constants, final CPlayer player, final TooltipModel out) {
		final int[] usage = constants.getUpkeepUsage();
		final float[] goldTax = constants.getUpkeepGoldTax();
		final float[] lumberTax = constants.getUpkeepLumberTax();
		final int ceiling = player.getFoodCapCeiling();
		final String[] names = { "UPKEEP_NONE", "UPKEEP_LOW", "UPKEEP_HIGH" };
		boolean taxesLumber = false;
		for (final float tax : lumberTax) {
			taxesLumber |= tax != 0;
		}
		final StringBuilder text = new StringBuilder(decorated("RESOURCE_UBERTIP_UPKEEP"));
		int low = 0;
		for (int level = 0; low <= ceiling; level++) {
			final int high = (level < usage.length) ? Math.min(usage[level], ceiling) : ceiling;
			final String name = decorated(names[Math.min(level, names.length - 1)]);
			final int goldKept = Math.round(100 * (1 - tax(goldTax, level)));
			if (taxesLumber) {
				text.append(String.format(Locale.ROOT, decorated("RESOURCE_UBERTIP_UPKEEP_INFO_WOOD"), low, high, name,
						goldKept, Math.round(100 * (1 - tax(lumberTax, level)))));
			}
			else {
				text.append(
						String.format(Locale.ROOT, decorated("RESOURCE_UBERTIP_UPKEEP_INFO"), low, high, name, goldKept));
			}
			low = high + 1;
		}
		final int level = constants.getUpkeepLevel(player.getFoodUsed());
		String info = decorated("COLON_GOLD_INCOME_RATE") + " " + Math.round(100 * (1 - tax(goldTax, level))) + "%";
		if (taxesLumber) {
			info += "|n" + decorated("COLON_LUMBER_INCOME_RATE") + " " + Math.round(100 * (1 - tax(lumberTax, level)))
					+ "%";
		}
		set(out, decorated(names[Math.min(level, names.length - 1)]), info, text.toString());
	}

	private static float tax(final float[] taxes, final int level) {
		return taxes.length == 0 ? 0 : taxes[Math.min(level, taxes.length - 1)];
	}

	private boolean unitStatTip(final int tip, final CSimulation simulation, final CUnit unit, final TooltipModel out) {
		switch (tip) {
		case ATTACK_ONE:
		case ATTACK_TWO: {
			final int index = tip == ATTACK_ONE ? 0 : 1;
			if (unit.getCurrentAttacks().size() <= index) {
				return false;
			}
			attackTip(simulation, unit, unit.getCurrentAttacks().get(index), out);
			return true;
		}
		case ARMOR:
			armorTip(simulation, unit, out);
			return true;
		case HERO_ATTRIBUTES:
			if (unit.getHeroData() == null) {
				return false;
			}
			heroAttributesTip(simulation, unit, out);
			return true;
		case RANK:
			rankTip(simulation, unit, out);
			return true;
		case FOOD:
			set(out, decorated("COLON_FOOD"), null, decorated("COLON_PROVIDED") + unit.getUnitType().getFoodMade());
			return true;
		default:
			return false;
		}
	}

	/** Type, range and speed of the attack, the speed in the five steps the move speed is told in. */
	private void attackTip(final CSimulation simulation, final CUnit unit, final CUnitAttack attack, final TooltipModel out) {
		final String key = damageKey(attack.getAttackType());
		final Element limits = this.miscData == null ? null : this.miscData.get("InfoPanel");
		final int range = attack.getRange();
		final String rangeText = range <= limit(limits, "MeleeRangeMax", 128) ? decorated("MELEE")
				: Integer.toString(range);
		final float cooldown = attack.getCooldownTime();
		final String speed;
		if (cooldown >= limit(limits, "AttackVerySlow", 3)) {
			speed = "MOVESPEEDVERYSLOW";
		}
		else if (cooldown >= limit(limits, "AttackSlow", 2)) {
			speed = "MOVESPEEDSLOW";
		}
		else if (cooldown >= limit(limits, "AttackAverage", 1.5f)) {
			speed = "MOVESPEEDAVERAGE";
		}
		else if (cooldown >= limit(limits, "AttackFast", 1)) {
			speed = "MOVESPEEDFAST";
		}
		else {
			speed = "MOVESPEEDVERYFAST";
		}
		final String info = decorated("DAMAGE_" + key) + "|n" + decorated("COLON_RANGE") + " " + rangeText + "|n"
				+ decorated("COLON_SPEED") + " "
				+ bonusColored(decorated(speed), attack.getTotalAttackSpeedPercent() - 1)
				+ upgradeLine(simulation, unit, CUpgradeClass.MELEE, CUpgradeClass.RANGED, CUpgradeClass.ARTILLERY);
		set(out, decorated("COLON_DAMAGE") + " " + damageText(attack), info, decorated("DAMAGETIP_" + key));
	}

	private void armorTip(final CSimulation simulation, final CUnit unit, final TooltipModel out) {
		final String key = armorKey(unit.getDefenseType());
		final float armor = unit.getDefense();
		final float armorFactor = simulation.getGameplayConstants().getDefenseArmor();
		final float reduction = armor >= 0 ? (armor * armorFactor) / (1 + (armor * armorFactor))
				: (float) (StrictMath.pow(1 - armorFactor, -armor) - 1);
		final StringBuilder info = new StringBuilder(decorated("ARMOR_" + key));
		info.append("|n").append(decorated("COLON_DAMAGE_REDUCTION")).append(' ')
				.append(bonusColored(Integer.toString(Math.round(reduction * 100)),
						unit.getTotalTemporaryDefenseBonus()))
				.append('%');
		if (!unit.isBuilding()) {
			final Element limits = this.miscData == null ? null : this.miscData.get("InfoPanel");
			final int speed = unit.getSpeed();
			final String speedKey;
			if (speed < limit(limits, "SpeedVerySlow", 175)) {
				speedKey = "MOVESPEEDVERYSLOW";
			}
			else if (speed < limit(limits, "SpeedSlow", 220)) {
				speedKey = "MOVESPEEDSLOW";
			}
			else if (speed < limit(limits, "SpeedAverage", 280)) {
				speedKey = "MOVESPEEDAVERAGE";
			}
			else if (speed < limit(limits, "SpeedFast", 350)) {
				speedKey = "MOVESPEEDFAST";
			}
			else {
				speedKey = "MOVESPEEDVERYFAST";
			}
			info.append("|n").append(decorated("COLON_MOVE_SPEED")).append(' ')
					.append(bonusColored(decorated(speedKey), unit.getSpeedBonus()));
		}
		info.append(upgradeLine(simulation, unit, CUpgradeClass.ARMOR));
		set(out, decorated("COLON_ARMOR") + " " + defenseText(unit), info.toString(), decorated("ARMORTIP_" + key));
	}

	/** The line of the unit's upgrade of the first of these classes it has, named as its first level before any. */
	private String upgradeLine(final CSimulation simulation, final CUnit unit, final CUpgradeClass... classes) {
		for (final CUpgradeClass upgradeClass : classes) {
			final War3ID upgradeId = unit.getUnitType().getUpgradeClassToType().get(upgradeClass);
			final CUpgradeType upgrade = upgradeId == null ? null : simulation.getUpgradeData().getType(upgradeId);
			if (upgrade == null) {
				continue;
			}
			final int level = simulation.getPlayer(unit.getPlayerIndex()).getTechtreeUnlocked(upgradeId);
			final UpgradeLevel named = upgrade.getLevel(Math.max(level, 1) - 1);
			if (named == null) {
				continue;
			}
			return "|n" + decorated("COLON_UPGRADE") + " "
					+ String.format(Locale.ROOT, decorated("UPGRADE_TOOLTIP"), named.getName(), level);
		}
		return "";
	}

	/** The rank name, the training that raises it and the spells the caster has at this rank. */
	private void rankTip(final CSimulation simulation, final CUnit unit, final TooltipModel out) {
		final int rank = casterRank(simulation, unit);
		final String name = casterField(unit, CASTER_RANK_NAMES, rank);
		final String spells = casterField(unit, CASTER_RANK_SPELLS, rank);
		set(out, decorated("COLON_RANK"),
				decorated("COLON_NAME") + " " + (name == null ? "" : name) + upgradeLine(simulation, unit, CUpgradeClass.CASTER),
				spells == null ? "" : (decorated("COLON_SPELLS") + "|n" + spells));
	}

	/** How many levels of its caster training the caster's owner has. */
	public static int casterRank(final CSimulation simulation, final CUnit unit) {
		final War3ID training = unit.getUnitType().getUpgradeClassToType().get(CUpgradeClass.CASTER);
		return training == null ? 0 : simulation.getPlayer(unit.getPlayerIndex()).getTechtreeUnlocked(training);
	}

	/** The caster rank data of the unit's type for this rank (the last one past the end); null when it has none. */
	public String casterField(final CUnit unit, final String field, final int rank) {
		if (this.unitData == null) {
			return null;
		}
		final GameObject row = this.unitData.get(unit.getTypeId());
		if (row == null) {
			return null;
		}
		final List<String> values = row.getFieldAsList(field);
		if ((values == null) || values.isEmpty()) {
			return null;
		}
		final String value = values.get(Math.min(rank, values.size() - 1));
		return value.isEmpty() ? null : value;
	}

	private void heroAttributesTip(final CSimulation simulation, final CUnit unit, final TooltipModel out) {
		final CGameplayConstants constants = simulation.getGameplayConstants();
		final CPrimaryAttribute primary = unit.getUnitType().getPrimaryAttribute();
		final StringBuilder text = new StringBuilder();
		attribute(text, "COLON_STRENGTH", primary == CPrimaryAttribute.STRENGTH, constants);
		text.append("|n").append(
				String.format(Locale.ROOT, decorated("BONUS_HITPOINTS"), Math.round(constants.getStrHitPointBonus())));
		text.append("|n").append(decorated("BONUS_HPREGEN"));
		text.append("|n");
		attribute(text, "COLON_AGILITY", primary == CPrimaryAttribute.AGILITY, constants);
		final float agilityDefense = constants.getAgiDefenseBonus();
		if (agilityDefense > 0) {
			text.append("|n");
			if (agilityDefense < 1) {
				text.append(
						String.format(Locale.ROOT, decorated("BONUS_DEFENSE_FIXED"), Math.round(1 / agilityDefense)));
			}
			else {
				text.append(String.format(Locale.ROOT, decorated("BONUS_DEFENSE"), 1, Math.round(agilityDefense)));
			}
		}
		text.append("|n").append(decorated("BONUS_ATTACK_SPEED"));
		text.append("|n");
		attribute(text, "COLON_INTELLECT", primary == CPrimaryAttribute.INTELLIGENCE, constants);
		text.append("|n")
				.append(String.format(Locale.ROOT, decorated("BONUS_MANA"), Math.round(constants.getIntManaBonus())));
		text.append("|n").append(decorated("BONUS_MANAREGEN"));
		set(out, decorated("COLON_HERO_ATTRIBUTES"), null, text.toString());
	}

	/** The attribute's name, and for the primary one its mark and the damage each point adds. */
	private void attribute(final StringBuilder text, final String nameKey, final boolean primary,
			final CGameplayConstants constants) {
		text.append(decorated(nameKey));
		if (primary) {
			text.append("|n - ").append(decorated("PRIMARY_ATTRIBUTE"));
			text.append("|n").append(
					String.format(Locale.ROOT, decorated("BONUS_DAMAGE"), Math.round(constants.getStrAttackBonus())));
		}
	}

	private static String bonusColored(final String text, final float bonus) {
		if (bonus > 0.0001f) {
			return GREEN + text + "|r";
		}
		if (bonus < -0.0001f) {
			return RED + text + "|r";
		}
		return text;
	}

	private static float limit(final Element limits, final String field, final float fallback) {
		if ((limits == null) || !limits.hasField(field)) {
			return fallback;
		}
		return limits.getFieldFloatValue(field);
	}

	private static String damageKey(final CAttackType attackType) {
		switch (attackType) {
		case SPELLS:
		case MAGIC:
			return "MAGIC";
		case NORMAL:
		case PIERCE:
		case SIEGE:
		case CHAOS:
		case HERO:
			return attackType.name();
		default:
			return "UNKNOWN";
		}
	}

	private static String armorKey(final CDefenseType defenseType) {
		switch (defenseType) {
		case SMALL:
		case MEDIUM:
		case LARGE:
		case FORT:
		case HERO:
		case DIVINE:
		case NONE:
		case NORMAL:
			return defenseType.name();
		default:
			return "UNKNOWN";
		}
	}

	/** The damage range of the attack with its temporary bonus, as the panel and the attack's tool tip show it. */
	public static String damageText(final CUnitAttack attack) {
		final String damage = attack.getMinDamageDisplay() + " - " + attack.getMaxDamageDisplay();
		final int bonus = attack.getTotalTemporaryDamageBonus();
		if (bonus == 0) {
			return damage;
		}
		return damage + (bonus > 0 ? "|cFF00FF00 +" : "|cFFFF0000 ") + bonus;
	}

	/** The armor with its temporary bonus, as the panel and the armor's tool tip show it. */
	public String defenseText(final CUnit unit) {
		if (unit.isInvulnerable()) {
			return decorated("INVULNERABLE");
		}
		final String defense = Integer.toString(unit.getCurrentDefenseDisplay());
		final float bonus = unit.getTotalTemporaryDefenseBonus();
		if (bonus == 0) {
			return defense;
		}
		return defense + (bonus > 0 ? "|cFF00FF00 +" : "|cFFFF0000 ") + formatBonus(bonus);
	}

	private static String formatBonus(final float bonus) {
		if (bonus == Math.round(bonus)) {
			return Integer.toString(Math.round(bonus));
		}
		return String.format(Locale.ROOT, "%.1f", bonus);
	}

	/**
	 * The text shown over a unit under the pointer: the owner's name in the colour of the alliance, the hero's name
	 * and level, or a neutral unit's name with its gold and level. Null when the unit shows nothing.
	 *
	 * @param maxPlayers the number of player slots of the game (the neutral players are the last four)
	 */
	public String unitHoverTip(final CSimulation game, final CUnit unit, final CPlayer localPlayer,
			final int maxPlayers) {
		final CAbilityHero heroData = unit.getHeroData();
		final int unitPlayerIndex = unit.getFakePlayerIndex();
		final boolean neutralHostile = unitPlayerIndex == (maxPlayers - 4);
		final boolean neutralPassive = unitPlayerIndex == (maxPlayers - 1);
		String returnValue = "";
		if ((unitPlayerIndex != localPlayer.getId())
				&& (!localPlayer.hasAlliance(unitPlayerIndex, CAllianceType.SHARED_CONTROL))
				&& (unitPlayerIndex < (maxPlayers - 4))) {
			final CPlayer unitPlayer = game.getPlayer(unitPlayerIndex);
			final String name = unitPlayer.getName();
			if (name != null) {
				if (unitPlayer.hasAlliance(localPlayer.getId(), CAllianceType.PASSIVE)) {
					if (unitPlayer.hasAlliance(localPlayer.getId(), CAllianceType.SHARED_SPELLS)) {
						returnValue = "|CFF00FF00" + name;
					}
					else {
						returnValue = "|CFFFFFF00" + name;
					}
				}
				else {
					returnValue = "|CFFFF0000" + name;
				}
			}
		}
		if (heroData != null) {
			if (!returnValue.isEmpty()) {
				returnValue += "|r|n";
			}
			final String level = decorated("LEVEL");
			return returnValue + heroData.getProperName() + "|n" + level + " " + heroData.getHeroLevel();
		}
		final CAbilityGoldMinable goldMineData = unit.getGoldMineData();
		final CAbilityOverlayedMine blightedGoldMineData = unit.getOverlayedGoldMineData();
		final boolean neutral = (neutralPassive && unit.isBuilding()) || neutralHostile || (goldMineData != null)
				|| (blightedGoldMineData != null);
		if (neutral) {
			if (!returnValue.isEmpty()) {
				returnValue += "|n";
			}
			returnValue += unit.getName();
			if (goldMineData != null) {
				returnValue += "|n" + decorated("COLON_GOLD") + " " + goldMineData.getGold();
			}
			else if (blightedGoldMineData != null) {
				returnValue += "|n" + decorated("COLON_GOLD") + " " + blightedGoldMineData.getGold();
			}
			final int creepLevel = unit.getUnitType().getLevel();
			if (neutralHostile && (creepLevel > 0)) {
				returnValue += "|n" + decorated("LEVEL") + " " + creepLevel;
			}
		}
		if (!returnValue.isEmpty()) {
			return returnValue;
		}
		return null;
	}

	/** The text shown over an item under the pointer. */
	public String itemHoverTip(final CItem item) {
		final ItemUI itemUI = this.abilityDataUI == null ? null : this.abilityDataUI.getItemUI(item.getTypeId());
		final String nameOverride = item.getNameOverride();
		return nameOverride != null ? nameOverride : (itemUI == null ? null : itemUI.getName());
	}

	/** The text shown over a destructable under the pointer. */
	public static String destructableHoverTip(final CDestructable destructable) {
		return destructable.getDestType().getName();
	}
}
