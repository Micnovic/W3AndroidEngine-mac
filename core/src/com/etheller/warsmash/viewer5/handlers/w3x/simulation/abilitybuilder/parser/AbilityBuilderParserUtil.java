package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.AbilityBuilderGsonBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public class AbilityBuilderParserUtil {
	public static void loadAbilityBuilderFiles(final AbilityBuilderFileListener listener) {
		loadAbilityBuilderFiles(listener, "abilityBehaviors");
	}
	/**
	 * The behaviors parsed ahead on a helper thread, by folder. Reading them takes the better part of a second (the
	 * parser registers every behavior class first), which a game's loading then does not wait for.
	 */
	private static final Map<String, Future<List<AbilityBuilderParser>>> PREPARED = new HashMap<>();

	private static final class Workers {
		static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
			final Thread thread = new Thread(runnable, "ability-prepare");
			thread.setDaemon(true);
			thread.setPriority(Thread.NORM_PRIORITY - 1);
			return thread;
		});
	}

	/** Starts parsing a folder's behaviors for the next {@link #loadAbilityBuilderFiles} of it. */
	public static synchronized void prepare(final String folder) {
		if (!PREPARED.containsKey(folder)) {
			PREPARED.put(folder, Workers.EXECUTOR.submit(() -> parseAll(folder)));
		}
	}

	public static void loadAbilityBuilderFiles(final AbilityBuilderFileListener listener, String folder) {
		final Future<List<AbilityBuilderParser>> prepared;
		synchronized (AbilityBuilderParserUtil.class) {
			prepared = PREPARED.remove(folder);
		}
		List<AbilityBuilderParser> behaviors = null;
		if (prepared != null) {
			try {
				behaviors = prepared.get();
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			catch (final ExecutionException e) {
				Log.error(e);
			}
		}
		if (behaviors == null) {
			behaviors = parseAll(folder);
		}
		for (final AbilityBuilderParser behavior : behaviors) {
			listener.callback(behavior);
		}
	}

	private static List<AbilityBuilderParser> parseAll(final String folder) {
		final List<AbilityBuilderParser> behaviors = new ArrayList<>();
		final Gson gson = AbilityBuilderGsonBuilder.create();
		try {
			// Internal files resolve to the APK assets on Android and to the working directory on desktop.
			final FileHandle abilityBehaviorsDir = Gdx.files != null ? Gdx.files.internal(folder)
					: new FileHandle(new File(folder));
			loadAbilityBuilderFiles(gson, abilityBehaviorsDir, behaviors::add);
		}
		catch (final Exception e) {
			Log.error(e);
		}
		return behaviors;
	}

	private static void loadAbilityBuilderFiles(final Gson gson, final FileHandle directory,
			final AbilityBuilderFileListener listener) {
		final FileHandle[] children = directory.list();
		Arrays.sort(children, (a, b) -> a.name().compareTo(b.name()));
		for (final FileHandle child : children) {
			if (child.extension().equalsIgnoreCase("json")) {
				loadAbilityBuilderFile(gson, child.reader("UTF-8"), child.name(), listener);
			}
			else {
				loadAbilityBuilderFiles(gson, child, listener);
			}
		}
	}

	public static void loadAbilityBuilderFile(final Gson gson, final File abilityBehaviorFile,
			final AbilityBuilderFileListener listener) throws FileNotFoundException {
		if (abilityBehaviorFile.isDirectory()) {
			final File[] abilityBehaviorFiles = abilityBehaviorFile.listFiles();
			if (abilityBehaviorFiles != null) {
				for (final File subAbilityBehaviorFile : abilityBehaviorFiles) {
					loadAbilityBuilderFile(gson, subAbilityBehaviorFile, listener);
				}
			}
		}
		loadAbilityBuilderFile(gson, new FileReader(abilityBehaviorFile), abilityBehaviorFile.getName(), listener);
	}

	private static void loadAbilityBuilderFile(final Gson gson, final Reader reader, final String name,
			final AbilityBuilderFileListener listener) {
		try (reader) {
			final AbilityBuilderFile behaviors = gson.fromJson(reader, AbilityBuilderFile.class);
			for (final AbilityBuilderParser behavior : behaviors.getAbilityList()) {
				listener.callback(behavior);
			}
		}
		catch (final JsonParseException e) {
			Log.error("Failed to load Ability Builder config file: " + name);
			Log.error(e);
		}
		catch (final IllegalArgumentException e) {
			Log.error("Failed to load Ability Builder config file: " + name);
			Log.error(e);
		}
		catch (final IOException e) {
			Log.error(e);
		}
	}

	public static interface AbilityBuilderFileListener {
		void callback(AbilityBuilderParser behavior);
	}
}
