package com.etheller.warsmash.parsers.fdf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.DataSourceFileHandle;
import com.etheller.warsmash.util.GraphicsContext;

public class DynamicFontGeneratorHolder {
	private final DataSource dataSource;
	private final Element skin;
	private final Map<String, FontGeneratorHolder> fontNameToGenerator;
	private final List<String> poolKeys = new ArrayList<>();

	/** The generators in use by any interface of this context, with how many interfaces use each. */
	private static final Map<String, Shared> POOL = new HashMap<>();

	private static final class Shared {
		private final FontGeneratorHolder holder;
		private int users;

		private Shared(final FontGeneratorHolder holder) {
			this.holder = holder;
		}
	}

	public DynamicFontGeneratorHolder(final DataSource dataSource, final Element skin) {
		this.dataSource = dataSource;
		this.skin = skin;
		this.fontNameToGenerator = new HashMap<>();
	}

	public FontGeneratorHolder getFontGenerator(final String font) {
		final String fontName = this.skin.getField(font);
		if (fontName == null) {
			throw new IllegalStateException("No such font: " + font);
		}
		final FontGeneratorHolder fontGenerator = getFontGeneratorForFile(fontName);
		if (fontGenerator == null) {
			throw new IllegalStateException("No such font file: " + fontName + " (for \"" + font + "\")");
		}
		return fontGenerator;
	}

	/**
	 * The generator of a font file of the data (a map script's font), or null when there is no such file. The skin's
	 * fonts are mostly one file under several names, and the menu and the game's interface use the same sizes of it,
	 * so a file with the same bytes gets one generator, and its fonts one set of pages, while any of them is alive.
	 */
	public FontGeneratorHolder getFontGeneratorForFile(final String fileName) {
		final String key = "file:" + fileName.replace('\\', '/').toLowerCase(Locale.ROOT);
		FontGeneratorHolder fontGenerator = this.fontNameToGenerator.get(key);
		if ((fontGenerator == null) && this.dataSource.has(fileName)) {
			final byte[] bytes = new DataSourceFileHandle(this.dataSource, fileName).readBytes();
			final CRC32 crc = new CRC32();
			crc.update(bytes, 0, bytes.length);
			final String poolKey = GraphicsContext.generation() + "|" + key + "|" + bytes.length + "|" + crc.getValue();
			synchronized (POOL) {
				Shared shared = POOL.get(poolKey);
				if (shared == null) {
					shared = new Shared(new FontGeneratorHolder(fileName,
							new UnroundedAdvanceFontGenerator(new DataSourceFileHandle(this.dataSource, fileName))));
					POOL.put(poolKey, shared);
				}
				shared.users++;
				this.poolKeys.add(poolKey);
				fontGenerator = shared.holder;
			}
			this.fontNameToGenerator.put(key, fontGenerator);
		}
		return fontGenerator;
	}

	public void dispose() {
		synchronized (POOL) {
			for (final String poolKey : this.poolKeys) {
				final Shared shared = POOL.get(poolKey);
				if ((shared != null) && (--shared.users <= 0)) {
					POOL.remove(poolKey);
					shared.holder.dispose();
				}
			}
		}
		this.poolKeys.clear();
		this.fontNameToGenerator.clear();
	}
}
