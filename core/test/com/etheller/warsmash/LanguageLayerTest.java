package com.etheller.warsmash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.CompoundDataSourceDescriptor;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.DataSourceDescriptor;
import com.etheller.warsmash.datasources.FolderDataSourceDescriptor;

/** The language layer a flat Classic folder keeps in its "_locales" folder is read over the folder's own files. */
class LanguageLayerTest {
	@TempDir
	File temp;

	private void write(final String path, final String text) throws IOException {
		final File file = new File(this.temp, path);
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private DataSource source(final String path, final String language) {
		final List<DataSourceDescriptor> sources = new ArrayList<>();
		sources.add(new FolderDataSourceDescriptor(path));
		WarsmashGdxMapScreen.addLanguageFolder(sources, path, language);
		return new CompoundDataSourceDescriptor(sources).createDataSource();
	}

	private static String text(final DataSource source, final String path) throws IOException {
		final ByteBuffer buffer = source.read(path);
		final byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	@Test
	void theLayerOfAFlatFolderWinsAndTheFolderStaysTheFallback() throws IOException {
		write("data/ui/strings.txt", "base");
		write("data/units/only-base.txt", "base only");
		write("data/_locales/eses.w3mod/ui/strings.txt", "spanish");
		write("data/_locales/eses.w3mod/sound/line.flac", "spanish speech");
		final String path = new File(this.temp, "data").getPath();
		final DataSource spanish = source(path, "esES");
		assertEquals("spanish", text(spanish, "ui/strings.txt"));
		assertEquals("spanish speech", text(spanish, "sound/line.flac"));
		assertEquals("base only", text(spanish, "units/only-base.txt"));
		assertEquals("base", text(source(path, "enUS"), "ui/strings.txt"));
		assertEquals("base", text(source(path, ""), "ui/strings.txt"));
		assertFalse(source(path, "esES").has("ui/missing.txt"));
	}

	@Test
	void aLayerFolderPathStillUsesItsSibling() throws IOException {
		write("data/_locales/eses.w3mod/ui/strings.txt", "spanish");
		write("data/_locales/frfr.w3mod/ui/strings.txt", "french");
		final String layer = new File(this.temp, "data/_locales/eses.w3mod").getPath();
		final List<DataSourceDescriptor> sources = new ArrayList<>();
		WarsmashGdxMapScreen.addLanguageFolder(sources, layer, "frFR");
		assertEquals(1, sources.size());
		assertTrue(sources.get(0).getDisplayName().endsWith("frfr.w3mod"));
	}
}
