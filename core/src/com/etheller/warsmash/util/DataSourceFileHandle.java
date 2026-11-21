package com.etheller.warsmash.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.datasources.AssetPathFallback;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.IndependentStream;

public class DataSourceFileHandle extends FileHandle {
	private static final String[] SOUND_EXTENSIONS = { ".flac", ".wav", ".ogg", ".mp3" };

	private final DataSource dataSource;

	public DataSourceFileHandle(final DataSource dataSource, final String path) {
		this(resolveSoundPath(dataSource, path), dataSource);
	}

	private DataSourceFileHandle(final String resolvedPath, final DataSource dataSource) {
		super(resolvedPath);
		this.dataSource = dataSource;
	}

	/** A handle of a path that {@link #resolveSoundPath} already gave, which is not looked up again. */
	public static DataSourceFileHandle ofResolved(final DataSource dataSource, final String resolvedPath) {
		return new DataSourceFileHandle(resolvedPath, dataSource);
	}

	@Override
	public String path() {
		return file().getPath();
	}

	@Override
	public InputStream read() {
		try {
			return this.dataSource.getResourceAsStream(path());
		}
		catch (final IOException e) {
			throw new RuntimeException("Failed to load FileHandle from DataSource: " + path());
		}
	}

	/**
	 * The file as {@link DataSource#opener} gives it, looked up now: it may be opened and read on another thread, and
	 * is null where the data source reads only on the thread that uses it.
	 */
	public IndependentStream.Opener opener() {
		return this.dataSource.opener(path());
	}

	/**
	 * Sound tables name one extension, but a game build may ship the sound in another
	 * format (3.0 tables say .flac while its Classic data has .ogg). The layer wins
	 * over the extension, so a Classic .ogg is preferred to a .flac of a later layer.
	 */
	public static String resolveSoundPath(final DataSource dataSource, final String path) {
		if (!isSoundPath(path)) {
			return path;
		}
		final int dotIndex = path.lastIndexOf('.');
		final String extension = path.substring(dotIndex).toLowerCase(Locale.US);
		final String basePath = path.substring(0, dotIndex);
		final String[] candidatePaths = new String[SOUND_EXTENSIONS.length];
		candidatePaths[0] = path;
		int candidateCount = 1;
		for (final String candidate : SOUND_EXTENSIONS) {
			if (!candidate.equals(extension)) {
				candidatePaths[candidateCount++] = basePath + candidate;
			}
		}
		final String found = dataSource.findFirst(candidatePaths);
		if (found != null) {
			return found;
		}
		for (int i = 0; i < candidateCount; i++) {
			final String fallback = AssetPathFallback.find(dataSource, candidatePaths[i]);
			if (fallback != null) {
				return fallback;
			}
		}
		return path;
	}

	public static boolean isSoundPath(final String path) {
		final int dotIndex = path.lastIndexOf('.');
		if ((dotIndex == -1) || (dotIndex < Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')))) {
			return false;
		}
		final String extension = path.substring(dotIndex).toLowerCase(Locale.US);
		for (final String candidate : SOUND_EXTENSIONS) {
			if (candidate.equals(extension)) {
				return true;
			}
		}
		return false;
	}
}
