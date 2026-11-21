package com.etheller.warsmash.parsers.jass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.etheller.interpreter.ast.function.ConvertedConstant;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.units.StandardObjectData;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.AbstractCAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractGenericAliasedAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpell;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.types.impl.CAbilityTypeAbilityBuilder;

/**
 * The Reforged ability field natives (BlzGetAbilityIntegerField and the other fields, level fields and level array
 * fields). A field constant is the object editor id of the field; the ability meta data of the game tells which
 * column of the ability data it is, and a value reads from the data of the ability's alias, with the values a
 * script set on this one ability in front. Levels count from 0 here, as common.j documents for these natives.
 *
 * A set changes what the ability casts with: a spell filled from its data (the spell base classes) is filled again
 * at once and at every level change, and an ability built on the level data of its type (item bonuses, immolation,
 * channel, harvesting, cargo) gets a level data of its own built from the data with the
 * script's values in front, applied at once if the ability is on a unit and at every level change. Other abilities
 * of the type keep sharing the type's level data.
 */
final class AbilityFieldNatives {
	/** One row of the ability meta data. */
	private static final class Meta {
		final String field;
		final String slk;
		final int index;
		final boolean leveled;
		final int data;
		final String type;

		Meta(final Element row) {
			this.field = row.getField("field");
			this.slk = row.getField("slk");
			this.index = parseInt(row.getField("index"), -1);
			this.leveled = parseInt(row.getField("repeat"), 0) > 0;
			this.data = parseInt(row.getField("data"), 0);
			this.type = row.getField("type");
		}

		boolean isBoolean() {
			return "bool".equals(this.type);
		}

		boolean isInteger() {
			return "int".equals(this.type);
		}

		boolean isReal() {
			return "unreal".equals(this.type);
		}

		/** The column of the ability data, or the list the value is an element of (the profile keeps lists). */
		String column(final int level) {
			final StringBuilder name = new StringBuilder(this.field == null ? "" : this.field);
			if (this.data > 0) {
				name.append((char) ('A' + (this.data - 1)));
			}
			if (this.leveled && !"Profile".equals(this.slk)) {
				name.append(level + 1);
			}
			return name.toString();
		}

		/** Which element of the column: the profile lists hold one entry per level. */
		int listIndex(final int level, final int arrayIndex) {
			if (arrayIndex >= 0) {
				return arrayIndex;
			}
			if (this.leveled && "Profile".equals(this.slk)) {
				return level;
			}
			return Math.max(0, this.index);
		}
	}

	private static int parseInt(final String text, final int fallback) {
		try {
			return Integer.parseInt(text.trim());
		}
		catch (final RuntimeException exc) {
			return fallback;
		}
	}

	private final JassProgram program;
	private final War3MapViewer viewer;
	private Map<String, Meta> metaById;

	private AbilityFieldNatives(final JassProgram program, final War3MapViewer viewer) {
		this.program = program;
		this.viewer = viewer;
	}

	static void register(final JassProgram program, final War3MapViewer viewer) {
		final AbilityFieldNatives natives = new AbilityFieldNatives(program, viewer);
		natives.registerPlain();
		natives.registerLevel();
		natives.registerLevelArray();
	}

	private void add(final String name, final JassFunction function) {
		this.program.getJassNativeManager().createNative(name, function);
	}

	private static <T> T object(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(ObjectJassValueVisitor.<T>getInstance());
	}

	private static int integer(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(IntegerJassValueVisitor.getInstance());
	}

	/** The object editor id of the field constant, as the four letters. */
	private static String fieldId(final List<JassValue> arguments, final int index) {
		final Object constant = AbilityFieldNatives.<Object>object(arguments, index);
		return constant instanceof ConvertedConstant ? new War3ID(((ConvertedConstant) constant).getValue()).toString()
				: null;
	}

	private Meta meta(final String id) {
		if (id == null) {
			return null;
		}
		if (this.metaById == null) {
			this.metaById = new HashMap<>();
			try {
				final DataTable table = new StandardObjectData(this.viewer.dataSource).getStandardAbilityMeta();
				for (final String key : table.keySet()) {
					this.metaById.put(key, new Meta(table.get(key)));
				}
			}
			catch (final RuntimeException exc) {
				this.metaById.clear();
			}
		}
		return this.metaById.get(id);
	}

	private GameObject data(final AbstractCAbility ability) {
		return this.viewer.getAllObjectData().getAbilities().get(ability.getAlias());
	}

	private String baseValue(final AbstractCAbility ability, final Meta meta, final int level,
			final int arrayIndex) {
		final GameObject data = data(ability);
		if (data == null) {
			return "";
		}
		final String value = data.getField(meta.column(level), meta.listIndex(level, arrayIndex));
		return value == null ? "" : value;
	}

	/** The value as text, the script's own first; an array index reads one element of the level's list. */
	private String read(final AbstractCAbility ability, final Meta meta, final int level, final int arrayIndex) {
		if (arrayIndex >= 0) {
			final List<String> elements = elements(ability, meta, level);
			return arrayIndex < elements.size() ? elements.get(arrayIndex) : "";
		}
		final String own = ability.getScriptField(meta.column(level) + "#" + meta.listIndex(level, -1));
		return own != null ? own : baseValue(ability, meta, level, -1);
	}

	private boolean write(final AbstractCAbility ability, final Meta meta, final int level, final int arrayIndex,
			final String value) {
		if (arrayIndex >= 0) {
			final List<String> elements = elements(ability, meta, level);
			while (elements.size() <= arrayIndex) {
				elements.add("");
			}
			elements.set(arrayIndex, value);
			storeElements(ability, meta, level, elements);
			refill(ability);
			return true;
		}
		ability.setScriptField(meta.column(level) + "#" + meta.listIndex(level, -1), value);
		applyCostAndCooldown(ability, meta, level, value);
		refill(ability);
		return true;
	}

	/**
	 * Spell abilities take their numbers from the data when they are filled, so a set fills the ability again; one
	 * built on level data of its type gets its level applied again, which reads the script's values.
	 */
	private void refill(final AbstractCAbility ability) {
		if (ability instanceof AbilityBuilderAbility) {
			refillBuilder(ability);
		}
		else if (ability instanceof CAbilitySpell) {
			final GameObject data = data(ability);
			if (data != null) {
				((CAbilitySpell) ability).populate(data, Math.max(1, ((CAbilitySpell) ability).getLevel()));
			}
		}
		else if (ability instanceof CLevelingAbility) {
			final CSimulation simulation = this.viewer.simulation;
			final CAbilityType<?> type = simulation.getAbilityData().getAbilityType(ability.getAlias());
			final int level = ((CLevelingAbility) ability).getLevel();
			final CUnit owner = (type != null) && type.appliesScriptFields() && (level >= 1) ? owner(simulation, ability)
					: null;
			if (owner != null) {
				type.setLevel(simulation, owner, (CLevelingAbility) ability, level);
			}
		}
	}

	/**
	 * An ability built from a behavior configuration reads its numbers (cost, cooldown, range, area, durations, data)
	 * from a list of level data it holds, so a set gives it a copy built from the script's values and fills its
	 * numbers again. It is not set to its level again: that would run what the configuration does on a level change.
	 */
	private void refillBuilder(final AbstractCAbility ability) {
		final CSimulation simulation = this.viewer.simulation;
		final CAbilityType<?> type = simulation.getAbilityData().getAbilityType(ability.getAlias());
		if (!(type instanceof CAbilityTypeAbilityBuilder)) {
			return;
		}
		final AbilityBuilderAbility builder = (AbilityBuilderAbility) ability;
		builder.setLevelData(((CAbilityTypeAbilityBuilder) type).levelDataFor(ability));
		final CUnit owner = owner(simulation, ability);
		if (owner != null) {
			builder.refreshSpellFields(simulation, owner);
		}
	}

	/** The unit the ability is on, or null for an ability nobody has (an item's ability while it lies on the ground). */
	private static CUnit owner(final CSimulation simulation, final AbstractCAbility ability) {
		final CItem item = ability.getItem();
		if ((item != null) && (item.getContainedUnit() != null)) {
			return item.getContainedUnit();
		}
		for (final CUnit unit : simulation.getUnitsIncludingNew()) {
			if (unit.getAbilities().contains(ability)) {
				return unit;
			}
		}
		return null;
	}

	private void applyCostAndCooldown(final AbstractCAbility ability, final Meta meta, final int level,
			final String value) {
		if (!(ability instanceof AbstractGenericAliasedAbility) || !meta.leveled) {
			return;
		}
		final AbstractGenericAliasedAbility leveling = (AbstractGenericAliasedAbility) ability;
		final String id = meta.field + (meta.data > 0 ? String.valueOf(meta.data) : "");
		if ("Cost".equals(id)) {
			leveling.overrideManaCost(level + 1, Math.max(0, (int) parseFloat(value)));
		}
		else if ("Cool".equals(id)) {
			leveling.overrideCooldown(level + 1, parseFloat(value));
		}
	}

	private static float parseFloat(final String text) {
		try {
			return Float.parseFloat(text.trim());
		}
		catch (final RuntimeException exc) {
			return 0f;
		}
	}

	private static boolean parseBoolean(final String text) {
		final String lower = text.trim().toLowerCase(Locale.ROOT);
		return "1".equals(lower) || "true".equals(lower) || (parseFloat(lower) != 0f);
	}

	private static String text(final boolean value) {
		return value ? "1" : "0";
	}

	private static String text(final float value) {
		return Float.toString(value);
	}

	/** What a native does with the field: the ability handle, the field row and the level, or null for any miss. */
	private interface Access {
		JassValue run(AbstractCAbility ability, Meta meta, int level, int arrayIndex, List<JassValue> arguments);
	}

	private void field(final String name, final int levelArgument, final int arrayArgument, final Access access,
			final JassValue miss) {
		add(name, (arguments, globalScope, triggerScope) -> {
			final CAbility ability = object(arguments, 0);
			final Meta meta = meta(fieldId(arguments, 1));
			if (!(ability instanceof AbstractCAbility) || (meta == null)) {
				return miss;
			}
			final int level = levelArgument < 0 ? 0 : integer(arguments, levelArgument);
			final int arrayIndex = arrayArgument < 0 ? -1 : integer(arguments, arrayArgument);
			if ((level < 0) || (arrayIndex < -1)) {
				return miss;
			}
			return access.run((AbstractCAbility) ability, meta, level, arrayIndex, arguments);
		});
	}

	private void registerKind(final String kind, final int levelArgument, final int arrayArgument) {
		final String suffix = levelArgument < 0 ? "" : arrayArgument < 0 ? "Level" : "LevelArray";
		final int valueArgument = 2 + (levelArgument < 0 ? 0 : 1) + (arrayArgument < 0 ? 0 : 1);
		switch (kind) {
		case "Boolean":
			field("BlzGetAbilityBoolean" + suffix + "Field", levelArgument, arrayArgument,
					(a, m, l, i, args) -> BooleanJassValue.of(parseBoolean(read(a, m, l, i))), BooleanJassValue.FALSE);
			field("BlzSetAbilityBoolean" + suffix + "Field", levelArgument, arrayArgument,
					(a, m, l, i, args) -> BooleanJassValue.of(write(a, m, l, i,
							text(args.get(valueArgument).visit(BooleanJassValueVisitor.getInstance())))),
					BooleanJassValue.FALSE);
			break;
		case "Integer":
			field("BlzGetAbilityInteger" + suffix + "Field", levelArgument, arrayArgument,
					(a, m, l, i, args) -> IntegerJassValue.of((int) parseFloat(read(a, m, l, i))),
					IntegerJassValue.ZERO);
			field("BlzSetAbilityInteger" + suffix + "Field", levelArgument, arrayArgument,
					(a, m, l, i, args) -> BooleanJassValue.of(write(a, m, l, i,
							Integer.toString(integer(args, valueArgument)))),
					BooleanJassValue.FALSE);
			break;
		case "Real":
			field("BlzGetAbilityReal" + suffix + "Field", levelArgument, arrayArgument,
					(a, m, l, i, args) -> RealJassValue.of(parseFloat(read(a, m, l, i))), RealJassValue.ZERO);
			field("BlzSetAbilityReal" + suffix + "Field", levelArgument, arrayArgument,
					(a, m, l, i, args) -> BooleanJassValue.of(write(a, m, l, i, text(
							args.get(valueArgument).visit(RealJassValueVisitor.getInstance()).floatValue()))),
					BooleanJassValue.FALSE);
			break;
		default:
			field("BlzGetAbilityString" + suffix + "Field", levelArgument, arrayArgument,
					(a, m, l, i, args) -> new StringJassValue(read(a, m, l, i)), new StringJassValue(""));
			field("BlzSetAbilityString" + suffix + "Field", levelArgument, arrayArgument,
					(a, m, l, i, args) -> BooleanJassValue.of(write(a, m, l, i, mapString(
							args.get(valueArgument).visit(StringJassValueVisitor.getInstance())))),
					BooleanJassValue.FALSE);
			break;
		}
	}

	private String mapString(final String value) {
		return value == null ? "" : this.viewer.getGameUI().getTrigStr(value);
	}

	private void registerPlain() {
		for (final String kind : new String[] { "Boolean", "Integer", "Real", "String" }) {
			registerKind(kind, -1, -1);
		}
	}

	private void registerLevel() {
		for (final String kind : new String[] { "Boolean", "Integer", "Real", "String" }) {
			registerKind(kind, 2, -1);
		}
	}

	/** The elements of a list column as the script's own copy of it, or the data's. */
	private List<String> elements(final AbstractCAbility ability, final Meta meta, final int level) {
		final List<String> elements = new ArrayList<>();
		final String own = ability.getScriptField(meta.column(level) + "#list");
		if (own != null) {
			if (!own.isEmpty()) {
				for (final String element : own.split(",", -1)) {
					elements.add(element);
				}
			}
			return elements;
		}
		final GameObject data = data(ability);
		if (data != null) {
			final List<String> base = data.getFieldAsList(meta.column(level));
			if (base != null) {
				elements.addAll(base);
			}
		}
		return elements;
	}

	private void storeElements(final AbstractCAbility ability, final Meta meta, final int level,
			final List<String> elements) {
		ability.setScriptField(meta.column(level) + "#list", String.join(",", elements));
		refill(ability);
	}

	/**
	 * Level array fields are the lists of a level (unit ids a spell summons, buffs it gives): a get reads one
	 * element, a set replaces it, an add appends one and a remove takes the first element of that value out.
	 */
	private void registerLevelArray() {
		for (final String kind : new String[] { "Boolean", "Integer", "Real", "String" }) {
			registerKind(kind, 2, 3);
			final int valueArgument = 4;
			final String addName = "BlzAddAbility" + kind + "LevelArrayField";
			final String removeName = "BlzRemoveAbility" + kind + "LevelArrayField";
			field(addName, 2, -1, (a, m, l, i, args) -> {
				final List<String> elements = elements(a, m, l);
				elements.add(valueText(kind, args, 3));
				storeElements(a, m, l, elements);
				return BooleanJassValue.TRUE;
			}, BooleanJassValue.FALSE);
			field(removeName, 2, -1, (a, m, l, i, args) -> {
				final List<String> elements = elements(a, m, l);
				final boolean removed = elements.remove(valueText(kind, args, 3));
				if (removed) {
					storeElements(a, m, l, elements);
				}
				return BooleanJassValue.of(removed);
			}, BooleanJassValue.FALSE);
			if (valueArgument < 0) {
				throw new IllegalStateException();
			}
		}
	}

	private String valueText(final String kind, final List<JassValue> args, final int index) {
		switch (kind) {
		case "Boolean":
			return text(args.get(index).visit(BooleanJassValueVisitor.getInstance()));
		case "Integer":
			return Integer.toString(integer(args, index));
		case "Real":
			return text(args.get(index).visit(RealJassValueVisitor.getInstance()).floatValue());
		default:
			return mapString(args.get(index).visit(StringJassValueVisitor.getInstance()));
		}
	}
}
