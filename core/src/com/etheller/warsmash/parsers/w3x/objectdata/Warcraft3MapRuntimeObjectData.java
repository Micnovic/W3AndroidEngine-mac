package com.etheller.warsmash.parsers.w3x.objectdata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.ObjectData;
import com.etheller.warsmash.units.StandardObjectData;
import com.etheller.warsmash.units.StandardObjectData.WarcraftData;
import com.etheller.warsmash.units.collapsed.CollapsedObjectData;
import com.etheller.warsmash.units.custom.WTS;
import com.etheller.warsmash.units.custom.WTSFile;
import com.etheller.warsmash.units.manager.MutableObjectData.WorldEditorDataType;
import com.etheller.warsmash.util.WorldEditStrings;

public final class Warcraft3MapRuntimeObjectData {
	private final ObjectData units;
	private final ObjectData items;
	private final ObjectData destructibles;
	private final ObjectData doodads;
	private final ObjectData abilities;
	private final ObjectData buffs;
	private final ObjectData upgrades;
	private final DataTable standardUpgradeEffectMeta;
	private final List<ObjectData> datas;
	private transient Map<WorldEditorDataType, ObjectData> typeToData = new HashMap<>();
	private final WTS wts;

	public Warcraft3MapRuntimeObjectData(final ObjectData units, final ObjectData items, final ObjectData destructibles,
			final ObjectData doodads, final ObjectData abilities, final ObjectData buffs, final ObjectData upgrades,
			final DataTable standardUpgradeEffectMeta, final WTS wts) {
		this.units = units;
		this.items = items;
		this.destructibles = destructibles;
		this.doodads = doodads;
		this.abilities = abilities;
		this.buffs = buffs;
		this.upgrades = upgrades;
		this.standardUpgradeEffectMeta = standardUpgradeEffectMeta;
		this.datas = new ArrayList<>();
		this.datas.add(units);
		this.typeToData.put(WorldEditorDataType.UNITS, units);
		this.datas.add(items);
		this.typeToData.put(WorldEditorDataType.ITEM, items);
		this.datas.add(destructibles);
		this.typeToData.put(WorldEditorDataType.DESTRUCTIBLES, destructibles);
		this.datas.add(doodads);
		this.typeToData.put(WorldEditorDataType.DOODADS, doodads);
		this.datas.add(abilities);
		this.typeToData.put(WorldEditorDataType.ABILITIES, abilities);
		this.datas.add(buffs);
		this.typeToData.put(WorldEditorDataType.BUFFS_EFFECTS, buffs);
		this.datas.add(upgrades);
		this.typeToData.put(WorldEditorDataType.UPGRADES, upgrades);
		for (final ObjectData data : this.datas) {
		}
		this.wts = wts;
	}

	public ObjectData getDataByType(final WorldEditorDataType type) {
		return this.typeToData.get(type);
	}

	public ObjectData getUnits() {
		return this.units;
	}

	public ObjectData getItems() {
		return this.items;
	}

	public ObjectData getDestructibles() {
		return this.destructibles;
	}

	public ObjectData getDoodads() {
		return this.doodads;
	}

	public ObjectData getAbilities() {
		return this.abilities;
	}

	public ObjectData getBuffs() {
		return this.buffs;
	}

	public ObjectData getUpgrades() {
		return this.upgrades;
	}

	public DataTable getStandardUpgradeEffectMeta() {
		return this.standardUpgradeEffectMeta;
	}

	public List<ObjectData> getDatas() {
		return this.datas;
	}

	public WTS getWts() {
		return this.wts;
	}

	private static WTS loadWTS(final DataSource dataSource, final String name) throws IOException {
		final WTS wts = dataSource.has(name) ? new WTSFile(dataSource.getResourceAsStream(name)) : WTS.DO_NOTHING;
		return wts;
	}

	public static WTS loadWTS(final DataSource dataSource) throws IOException {
		return loadWTS(dataSource, "war3map.wts");
	}

	public static WTS loadCampaignWTS(final DataSource dataSource) throws IOException {
		return loadWTS(dataSource, "war3campaign.wts");
	}

	public static Warcraft3MapRuntimeObjectData load(final DataSource dataSource, final boolean inlineWTS)
			throws IOException {
		final WTS wts = loadWTS(dataSource);
		return load(dataSource, inlineWTS, wts);
	}

	public static Warcraft3MapRuntimeObjectData load(final DataSource dataSource, final boolean inlineWTS,
			final WTS wts) throws IOException {
		final WTS campaignWTS = loadCampaignWTS(dataSource);
		return load(dataSource, inlineWTS, wts, campaignWTS);
	}

	public static Warcraft3MapRuntimeObjectData load(final DataSource dataSource, final boolean inlineWTS,
			final WTS wts, final WTS campaignWTS) throws IOException {
		return load(dataSource, inlineWTS, wts, campaignWTS, GraphicsSet.CLASSIC);
	}

	public static Warcraft3MapRuntimeObjectData load(final DataSource dataSource, final boolean inlineWTS,
			final WTS wts, final WTS campaignWTS, final GraphicsSet graphics) throws IOException {
		final Loading loading = loadInSteps(dataSource, inlineWTS, wts, campaignWTS, graphics);
		for (final Step step : loading.getSteps()) {
			step.run();
		}
		return loading.getResult();
	}

	/** A part of the loading, small enough to run in a frame of its own. */
	public interface Step {
		void run() throws IOException;
	}

	/** The loading in the order its steps are to run; the result is there once the last one has. */
	public static final class Loading {
		private final List<Step> steps = new ArrayList<>();
		private Warcraft3MapRuntimeObjectData result;

		public List<Step> getSteps() {
			return this.steps;
		}

		public Warcraft3MapRuntimeObjectData getResult() {
			return this.result;
		}
	}

	public static Loading loadInSteps(final DataSource dataSource, final boolean inlineWTS, final WTS wts,
			final WTS campaignWTS, final GraphicsSet graphics) {
		final Loading loading = new Loading();
		final StandardObjectData standardObjectData = new StandardObjectData(dataSource, graphics);
		final WarcraftData[] standard = new WarcraftData[7];
		final DataTable[] meta = new DataTable[7];
		final MapObjectChangesets[] changesets = new MapObjectChangesets[1];

		loading.steps.add(() -> standard[0] = standardObjectData.getStandardUnits());
		loading.steps.add(() -> {
			standard[1] = standardObjectData.getStandardItems();
			standard[2] = standardObjectData.getStandardDoodads();
			standard[3] = standardObjectData.getStandardDestructables();
		});
		loading.steps.add(() -> standard[4] = standardObjectData.getStandardAbilities());
		loading.steps.add(() -> {
			standard[5] = standardObjectData.getStandardAbilityBuffs();
			standard[6] = standardObjectData.getStandardUpgrades();
		});
		loading.steps.add(() -> {
			meta[0] = standardObjectData.getStandardUnitMeta();
			meta[1] = standardObjectData.getStandardDoodadMeta();
			meta[2] = standardObjectData.getStandardDestructableMeta();
			meta[3] = standardObjectData.getStandardAbilityMeta();
			meta[4] = standardObjectData.getStandardAbilityBuffMeta();
			meta[5] = standardObjectData.getStandardUpgradeMeta();
			meta[6] = standardObjectData.getStandardUpgradeEffectMeta();
		});
		loading.steps.add(() -> changesets[0] = MapObjectChangesets.read(dataSource, inlineWTS, wts, campaignWTS,
				standard[0], standard[1]));

		final WorldEditStrings worldEditStrings = standardObjectData.getWorldEditStrings();
		loading.steps.add(() -> CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.UNITS, standard[0],
				meta[0], changesets[0].units));
		loading.steps.add(() -> CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.ITEM, standard[1],
				meta[0], changesets[0].items));
		loading.steps.add(() -> CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.DOODADS, standard[2],
				meta[1], changesets[0].doodads));
		loading.steps.add(() -> CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.DESTRUCTIBLES,
				standard[3], meta[2], changesets[0].destructables));
		loading.steps.add(() -> CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.ABILITIES, standard[4],
				meta[3], changesets[0].abilities));
		loading.steps.add(() -> CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.BUFFS_EFFECTS,
				standard[5], meta[4], changesets[0].buffs));
		loading.steps.add(() -> CollapsedObjectData.apply(worldEditStrings, WorldEditorDataType.UPGRADES, standard[6],
				meta[5], changesets[0].upgrades));
		loading.steps.add(() -> loading.result = new Warcraft3MapRuntimeObjectData(standard[0], standard[1],
				standard[3], standard[2], standard[4], standard[5], standard[6], meta[6], wts));
		return loading;
	}
}
