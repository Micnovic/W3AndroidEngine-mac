package com.etheller.warsmash.viewer5.handlers.w3x;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.jass.ScriptPreload;
import com.etheller.warsmash.parsers.w3x.unitsdoo.War3MapUnitsDoo;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3iFlags;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.MapLoader;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitTypeData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManagerEntry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFromLabel;

/**
 * Loads the models of the units a map starts with while its loading screen is still up, one type for each step of the
 * loader. Left to the first frames of the game, they were all loaded in one frame that showed nothing new.
 */
final class MapViewerPrewarm {
	private static final Pattern SCRIPT_LABEL_SOUND = Pattern
			.compile("(?:SetAmbientDaySound|SetAmbientNightSound|CreateMIDISound)\\s*\\(\\s*\"([^\"]+)\"");
	private static final int MAX_PREFETCHED_FILES = 4;
	private static final Pattern SCRIPT_PRELOAD = Pattern
			.compile("\\bPreload\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
	private static final Pattern SCRIPT_PRELOADER = Pattern
			.compile("\\bPreloader\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
	private static final long PRELOAD_BATCH_NANOS = 8_000_000L;

	private MapViewerPrewarm() {
	}

	/**
	 * Makes the unit types' render data and queues the loading of the types the map places, and those its melee
	 * script starts the players with, onto the loader.
	 */
	static void queue(final War3MapViewer viewer, final War3MapW3i w3i, final MapLoader loader) throws IOException {
		final RenderUnitTypeData types = new RenderUnitTypeData(viewer.allObjectData.getUnits(), viewer.mapMpq, viewer,
				viewer.unitAckSoundsTable, viewer.terrain.uberSplatTable);
		viewer.renderUnitTypeData = types;
		viewer.renderUnitTypeDataObjects = viewer.allObjectData;

		final String script = readMapScript(viewer);
		// First, so that the decoding runs beside the model loading that follows.
		loader.addLoadTask(() -> prefetchAmbientSounds(viewer, script, loader));

		final Set<War3ID> placed = new LinkedHashSet<>();
		final Set<War3ID> ownTypes = new LinkedHashSet<>();
		final Set<War3ID> otherTypes = new LinkedHashSet<>();
		// Whether the map's units are made from this file or by its script, which makes the ones the editor placed.
		if (viewer.dataSource.has("war3mapUnits.doo")) {
			final War3MapUnitsDoo dooFile = viewer.mapMpq.readUnits(w3i);
			for (final com.etheller.warsmash.parsers.w3x.unitsdoo.Unit unit : dooFile.getUnits()) {
				if (!War3MapViewer.sloc.equals(unit.getId())) {
					placed.add(unit.getId());
					(unit.getPlayer() == viewer.localPlayerIndex ? ownTypes : otherTypes).add(unit.getId());
				}
			}
		}
		final Set<War3ID> started = new LinkedHashSet<>();
		if (w3i.hasFlag(War3MapW3iFlags.MELEE_MAP)) {
			addMeleeStartingTypes(viewer, started, -1);
			addMeleeStartingTypes(viewer, ownTypes, viewer.localPlayerIndex);
			otherTypes.addAll(started);
		}
		started.removeAll(placed);
		otherTypes.removeAll(ownTypes);
		loader.addLoadTask(() -> prefetchUnitSounds(viewer, ownTypes, otherTypes, loader));
		for (final War3ID typeId : placed) {
			loader.addLoadTask(loader.modelTask(() -> load(types, typeId)));
		}
		for (final War3ID typeId : started) {
			loader.addLoadTask(loader.modelTask(() -> load(types, typeId)));
		}
		loader.addLoadTask(() -> queuePreloads(viewer, w3i, script, loader));
	}

	private static String readMapScript(final War3MapViewer viewer) {
		for (final String path : new String[] { "war3map.j", "Scripts\\war3map.j" }) {
			if (viewer.mapMpq.has(path)) {
				return readText(viewer.mapMpq, path);
			}
		}
		return "";
	}

	private static String readText(final DataSource dataSource, final String path) {
		try (InputStream stream = dataSource.getResourceAsStream(path)) {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			final byte[] buffer = new byte[8192];
			for (int read = stream.read(buffer); read != -1; read = stream.read(buffer)) {
				bytes.write(buffer, 0, read);
			}
			return new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
		}
		catch (final IOException | RuntimeException e) {
			Log.error(e);
			return "";
		}
	}

	/** Reads the escapes left to right in one pass, as the game's script reader does: a pair is never reread. */
	static String unescape(final String jassString) {
		if (jassString.indexOf('\\') == -1) {
			return jassString;
		}
		final StringBuilder result = new StringBuilder(jassString.length());
		for (int i = 0; i < jassString.length(); i++) {
			final char c = jassString.charAt(i);
			if ((c != '\\') || (i + 1 == jassString.length())) {
				result.append(c);
				continue;
			}
			final char escaped = jassString.charAt(++i);
			switch (escaped) {
			case 'n':
				result.append('\n');
				break;
			case 't':
				result.append('\t');
				break;
			case 'r':
				result.append('\r');
				break;
			case 'b':
				result.append('\b');
				break;
			case 'f':
				result.append('\f');
				break;
			case '"':
			case '\\':
				result.append(escaped);
				break;
			default:
				result.append(c).append(escaped);
				break;
			}
		}
		return result.toString();
	}

	/**
	 * Queues the files the map's script preloads at its start, which the game loaded in its first frame: the lists
	 * the script hands to Preloader, and what the standard melee script gives the playing races.
	 */
	private static void queuePreloads(final War3MapViewer viewer, final War3MapW3i w3i, final String script,
			final MapLoader loader) {
		final Set<String> lists = new LinkedHashSet<>();
		if (w3i.hasFlag(War3MapW3iFlags.MELEE_MAP) && script.contains("MeleeStartingUnits")) {
			lists.add("scripts\\SharedMelee.pld");
			for (final String race : playingRaces(viewer, -1)) {
				final String name = meleeListName(race);
				if (name != null) {
					lists.add("scripts\\" + name + "Melee.pld");
				}
			}
		}
		final Matcher matcher = SCRIPT_PRELOADER.matcher(script);
		while (matcher.find()) {
			lists.add(unescape(matcher.group(1)));
		}
		final Set<String> files = new LinkedHashSet<>();
		for (final String list : lists) {
			if (viewer.dataSource.has(list)) {
				addPreloadedFiles(files, readText(viewer.dataSource, list));
			}
		}
		addPreloadedFiles(files, script);
		if (!files.isEmpty()) {
			final Deque<String> names = new ArrayDeque<>(files);
			loader.addLoadTask(loader.modelTask(() -> loadPreloaded(viewer, names, loader)));
		}
	}

	private static void addPreloadedFiles(final Set<String> files, final String script) {
		final Matcher matcher = SCRIPT_PRELOAD.matcher(script);
		while (matcher.find()) {
			files.add(unescape(matcher.group(1)));
		}
	}

	/** Loads as many as fit a frame, and queues the rest, so that the loading screen keeps moving. */
	private static void loadPreloaded(final War3MapViewer viewer, final Deque<String> names, final MapLoader loader) {
		final long end = System.nanoTime() + PRELOAD_BATCH_NANOS;
		do {
			ScriptPreload.load(viewer, names.poll());
		}
		while (!names.isEmpty() && (System.nanoTime() < end));
		if (!names.isEmpty()) {
			loader.addLoadTask(loader.modelTask(() -> loadPreloaded(viewer, names, loader)));
		}
	}

	private static String meleeListName(final String race) {
		switch (race) {
		case "human":
			return "Human";
		case "orc":
			return "Orc";
		case "undead":
			return "Undead";
		case "nightelf":
			return "NightElf";
		default:
			return null;
		}
	}

	/**
	 * Starts the decoding of the ambience the map's script names, which it starts in the first game step; where a
	 * sound is decoded on the calling thread, that decoding held up the first frame for a second.
	 */
	private static void prefetchAmbientSounds(final War3MapViewer viewer, final String script,
			final MapLoader loader) throws IOException {
		final Matcher matcher = SCRIPT_LABEL_SOUND.matcher(script);
		final Set<String> labels = new LinkedHashSet<>();
		while (matcher.find()) {
			labels.add(matcher.group(1));
		}
		loader.addSlices(labels.iterator(), label -> {
			final UnitSound sound = viewer.uiSounds.getSound(label);
			if (sound.getSoundCount() <= MAX_PREFETCHED_FILES) {
				sound.prefetch();
			}
			else if (sound.getSoundCount() > 0) {
				// The script starts the day ambience in the first step, whose file is known from the step and the count.
				sound.prefetch(CSoundFromLabel.variant(0, 0, sound.getSoundCount()));
			}
		}, () -> {
		});
	}

	/**
	 * Starts the decoding of the answers the sound preload of a game's first step asks for, from the unit types the
	 * map is going to start with, which is what that step finds.
	 */
	private static void prefetchUnitSounds(final War3MapViewer viewer, final Set<War3ID> own,
			final Set<War3ID> others, final MapLoader loader) throws IOException {
		final Set<War3ID> made = new LinkedHashSet<>(own);
		MapViewerLoading.addMadeUnitTypes(viewer, made);
		made.removeAll(own);
		others.removeAll(own);
		loader.addSlices(soundPrefetches(viewer, own, made, others).iterator(), Runnable::run, () -> {
		});
	}

	/** Starts the decoding of what the sound preload asks for of these unit types. */
	static void prefetchSoundsOf(final War3MapViewer viewer, final Set<War3ID> own, final Set<War3ID> made,
			final Set<War3ID> others) {
		for (final Runnable prefetch : soundPrefetches(viewer, own, made, others)) {
			prefetch.run();
		}
	}

	/** One step for each unit type, which a copy of its sounds out of the game data makes take long. */
	private static List<Runnable> soundPrefetches(final War3MapViewer viewer, final Set<War3ID> own,
			final Set<War3ID> made, final Set<War3ID> others) {
		final List<Runnable> prefetches = new ArrayList<>();
		for (final War3ID typeId : own) {
			prefetches.add(() -> {
				final UnitSoundset soundset = viewer.renderUnitTypeData.getSoundset(typeId);
				if (soundset != null) {
					soundset.prefetch();
				}
			});
		}
		for (final War3ID typeId : made) {
			prefetches.add(() -> {
				final UnitSoundset soundset = viewer.renderUnitTypeData.getSoundset(typeId);
				if (soundset != null) {
					soundset.ready.prefetch();
					soundset.what.prefetch();
				}
			});
		}
		for (final War3ID typeId : others) {
			prefetches.add(() -> {
				final UnitSoundset soundset = viewer.renderUnitTypeData.getSoundset(typeId);
				if (soundset != null) {
					soundset.what.prefetch();
				}
			});
		}
		return prefetches;
	}

	private static void load(final RenderUnitTypeData types, final War3ID typeId) {
		try {
			types.get(typeId);
		}
		catch (final RuntimeException e) {
			// The same type is made again when a unit of it is placed, and fails there as it would have anyway.
			Log.error(e);
		}
	}

	private static Set<String> playingRaces(final War3MapViewer viewer, final int playerIndex) {
		final CSimulation simulation = viewer.simulation;
		final Set<String> races = new LinkedHashSet<>();
		for (int i = 0; i < (viewer.getGameData().getMaxPlayers() - 4); i++) {
			if ((playerIndex != -1) && (i != playerIndex)) {
				continue;
			}
			final CPlayer player = simulation.getPlayer(i);
			if ((player.getSlotState() == CPlayerSlotState.PLAYING) && (player.getRace() != null)) {
				final CRaceManagerEntry entry = viewer.getGameData().getRaceManager().get(player.getRace());
				if (entry != null) {
					races.add(entry.getKey().toLowerCase(java.util.Locale.ROOT));
				}
			}
		}
		return races;
	}

	/**
	 * The town hall, the workers and the mine the standard melee script gives each playing race.
	 *
	 * @param playerIndex the only player to give them to, or -1 for all
	 */
	private static void addMeleeStartingTypes(final War3MapViewer viewer, final Set<War3ID> started,
			final int playerIndex) {
		for (final String race : playingRaces(viewer, playerIndex)) {
			final String[] ids;
			switch (race) {
			case "human":
				ids = new String[] { "htow", "hpea" };
				break;
			case "orc":
				ids = new String[] { "ogre", "opeo" };
				break;
			case "undead":
				ids = new String[] { "unpl", "uaco", "ugol" };
				break;
			case "nightelf":
				ids = new String[] { "etol", "ewsp" };
				break;
			default:
				ids = new String[0];
				break;
			}
			for (final String id : ids) {
				started.add(War3ID.fromString(id));
			}
		}
	}
}
