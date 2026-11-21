package com.etheller.warsmash.parsers.jass;

import java.util.Locale;

import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;

/**
 * What a script's Preload of a file loads.
 */
public final class ScriptPreload {
	private ScriptPreload() {
	}

	private static boolean hasViewerHandler(final War3MapViewer war3MapViewer, final String filename) {
		final int dotIndex = filename.lastIndexOf('.');
		return (dotIndex != -1)
				&& (war3MapViewer.findHandler(filename.substring(dotIndex).toLowerCase(Locale.US)) != null);
	}

	/**
	 * The melee and campaign preload lists of 2.0 and later name the Reforged birth, death and portrait models of
	 * every building, which the Classic data does not have. Loading them through the graphics packages only fills
	 * memory with models nothing shows, so a preload takes only files the data has under their own path.
	 */
	private static boolean hasOwnFile(final War3MapViewer war3MapViewer, final String filename) {
		final String path = war3MapViewer.mapPathSolver.solve(filename, war3MapViewer.solverParams).getFinalSrc();
		return war3MapViewer.dataSource.has(path);
	}

	public static boolean isLoadable(final War3MapViewer war3MapViewer, final String filename) {
		final String trimmed = filename.trim();
		return hasViewerHandler(war3MapViewer, trimmed) && hasOwnFile(war3MapViewer, trimmed);
	}

	public static void load(final War3MapViewer war3MapViewer, final String filename) {
		if (isLoadable(war3MapViewer, filename)) {
			try {
				// loading makes models and textures, which only the render thread can
				RenderSide.of(war3MapViewer).run("Preload", () -> war3MapViewer.load(filename.trim(),
						war3MapViewer.mapPathSolver, war3MapViewer.solverParams));
			}
			catch (final Exception exc) {
				Log.error("Preload(\"" + filename + "\") failed!");
				Log.error(exc);
			}
		}
	}
}
