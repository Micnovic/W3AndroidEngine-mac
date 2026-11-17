package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;

/**
 * What the simulation reads comes from the data without the graphics packages, so every graphics set plays by the
 * same water level, sound lengths and tables.
 */
class GraphicsIndependentDataTest {
	private static final String FOOTMAN = "units\\human\\footman\\footman.mdx";
	private static final String WATER = "terrainart\\water.slk";
	private static final String SOUND = "sound\\interface\\hello.wav";

	@Test
	void plainDataIsItsOwnView() {
		final DataSource data = installed();
		assertSame(data, data.graphicsIndependent());
		assertSame(data, LayeredDataSource.wrap(data, GraphicsSet.CLASSIC, "enus").graphicsIndependent());
	}

	@Test
	void layeredDataReadsTheClassicFiles() throws Exception {
		final DataSource base = installed();
		final DataSource layered = LayeredDataSource.wrap(base, GraphicsSet.REFORGED, "enus");
		assertEquals("hd water without uSha", text(layered, WATER));
		assertSame(base, layered.graphicsIndependent());
		assertEquals("classic water with uSha", text(layered.graphicsIndependent(), WATER));
	}

	@Test
	void aMapStillWinsOverTheClassicFiles() throws Exception {
		final DataSource layered = LayeredDataSource.wrap(installed(), GraphicsSet.REFORGED, "enus");
		final Map<String, byte[]> mapFiles = new HashMap<>();
		mapFiles.put("war3map.w3e", bytes("map"));
		mapFiles.put(WATER, bytes("map water"));
		final CompoundDataSource compound = new CompoundDataSource(Arrays.asList(layered, new Files(mapFiles)));
		assertEquals("map water", text(compound, WATER));
		final DataSource view = compound.graphicsIndependent();
		assertEquals("map water", text(view, WATER));
		assertEquals("map", text(view, "war3map.w3e"));
		assertEquals("map water", text(view, "TerrainArt\\Water.slk"));
		assertSame(view, compound.graphicsIndependent());
	}

	@Test
	void aSourceAddedLaterIsInTheView() throws Exception {
		final CompoundDataSource compound = new CompoundDataSource(Arrays
				.asList(LayeredDataSource.wrap(installed(), GraphicsSet.REFORGED, "enus")));
		final DataSource before = compound.graphicsIndependent();
		final Map<String, byte[]> mapFiles = new HashMap<>();
		mapFiles.put(WATER, bytes("late water"));
		compound.addSource(new Files(mapFiles));
		assertEquals("classic water with uSha", text(before, WATER));
		assertEquals("late water", text(compound.graphicsIndependent(), WATER));
	}

	@Test
	void aSubdirectoryViewReadsTheClassicFiles() throws Exception {
		final DataSource layered = LayeredDataSource.wrap(installed(), GraphicsSet.REFORGED, "enus");
		final DataSource sub = new SubdirDataSource(layered, "terrainart\\");
		assertEquals("hd water without uSha", text(sub, "water.slk"));
		assertEquals("classic water with uSha", text(sub.graphicsIndependent(), "water.slk"));
	}

	@Test
	void aSoundsLengthIsTheClassicFilesLength() {
		final DataSource layered = LayeredDataSource.wrap(installed(), GraphicsSet.REFORGED, "enus");
		// 22050 Hz mono 16 bit: the Classic file is one second, the package's two.
		assertEquals(1f, UnitSound.fileDuration(layered, "Sound\\Interface\\Hello.wav"), 0.001f);
		assertEquals(1f, UnitSound.fileDuration(installed(), "Sound\\Interface\\Hello.wav"), 0.001f);
	}

	private static DataSource installed() {
		final Map<String, byte[]> files = new HashMap<>();
		files.put(FOOTMAN, bytes("classic"));
		files.put("_hd.w3mod\\" + FOOTMAN, bytes("hd"));
		files.put("_de.w3mod\\" + FOOTMAN, bytes("de"));
		files.put(WATER, bytes("classic water with uSha"));
		files.put("_hd.w3mod\\" + WATER, bytes("hd water without uSha"));
		files.put(SOUND, wav(22050 * 2));
		files.put("_hd.w3mod\\" + SOUND, wav(22050 * 4));
		return new Files(files);
	}

	private static byte[] bytes(final String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	/** A 22050 Hz mono 16 bit PCM file with this many bytes of samples. */
	private static byte[] wav(final int dataSize) {
		final ByteBuffer out = ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		out.put(bytes("RIFF")).putInt(36 + dataSize).put(bytes("WAVE")).put(bytes("fmt ")).putInt(16);
		out.putShort((short) 1).putShort((short) 1).putInt(22050).putInt(22050 * 2).putShort((short) 2)
				.putShort((short) 16);
		out.put(bytes("data")).putInt(dataSize);
		return out.array();
	}

	private static String text(final DataSource data, final String path) throws Exception {
		try (InputStream in = data.getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static final class Files implements DataSource {
		private final Map<String, byte[]> files = new HashMap<>();

		Files(final Map<String, byte[]> files) {
			for (final Map.Entry<String, byte[]> file : files.entrySet()) {
				this.files.put(file.getKey().toLowerCase(Locale.US), file.getValue());
			}
		}

		private byte[] find(final String path) {
			return path == null ? null : this.files.get(path.replace('/', '\\').toLowerCase(Locale.US));
		}

		@Override
		public InputStream getResourceAsStream(final String filepath) {
			final byte[] content = find(filepath);
			return content == null ? null : new ByteArrayInputStream(content);
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
			final byte[] content = find(path);
			return content == null ? null : ByteBuffer.wrap(content);
		}

		@Override
		public boolean has(final String filepath) {
			return find(filepath) != null;
		}

		@Override
		public Collection<String> getListfile() {
			return this.files.keySet();
		}

		@Override
		public void close() {
		}
	}
}
