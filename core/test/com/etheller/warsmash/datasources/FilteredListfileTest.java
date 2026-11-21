package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.MapListContainer;

/** The accepted part of a list is the same whether it is asked for directly or taken from the merged list. */
class FilteredListfileTest {
	private static final class Listed implements DataSource {
		private final List<String> paths;

		Listed(final String... paths) {
			this.paths = Arrays.asList(paths);
		}

		@Override
		public InputStream getResourceAsStream(final String filepath) {
			return null;
		}

		@Override
		public File getFile(final String filepath) {
			return null;
		}

		@Override
		public File getDirectory(final String filepath) {
			return null;
		}

		@Override
		public ByteBuffer read(final String path) {
			return null;
		}

		@Override
		public boolean has(final String filepath) {
			return false;
		}

		@Override
		public Collection<String> getListfile() {
			return this.paths;
		}

		@Override
		public void close() {
		}
	}

	@Test
	void compoundAndSubdirAnswerLikeTheMergedList() {
		final DataSource first = new Listed("Maps\\A.w3x", "units\\x.mdx", "war3.w3mod\\maps\\B.W3M",
				"Maps\\WC3Probes\\T.w3x");
		final DataSource second = new Listed("Maps\\A.w3x", "Maps\\C.w3x", "readme.txt");
		final CompoundDataSource compound = new CompoundDataSource(Arrays.asList(first, second));
		assertEquals(new HashSet<>(Arrays.asList("Maps\\A.w3x", "Maps\\C.w3x")),
				new HashSet<>(compound.getListfile(MapListContainer::isListedMap)));
		assertEquals(2, compound.getListfile(MapListContainer::isListedMap).size());

		final SubdirDataSource subdir = new SubdirDataSource(compound, "war3.w3mod\\");
		assertEquals(Arrays.asList("maps\\B.W3M"), subdir.getListfile(path -> path.toLowerCase().endsWith(".w3m")));
	}

	@Test
	void extensionListingAnswersLikeTheFilterOnEveryLayer() {
		final DataSource first = new Listed("war3.w3mod\\a\\One.ANIMSET_ingame", "war3.w3mod\\.animset_ingame",
				"war3.w3mod\\animset_ingame", "other\\Two.animset_ingame", "war3.w3mod\\b\\x.mdx");
		final DataSource second = new Listed("war3.w3mod\\a\\One.ANIMSET_ingame", "c\\Three.animset_ingame");
		final CompoundDataSource compound = new CompoundDataSource(Arrays.asList(first, second));
		final SubdirDataSource subdir = new SubdirDataSource(compound, "war3.w3mod\\");
		final String extension = ".animset_ingame";
		final DataSource.PathFilter filter = DataSource.PathFilter.endingWith(extension);
		assertEquals(new ArrayList<>(first.getListfile(filter)), new ArrayList<>(first.getListfileByExtension(extension)));
		assertEquals(new ArrayList<>(compound.getListfile(filter)),
				new ArrayList<>(compound.getListfileByExtension(extension)));
		assertEquals(new ArrayList<>(subdir.getListfile(filter)),
				new ArrayList<>(subdir.getListfileByExtension(extension)));
		assertEquals(Arrays.asList("a\\One.ANIMSET_ingame", ".animset_ingame"),
				new ArrayList<>(subdir.getListfileByExtension(extension)));
	}

	@Test
	void mapExtensionsAreRecognizedInAnyCase() {
		assertEquals(true, MapListContainer.isListedMap("Maps\\Melee\\X.W3X"));
		assertEquals(true, MapListContainer.isListedMap("x.w3m"));
		assertEquals(false, MapListContainer.isListedMap("Maps\\x.mdx"));
		assertEquals(false, MapListContainer.isListedMap("w3x"));
		assertEquals(false, MapListContainer.isListedMap("units\\x.w3x"));
	}
}
