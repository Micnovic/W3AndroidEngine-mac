package com.etheller.warsmash.parsers.fdf;

import java.util.Arrays;
import java.util.List;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.fdf.datamodel.FrameTemplateEnvironment;
import com.etheller.warsmash.util.Log;

/**
 * Reads the frame templates of TOC files on a thread of its own while a map is still loading, so that the game's
 * interface does not read them in the frame that opens the game, where they took a few hundred milliseconds of a
 * phone's time.
 */
public final class FrameTemplatePrefetch {
	private final List<String> tocFilePaths;
	private final Thread thread;
	private FrameTemplateEnvironment templates;

	private FrameTemplatePrefetch(final DataSource dataSource, final String[] tocFilePaths) {
		this.tocFilePaths = Arrays.asList(tocFilePaths.clone());
		this.thread = new Thread(() -> {
			final FrameTemplateEnvironment read = new FrameTemplateEnvironment();
			try {
				for (final String tocFilePath : this.tocFilePaths) {
					GameUI.readTOCFile(dataSource, read, tocFilePath);
				}
				this.templates = read;
			}
			catch (final Exception e) {
				// The interface reads the files itself and fails there as it would have anyway.
				Log.info("Frame templates not read ahead: " + e);
			}
		}, "frame-templates");
		this.thread.setDaemon(true);
	}

	public static FrameTemplatePrefetch start(final DataSource dataSource, final String... tocFilePaths) {
		final FrameTemplatePrefetch prefetch = new FrameTemplatePrefetch(dataSource, tocFilePaths);
		prefetch.thread.start();
		return prefetch;
	}

	public List<String> getTocFilePaths() {
		return this.tocFilePaths;
	}

	/** The templates once they are read, or null when reading them failed. */
	public FrameTemplateEnvironment await() {
		try {
			this.thread.join();
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
		return this.templates;
	}
}
