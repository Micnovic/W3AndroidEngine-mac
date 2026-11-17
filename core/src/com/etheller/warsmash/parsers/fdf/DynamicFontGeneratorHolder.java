package com.etheller.warsmash.parsers.fdf;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.DataSourceFileHandle;

public class DynamicFontGeneratorHolder {
	private final DataSource dataSource;
	private final Element skin;
	private final Map<String, FontGeneratorHolder> fontNameToGenerator;

	public DynamicFontGeneratorHolder(final DataSource dataSource, final Element skin) {
		this.dataSource = dataSource;
		this.skin = skin;
		this.fontNameToGenerator = new HashMap<>();
	}

	public FontGeneratorHolder getFontGenerator(final String font) {
		FontGeneratorHolder fontGenerator = this.fontNameToGenerator.get(font);
		if (fontGenerator == null) {
			final String fontName = this.skin.getField(font);
			if (fontName == null) {
				throw new IllegalStateException("No such font: " + font);
			}
			if (!this.dataSource.has(fontName)) {
				throw new IllegalStateException("No such font file: " + fontName + " (for \"" + font + "\")");
			}
			fontGenerator = new FontGeneratorHolder(
					new UnroundedAdvanceFontGenerator(new DataSourceFileHandle(this.dataSource, fontName)));
			this.fontNameToGenerator.put(font, fontGenerator);
		}
		return fontGenerator;
	}

	/** The generator of a font file of the data (a map script's font), or null when there is no such file. */
	public FontGeneratorHolder getFontGeneratorForFile(final String fileName) {
		final String key = "file:" + fileName.toLowerCase(Locale.ROOT);
		FontGeneratorHolder fontGenerator = this.fontNameToGenerator.get(key);
		if ((fontGenerator == null) && this.dataSource.has(fileName)) {
			fontGenerator = new FontGeneratorHolder(
					new UnroundedAdvanceFontGenerator(new DataSourceFileHandle(this.dataSource, fileName)));
			this.fontNameToGenerator.put(key, fontGenerator);
		}
		return fontGenerator;
	}

	public void dispose() {
		for (final FontGeneratorHolder generator : this.fontNameToGenerator.values()) {
			generator.dispose();
		}
		this.fontNameToGenerator.clear();
	}
}
