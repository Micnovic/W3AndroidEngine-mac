package com.etheller.warsmash.parsers.facefx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.etheller.warsmash.datasources.DataSource;

/**
 * The FaceFX data of a game's files: the face of a character next to its portrait model and the animations of the
 * spoken lines. Nothing is read before a line plays, and data without FaceFX (Classic portraits, a data source without
 * the Reforged files) answers null everywhere.
 */
public final class FaceFxLibrary {
	private static final String ACTOR_EXTENSION = ".facefx_ingame";
	private static final String PORTRAIT_SUFFIX = "_portrait";

	private final DataSource data;
	private final Map<String, FaceFxActor> actors = new HashMap<>();
	private FaceFxAnimIndex index;

	public FaceFxLibrary(final DataSource data) {
		this.data = data;
	}

	/**
	 * {@code Units\Human\Footman\Footman_Portrait.mdx} has its face in {@code Units\Human\Footman\Footman.facefx_ingame};
	 * null for a model path that cannot have one.
	 */
	public static String actorPathForModel(final String modelPath) {
		if (modelPath == null) {
			return null;
		}
		final String path = modelPath.replace('/', '\\');
		final int slash = path.lastIndexOf('\\');
		String name = path.substring(slash + 1);
		final int dot = name.lastIndexOf('.');
		if (dot <= 0) {
			return null;
		}
		name = name.substring(0, dot);
		if (name.toLowerCase(Locale.ROOT).endsWith(PORTRAIT_SUFFIX) && (name.length() > PORTRAIT_SUFFIX.length())) {
			name = name.substring(0, name.length() - PORTRAIT_SUFFIX.length());
		}
		return path.substring(0, slash + 1) + name + ACTOR_EXTENSION;
	}

	/** @return the speaker name an actor file has: its name without folders and extension, lower case */
	public static String speakerOfActor(final String actorPath) {
		return FaceFxAnimIndex.speakerOfFile(actorPath.replace('/', '\\'));
	}

	/** @return the face of the character whose portrait model is at this path, null when it has none */
	public synchronized FaceFxActor actorForModel(final String modelPath) {
		final String path = actorPathForModel(modelPath);
		if (path == null) {
			return null;
		}
		final String key = path.toLowerCase(Locale.ROOT);
		if (this.actors.containsKey(key)) {
			return this.actors.get(key);
		}
		FaceFxActor actor = null;
		try {
			final byte[] bytes = read(path);
			if (bytes != null) {
				actor = FaceFxActor.read(bytes);
			}
		}
		catch (final IOException | RuntimeException e) {
			actor = null;
		}
		this.actors.put(key, actor);
		return actor;
	}

	/**
	 * @param modelPath the portrait model, whose file name is the speaker's
	 * @param soundPath the sound file of the line
	 * @return the animation of the line, null when there is none
	 */
	public FaceFxAnimSet.Anim animFor(final String modelPath, final String soundPath) {
		final String actorPath = actorPathForModel(modelPath);
		return index().find((actorPath == null) ? null : speakerOfActor(actorPath), soundPath);
	}

	/** Lists the animations now, which took a frame of a tenth of a second and more at the first line. */
	public void prepare() {
		index();
	}

	private synchronized FaceFxAnimIndex index() {
		if (this.index == null) {
			this.index = new FaceFxAnimIndex(
					this.data.getListfileByExtension(FaceFxAnimIndex.ANIMSET_EXTENSION),
					this::read);
		}
		return this.index;
	}

	private byte[] read(final String path) throws IOException {
		if (!this.data.has(path)) {
			return null;
		}
		final ByteBuffer buffer = this.data.read(path);
		if (buffer == null) {
			return null;
		}
		final byte[] bytes = new byte[buffer.remaining()];
		buffer.duplicate().get(bytes);
		return bytes;
	}
}
