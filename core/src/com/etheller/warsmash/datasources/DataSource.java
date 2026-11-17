package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public interface DataSource {
	/**
	 * Efficiently return a stream instance that will read the data source file's
	 * contents directly from the data source. For example, this will read a file
	 * within an MPQ or CASC storage without extracting it.
	 *
	 * @param filepath
	 * @return
	 * @throws IOException
	 */
	InputStream getResourceAsStream(String filepath) throws IOException;

	/**
	 * Inefficiently copy a file from the data source onto the Hard Drive of the
	 * computer, and then return a java File instance pointed at the file.
	 *
	 * @param filepath
	 * @return
	 * @throws IOException
	 */
	File getFile(String filepath) throws IOException;

	/**
	 * Returns a directory from a FolderDataSource, otherwise returns null
	 *
	 * @param filepath
	 * @return
	 * @throws IOException
	 */
	File getDirectory(String filepath) throws IOException;

	ByteBuffer read(String path) throws IOException;

	/**
	 * Returns true if the data source contains a valid entry for a particular file.
	 * Some data sources (MPQs) may contain files for which this returns true, even
	 * though they cannot list the file in their Listfile.
	 *
	 * @param filepath
	 * @return
	 */
	boolean has(String filepath);

	/**
	 * Returns the first of the given paths that exists, searching layer by layer so
	 * that a higher priority layer wins over the order of the paths.
	 *
	 * @param filepaths
	 * @return the matching path, or null if none exists
	 */
	default String findFirst(final String... filepaths) {
		for (final String filepath : filepaths) {
			if (has(filepath)) {
				return filepath;
			}
		}
		return null;
	}

	/**
	 * @return a list of data source contents, or null if no list is provided
	 */
	Collection<String> getListfile();

	/**
	 * The listed paths that the filter accepts. A data source made of several others answers this without merging
	 * their whole lists, which for the game data are some hundred thousand paths.
	 *
	 * @return the accepted paths, none twice
	 */
	default Collection<String> getListfile(final Predicate<String> accept) {
		final Collection<String> listfile = getListfile();
		if (listfile == null) {
			return Collections.emptyList();
		}
		final List<String> accepted = new ArrayList<>();
		for (final String path : listfile) {
			if (accept.test(path)) {
				accepted.add(path);
			}
		}
		return accepted;
	}

	/**
	 * This data as the Classic game reads it, without the graphics packages laid over it. What the simulation reads
	 * comes from here, so that players using other graphics sets (and a replay) simulate alike.
	 *
	 * @return this when no graphics package is part of it
	 */
	default DataSource graphicsIndependent() {
		return this;
	}

	void close() throws IOException;
}
