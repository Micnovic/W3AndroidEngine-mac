package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.files.FileHandle;

/** The checkpoint saves a script made, oldest first, and how many of them the game keeps (SetMaxCheckpointSaves). */
public final class CheckpointSaves {
	private int max = -1;
	private final List<FileHandle> saves = new ArrayList<>();

	/** The saves that no longer fit; the newest one always stays, as it may still be being written. */
	public List<FileHandle> setMax(final int max) {
		this.max = max;
		return trim();
	}

	public int getMax() {
		return this.max;
	}

	/** Notes a checkpoint save and returns the older ones the limit pushes out. */
	public List<FileHandle> record(final FileHandle file) {
		for (int i = 0; i < this.saves.size(); i++) {
			if (this.saves.get(i).path().equals(file.path())) {
				this.saves.remove(i);
				break;
			}
		}
		this.saves.add(file);
		return trim();
	}

	/** Deletes saves that were pushed out, once the ones still being written are on disk. */
	public static void delete(final List<FileHandle> files) {
		if (files.isEmpty()) {
			return;
		}
		CSaveStorage.finishBackgroundWrites();
		for (final FileHandle file : files) {
			if (file.exists()) {
				file.delete();
			}
		}
	}

	private List<FileHandle> trim() {
		final List<FileHandle> excess = new ArrayList<>();
		if (this.max < 0) {
			return excess;
		}
		while ((this.saves.size() > this.max) && (this.saves.size() > 1)) {
			excess.add(this.saves.remove(0));
		}
		return excess;
	}
}
