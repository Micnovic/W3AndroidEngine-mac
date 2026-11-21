package com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui;

import java.util.Arrays;

/**
 * Everything the info panel and the unit tooltip show about one unit: the active unit of the selection, a hero in the
 * hero bar, or the unit under the cursor. Writer: the simulation thread while the frame is built. Reader: the render
 * thread. A hovered unit's details are one frame late (the hover comes from the local snapshot).
 */
public final class UnitDetailsModel {
	public static final int INVENTORY_SLOTS = 9;
	public static final int MAX_BUFFS = 16;
	public static final int MAX_QUEUE = 7;
	public static final int MAX_CARGO = 12;
	public static final int ATTACKS = 2;

	/** What a queue entry is, as the panel's labels and icons tell them apart. */
	public static final int QUEUE_UNIT = 0;
	public static final int QUEUE_RESEARCH = 1;
	public static final int QUEUE_HERO_REVIVE = 2;
	public static final int QUEUE_SACRIFICE = 3;

	public static final int ATTRIBUTE_STRENGTH = 0;
	public static final int ATTRIBUTE_AGILITY = 1;
	public static final int ATTRIBUTE_INTELLIGENCE = 2;

	/** 0 when the model is unused. */
	public int unitHandle;
	public int typeId;
	/** The hero's own name, else the unit type's name. */
	public String name;
	/** The unit type's name, also for a hero. */
	public String typeName;
	/** The classification line of a unit that is not a hero (null for none). */
	public String classText;
	public int level;
	public int playerIndex;
	public float x;
	public float y;
	public float hp;
	public float maxHp;
	public float mana;
	public float maxMana;
	public int maxManaInt;
	public boolean dead;
	public boolean hidden;
	/** Whether the local player can see the unit (fog, invisibility). */
	public boolean visibleToLocal;
	public boolean paused;
	/** Whether the unit's owner gives the local player shared control. */
	public boolean controlShared;
	public boolean building;
	public boolean illusion;
	public boolean constructing;
	public boolean upgrading;
	/** The icon of the unit type, as a path; null when the data has none. */
	public String iconPath;
	public int armor;
	public int armorType;
	public String armorText;
	/** Level of the armor upgrade the player has, -1 when the type has none. */
	public int armorUpgradeLevel = -1;
	public int damageMin;
	public int damageMax;
	public int attackType;
	public int attackCount;
	public final int[] attackTypes = new int[ATTACKS];
	public final String[] attackTexts = new String[ATTACKS];
	public final boolean[] attackShown = new boolean[ATTACKS];
	/** Level of the weapon upgrade the player has, -1 when the type has none. */
	public int weaponUpgradeLevel = -1;
	public boolean hero;
	public int primaryAttribute;
	public int heroXp;
	public int heroXpToNextLevel;
	public int strength;
	public int agility;
	public int intelligence;
	public String strengthText;
	public String agilityText;
	public String intelligenceText;
	public int skillPoints;
	/** Caster rank (0 when none), its name and its art path; the name is null for a unit without ranks. */
	public int rank;
	public String rankName;
	public String rankArt;
	public int foodMade;
	public int buffCount;
	public final int[] buffIds = new int[MAX_BUFFS];
	public final float[] buffRemaining = new float[MAX_BUFFS];
	public final String[] buffIconPaths = new String[MAX_BUFFS];
	public final String[] buffTips = new String[MAX_BUFFS];
	public final String[] buffUberTips = new String[MAX_BUFFS];
	public final boolean[] buffPositive = new boolean[MAX_BUFFS];
	public final boolean[] buffLeveled = new boolean[MAX_BUFFS];
	public final int[] buffLevels = new int[MAX_BUFFS];
	/** The timed life bar of a summon or illusion the local player owns; the label is null when there is none. */
	public String timedLifeLabel;
	public float timedLifeRemaining;
	public float timedLifeMax;
	/** An inventory (the unit's own, or a patron's for a shop) is shown; slots below are meaningful then. */
	public boolean inventoryShown;
	/** False for a patron's inventory shown on a shop: its items cannot be used from here. */
	public boolean inventoryEnabled;
	public int inventoryCapacity;
	public final int[] itemTypeIds = new int[INVENTORY_SLOTS];
	public final int[] itemCharges = new int[INVENTORY_SLOTS];
	public final float[] itemCooldown = new float[INVENTORY_SLOTS];
	public final float[] itemCooldownMax = new float[INVENTORY_SLOTS];
	public final String[] itemIconPaths = new String[INVENTORY_SLOTS];
	public final String[] itemNames = new String[INVENTORY_SLOTS];
	public final String[] itemUberTips = new String[INVENTORY_SLOTS];
	public final boolean[] itemActive = new boolean[INVENTORY_SLOTS];
	public final boolean[] itemPawnable = new boolean[INVENTORY_SLOTS];
	public final int[] itemPawnGold = new int[INVENTORY_SLOTS];
	public final int[] itemPawnLumber = new int[INVENTORY_SLOTS];
	public int cargoCapacity;
	public int cargoCount;
	public final String[] cargoIconPaths = new String[MAX_CARGO];
	public final String[] cargoTips = new String[MAX_CARGO];
	public final String[] cargoUberTips = new String[MAX_CARGO];
	public final float[] cargoLife = new float[MAX_CARGO];
	public final float[] cargoMana = new float[MAX_CARGO];
	public final boolean[] cargoHasMana = new boolean[MAX_CARGO];
	/** The first entry of the queue is being worked on and the local player may see the queue. */
	public boolean queueShown;
	public int queueCount;
	public final int[] queueTypeIds = new int[MAX_QUEUE];
	public final int[] queueKinds = new int[MAX_QUEUE];
	public final String[] queueIconPaths = new String[MAX_QUEUE];
	public final String[] queueTips = new String[MAX_QUEUE];
	public final String[] queueUberTips = new String[MAX_QUEUE];
	/** Progress of the first queue entry, 0..1. */
	public float queueProgress;
	/** Icon path of the worker inside a building under construction, null for none. */
	public String workerIconPath;
	/** Handle of the worker inside a building under construction, 0 for none. */
	public int workerHandle;

	public void clear() {
		unitHandle = 0;
		typeId = 0;
		name = null;
		typeName = null;
		classText = null;
		level = 0;
		playerIndex = 0;
		x = 0;
		y = 0;
		hp = 0;
		maxHp = 0;
		mana = 0;
		maxMana = 0;
		maxManaInt = 0;
		dead = false;
		hidden = false;
		visibleToLocal = false;
		paused = false;
		controlShared = false;
		building = false;
		illusion = false;
		constructing = false;
		upgrading = false;
		iconPath = null;
		armor = 0;
		armorType = 0;
		armorText = null;
		armorUpgradeLevel = -1;
		damageMin = 0;
		damageMax = 0;
		attackType = 0;
		attackCount = 0;
		Arrays.fill(attackTypes, 0);
		Arrays.fill(attackTexts, null);
		Arrays.fill(attackShown, false);
		weaponUpgradeLevel = -1;
		hero = false;
		primaryAttribute = 0;
		heroXp = 0;
		heroXpToNextLevel = 0;
		strength = 0;
		agility = 0;
		intelligence = 0;
		strengthText = null;
		agilityText = null;
		intelligenceText = null;
		skillPoints = 0;
		rank = 0;
		rankName = null;
		rankArt = null;
		foodMade = 0;
		buffCount = 0;
		timedLifeLabel = null;
		timedLifeRemaining = 0;
		timedLifeMax = 0;
		inventoryShown = false;
		inventoryEnabled = false;
		inventoryCapacity = 0;
		for (int i = 0; i < INVENTORY_SLOTS; i++) {
			itemTypeIds[i] = 0;
			itemCharges[i] = 0;
			itemCooldown[i] = 0;
			itemCooldownMax[i] = 0;
			itemIconPaths[i] = null;
			itemNames[i] = null;
			itemUberTips[i] = null;
			itemActive[i] = false;
			itemPawnable[i] = false;
			itemPawnGold[i] = 0;
			itemPawnLumber[i] = 0;
		}
		cargoCapacity = 0;
		cargoCount = 0;
		queueShown = false;
		queueCount = 0;
		queueProgress = 0;
		workerIconPath = null;
		workerHandle = 0;
	}

	/** Clears what {@code clear} would except the buffs and the timed life bar, which the card builder fills. */
	public void clearKeepingBuffs() {
		final int count = buffCount;
		final String label = timedLifeLabel;
		final float remaining = timedLifeRemaining;
		final float max = timedLifeMax;
		clear();
		buffCount = count;
		timedLifeLabel = label;
		timedLifeRemaining = remaining;
		timedLifeMax = max;
	}

	public void copyFrom(final UnitDetailsModel other) {
		unitHandle = other.unitHandle;
		typeId = other.typeId;
		name = other.name;
		typeName = other.typeName;
		classText = other.classText;
		level = other.level;
		playerIndex = other.playerIndex;
		x = other.x;
		y = other.y;
		hp = other.hp;
		maxHp = other.maxHp;
		mana = other.mana;
		maxMana = other.maxMana;
		maxManaInt = other.maxManaInt;
		dead = other.dead;
		hidden = other.hidden;
		visibleToLocal = other.visibleToLocal;
		paused = other.paused;
		controlShared = other.controlShared;
		building = other.building;
		illusion = other.illusion;
		constructing = other.constructing;
		upgrading = other.upgrading;
		iconPath = other.iconPath;
		armor = other.armor;
		armorType = other.armorType;
		armorText = other.armorText;
		armorUpgradeLevel = other.armorUpgradeLevel;
		damageMin = other.damageMin;
		damageMax = other.damageMax;
		attackType = other.attackType;
		attackCount = other.attackCount;
		System.arraycopy(other.attackTypes, 0, attackTypes, 0, ATTACKS);
		System.arraycopy(other.attackTexts, 0, attackTexts, 0, ATTACKS);
		System.arraycopy(other.attackShown, 0, attackShown, 0, ATTACKS);
		weaponUpgradeLevel = other.weaponUpgradeLevel;
		hero = other.hero;
		primaryAttribute = other.primaryAttribute;
		heroXp = other.heroXp;
		heroXpToNextLevel = other.heroXpToNextLevel;
		strength = other.strength;
		agility = other.agility;
		intelligence = other.intelligence;
		strengthText = other.strengthText;
		agilityText = other.agilityText;
		intelligenceText = other.intelligenceText;
		skillPoints = other.skillPoints;
		rank = other.rank;
		rankName = other.rankName;
		rankArt = other.rankArt;
		foodMade = other.foodMade;
		buffCount = other.buffCount;
		System.arraycopy(other.buffIds, 0, buffIds, 0, MAX_BUFFS);
		System.arraycopy(other.buffRemaining, 0, buffRemaining, 0, MAX_BUFFS);
		System.arraycopy(other.buffIconPaths, 0, buffIconPaths, 0, MAX_BUFFS);
		System.arraycopy(other.buffTips, 0, buffTips, 0, MAX_BUFFS);
		System.arraycopy(other.buffUberTips, 0, buffUberTips, 0, MAX_BUFFS);
		System.arraycopy(other.buffPositive, 0, buffPositive, 0, MAX_BUFFS);
		System.arraycopy(other.buffLeveled, 0, buffLeveled, 0, MAX_BUFFS);
		System.arraycopy(other.buffLevels, 0, buffLevels, 0, MAX_BUFFS);
		timedLifeLabel = other.timedLifeLabel;
		timedLifeRemaining = other.timedLifeRemaining;
		timedLifeMax = other.timedLifeMax;
		inventoryShown = other.inventoryShown;
		inventoryEnabled = other.inventoryEnabled;
		inventoryCapacity = other.inventoryCapacity;
		System.arraycopy(other.itemTypeIds, 0, itemTypeIds, 0, INVENTORY_SLOTS);
		System.arraycopy(other.itemCharges, 0, itemCharges, 0, INVENTORY_SLOTS);
		System.arraycopy(other.itemCooldown, 0, itemCooldown, 0, INVENTORY_SLOTS);
		System.arraycopy(other.itemCooldownMax, 0, itemCooldownMax, 0, INVENTORY_SLOTS);
		System.arraycopy(other.itemIconPaths, 0, itemIconPaths, 0, INVENTORY_SLOTS);
		System.arraycopy(other.itemNames, 0, itemNames, 0, INVENTORY_SLOTS);
		System.arraycopy(other.itemUberTips, 0, itemUberTips, 0, INVENTORY_SLOTS);
		System.arraycopy(other.itemActive, 0, itemActive, 0, INVENTORY_SLOTS);
		System.arraycopy(other.itemPawnable, 0, itemPawnable, 0, INVENTORY_SLOTS);
		System.arraycopy(other.itemPawnGold, 0, itemPawnGold, 0, INVENTORY_SLOTS);
		System.arraycopy(other.itemPawnLumber, 0, itemPawnLumber, 0, INVENTORY_SLOTS);
		cargoCapacity = other.cargoCapacity;
		cargoCount = other.cargoCount;
		System.arraycopy(other.cargoIconPaths, 0, cargoIconPaths, 0, MAX_CARGO);
		System.arraycopy(other.cargoTips, 0, cargoTips, 0, MAX_CARGO);
		System.arraycopy(other.cargoUberTips, 0, cargoUberTips, 0, MAX_CARGO);
		System.arraycopy(other.cargoLife, 0, cargoLife, 0, MAX_CARGO);
		System.arraycopy(other.cargoMana, 0, cargoMana, 0, MAX_CARGO);
		System.arraycopy(other.cargoHasMana, 0, cargoHasMana, 0, MAX_CARGO);
		queueShown = other.queueShown;
		queueCount = other.queueCount;
		System.arraycopy(other.queueTypeIds, 0, queueTypeIds, 0, MAX_QUEUE);
		System.arraycopy(other.queueKinds, 0, queueKinds, 0, MAX_QUEUE);
		System.arraycopy(other.queueIconPaths, 0, queueIconPaths, 0, MAX_QUEUE);
		System.arraycopy(other.queueTips, 0, queueTips, 0, MAX_QUEUE);
		System.arraycopy(other.queueUberTips, 0, queueUberTips, 0, MAX_QUEUE);
		queueProgress = other.queueProgress;
		workerIconPath = other.workerIconPath;
		workerHandle = other.workerHandle;
	}
}
