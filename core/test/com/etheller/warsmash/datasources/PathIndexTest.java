package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The index answers as the set of lower case copies of the listed paths did, with the trailing separators dropped.
 */
class PathIndexTest {
	private static final List<String> LISTED = Arrays.asList("war3.w3mod\\units\\human\\footman\\footman.mdx",
			"War3.w3mod\\_hd.w3mod\\Units\\Orc\\Grunt\\Grunt.MDX", "war3.w3mod\\ui\\framedef\\glue\\mainmenu.fdf",
			"war3.w3mod\\\u0130stanbul\\Dotted.mdx", "war3.w3mod\\caf\u00e9\\menu.txt",
			"war3.w3mod\\K\u212A\\Kelvin.mdx", "a", "", "war3.w3mod\\units\\human\\footman\\footman.mdx");

	private static String listedForm(final String path) {
		int end = path.length();
		while ((end > 0) && (path.charAt(end - 1) == '\\')) {
			end--;
		}
		return path.substring(0, end).toLowerCase(Locale.ROOT);
	}

	@Test
	void findsPathsInAnyLetterCaseAndWithoutTrailingSeparators() {
		final PathIndex index = new PathIndex(LISTED);
		final Set<String> lowered = new HashSet<>();
		for (final String path : LISTED) {
			lowered.add(path.toLowerCase(Locale.ROOT));
		}
		final List<String> asked = new ArrayList<>(LISTED);
		for (final String path : LISTED) {
			asked.add(path.toUpperCase(Locale.ROOT));
			asked.add(path.toLowerCase(Locale.ROOT));
			asked.add(path + "\\");
			asked.add(path + "\\\\");
			asked.add(path + "x");
			asked.add("x" + path);
		}
		asked.add("\\");
		asked.add("war3.w3mod\\units\\human\\footman");
		asked.add("war3.w3mod\\k\\kelvin.mdx");
		asked.add("war3.w3mod\\\u212A\\kelvin.mdx");
		asked.add("war3.w3mod\\ui\\framedef\\glue\\mainmenu.fdf\\");
		for (final String path : asked) {
			final int position = index.indexOf(path);
			assertEquals(lowered.contains(listedForm(path)), position != -1, path);
			if (position != -1) {
				assertEquals(listedForm(path), index.get(position).toLowerCase(Locale.ROOT), path);
			}
		}
	}

	@Test
	void aPathAsAskedIsFoundAsItsNormalizedForm() {
		final PathIndex index = new PathIndex(LISTED);
		final List<String> asked = new ArrayList<>();
		for (final String path : LISTED) {
			asked.add(path.replace('\\', '/'));
			asked.add(path.replace('\\', ':'));
			asked.add(path.replace('\\', '/').toUpperCase(Locale.ROOT) + "/");
			asked.add(path.replace('\\', '/') + "x/");
		}
		asked.add("/");
		asked.add("units/human/footman/footman.mdx");
		for (final String path : asked) {
			assertEquals(index.indexOf(CascDataSource.normalizePath(path)), index.indexOf(path), path);
			assertEquals(index.indexOf("war3.w3mod", CascDataSource.normalizePath(path)),
					index.indexOf("war3.w3mod", path), path);
		}
		assertEquals(0, index.indexOf("war3.w3mod/units/human/FOOTMAN/footman.mdx"));
	}

	@Test
	void aPrefixAndAPathAreAskedAsOneJoinedPath() {
		final PathIndex index = new PathIndex(LISTED);
		final String[][] pairs = { { "war3.w3mod", "units\\human\\footman\\footman.mdx" },
				{ "War3.w3mod\\_HD.w3mod", "units\\orc\\grunt\\grunt.mdx" },
				{ "war3.w3mod", "UI\\FrameDef\\Glue\\MainMenu.fdf" },
				{ "war3.w3mod", "units\\human\\footman\\footman.mdl" },
				{ "war3.w3mod", "\u0130stanbul\\dotted.mdx" }, { "x", "a" }, { "war3.w3mod", "caf\u00e9\\menu.txt" } };
		for (final String[] pair : pairs) {
			assertEquals(index.indexOf(pair[0] + "\\" + pair[1]), index.indexOf(pair[0], pair[1]),
					pair[0] + " + " + pair[1]);
		}
		assertEquals(0, index.indexOf("war3.w3mod", "units\\human\\footman\\footman.mdx"));
		assertEquals(-1, index.indexOf("war3.w3mod", "units\\human\\footman\\footman.mdl"));
	}

	@Test
	void aDuplicatePathGivesTheFirstPosition() {
		final PathIndex index = new PathIndex(LISTED);
		assertEquals(0, index.indexOf("WAR3.W3MOD\\UNITS\\HUMAN\\FOOTMAN\\FOOTMAN.MDX"));
	}

	@Test
	void manyRandomPathsAreAllFoundAndNeighboursAreNot() {
		final Random random = new Random(7);
		final List<String> listed = new ArrayList<>();
		final Set<String> lowered = new HashSet<>();
		for (int i = 0; i < 20000; i++) {
			final StringBuilder builder = new StringBuilder("war3.w3mod");
			final int parts = 1 + random.nextInt(4);
			for (int p = 0; p < parts; p++) {
				builder.append('\\');
				final int length = 1 + random.nextInt(8);
				for (int c = 0; c < length; c++) {
					builder.append((char) ((random.nextInt(3) == 0 ? 'A' : 'a') + random.nextInt(26)));
				}
			}
			listed.add(builder.toString());
			lowered.add(builder.toString().toLowerCase(Locale.ROOT));
		}
		final PathIndex index = new PathIndex(listed);
		for (final String path : listed) {
			assertEquals(true, index.indexOf(path.toUpperCase(Locale.ROOT)) != -1, path);
			assertEquals(lowered.contains(path.toLowerCase(Locale.ROOT) + "x"), index.indexOf(path + "x") != -1);
		}
		assertEquals(-1, index.indexOf("war3.w3mod"));
	}
}
