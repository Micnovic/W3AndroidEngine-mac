package com.etheller.warsmash.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import com.etheller.warsmash.datasources.DataSource;

public class WorldEditStrings implements StringBundle {
	private ResourceBundle bundle;
	private ResourceBundle bundlegs;

	public WorldEditStrings(final DataSource dataSource) {
		if (dataSource.has("UI\\WorldEditStrings.txt")) {
			this.bundle = loadBundle(dataSource, "UI\\WorldEditStrings.txt");
		}
		if (!dataSource.has("UI\\WorldEditGameStrings.txt")) {
			throw new IllegalStateException("Game data not found: UI\\WorldEditGameStrings.txt is missing");
		}
		this.bundlegs = loadBundle(dataSource, "UI\\WorldEditGameStrings.txt");
	}

	// The menu, the map viewer and the object data each read these files: about ten thousand strings per copy.
	// Bundles are only read, so equal files share one, for as long as the memory is not needed.
	private static final Map<String, SoftReference<Loaded>> LOADED = new HashMap<>();

	private static final class Loaded {
		private final byte[] content;
		private final ResourceBundle bundle;

		private Loaded(final byte[] content, final ResourceBundle bundle) {
			this.content = content;
			this.bundle = bundle;
		}
	}

	private static synchronized ResourceBundle loadBundle(final DataSource dataSource, final String path) {
		try (InputStream fis = dataSource.getResourceAsStream(path)) {
			final byte[] content = StreamBytes.readAll(fis);
			final SoftReference<Loaded> reference = LOADED.get(path);
			final Loaded known = reference == null ? null : reference.get();
			if ((known != null) && Arrays.equals(known.content, content)) {
				return known.bundle;
			}
			final ResourceBundle bundle = new PropertyResourceBundle(
					new InputStreamReader(new ByteArrayInputStream(content), "utf-8"));
			LOADED.put(path, new SoftReference<>(new Loaded(content, bundle)));
			return bundle;
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getString(String string) {
		try {
			while (string.toUpperCase(Locale.ROOT).startsWith("WESTRING")) {
				string = internalGetString(string);
			}
			return string;
		}
		catch (final MissingResourceException exc) {
			try {
				return this.bundlegs.getString(string.toUpperCase(Locale.ROOT));
			}
			catch (final MissingResourceException exc2) {
				return string;
			}
		}
	}

	private String internalGetString(final String key) {
		if (this.bundle == null) {
			return this.bundlegs.getString(key.toUpperCase(Locale.ROOT));
		}
		try {
			String string = this.bundle.getString(key.toUpperCase(Locale.ROOT));
			if ((string.charAt(0) == '"') && (string.length() >= 2) && (string.charAt(string.length() - 1) == '"')) {
				string = string.substring(1, string.length() - 1);
			}
			return string;
		}
		catch (final MissingResourceException exc) {
			return this.bundlegs.getString(key.toUpperCase(Locale.ROOT));
		}
	}

	@Override
	public String getStringCaseSensitive(final String key) {
		if (this.bundle == null) {
			return this.bundlegs.getString(key);
		}
		try {
			return this.bundle.getString(key);
		}
		catch (final MissingResourceException exc) {
			return this.bundlegs.getString(key);
		}
	}
}
