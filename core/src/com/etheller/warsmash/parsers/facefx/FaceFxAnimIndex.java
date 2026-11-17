package com.etheller.warsmash.parsers.facefx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the animation of a spoken line. A line's animation is named like its sound file and lives in the animset of
 * the speaker, which sits in a folder of a campaign chapter that the line's name does not give away (the footman line
 * {@code H03Footman47} is in {@code human01}), so the animsets are listed once by speaker and tried one after the
 * other, those of the chapter the sound's folder names first. An animset is read when it is first tried and a few are
 * kept.
 */
public final class FaceFxAnimIndex {
	public interface Loader {
		/** @return the bytes of the file at the logical path, null when it cannot be read */
		byte[] load(String path) throws IOException;
	}

	public static final String ANIMSET_EXTENSION = ".animset_ingame";
	private static final String PACKAGE_END = ".w3mod\\";
	private static final String ANIMATION_FOLDER = "facialanimation\\";
	private static final int KEPT_ANIMSETS = 12;
	/** The campaign code in front of a line's speaker ({@code H03}, {@code N07}) and the number behind it. */
	private static final Pattern LINE_SPEAKER = Pattern.compile("^[A-Za-z]\\d+_?([A-Za-z][A-Za-z_]*?)_?\\d+[A-Za-z]?$");
	private static final Object NONE = new Object();

	private final Loader loader;
	/** The logical paths of the animsets by the speaker's lower case name, which is the file's name. */
	private final Map<String, List<String>> bySpeaker = new HashMap<>();
	private final Map<String, FaceFxAnimSet> animsets = new LinkedHashMap<String, FaceFxAnimSet>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(final Map.Entry<String, FaceFxAnimSet> eldest) {
			return size() > KEPT_ANIMSETS;
		}
	};
	/** Paths whose file was missing or not an animset, so they are not read again. */
	private final Set<String> unusable = new HashSet<>();
	private final Map<String, Object> answers = new HashMap<>();

	/**
	 * @param listed the paths of the data source, of which those of animsets are taken
	 */
	public FaceFxAnimIndex(final Collection<String> listed, final Loader loader) {
		this.loader = loader;
		final Set<String> seen = new HashSet<>();
		if (listed != null) {
			for (final String path : listed) {
				if (!path.toLowerCase(Locale.ROOT).endsWith(ANIMSET_EXTENSION)) {
					continue;
				}
				final String logical = logicalPath(path);
				if (!seen.add(logical.toLowerCase(Locale.ROOT))) {
					continue;
				}
				final String speaker = speakerOfFile(logical);
				List<String> paths = this.bySpeaker.get(speaker);
				if (paths == null) {
					paths = new ArrayList<>();
					this.bySpeaker.put(speaker, paths);
				}
				paths.add(logical);
			}
		}
	}

	/**
	 * A data source lists a path with the packages it lies in in front
	 * ({@code war3.w3mod\_locales\enus.w3mod\sound\...}); the data source reads it by what follows the last of them.
	 */
	public static String logicalPath(final String listed) {
		final String path = listed.replace('/', '\\');
		final int end = path.toLowerCase(Locale.ROOT).lastIndexOf(PACKAGE_END);
		return (end < 0) ? path : path.substring(end + PACKAGE_END.length());
	}

	/** @return the lower case name of the file without folders and extension: the speaker */
	static String speakerOfFile(final String path) {
		final String name = path.substring(path.lastIndexOf('\\') + 1);
		final int dot = name.lastIndexOf('.');
		return (dot > 0 ? name.substring(0, dot) : name).toLowerCase(Locale.ROOT);
	}

	/**
	 * The speaker a line's name gives ({@code H01Arthas08} is arthas, {@code H03Footman47} footman), lower case; null
	 * when the name does not have that shape.
	 */
	public static String speakerOfLine(final String animName) {
		if (animName == null) {
			return null;
		}
		final Matcher matcher = LINE_SPEAKER.matcher(animName);
		return matcher.matches() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
	}

	/** @return the folder of the chapter an animset lies in ({@code human01}), lower case; null for one outside them */
	static String chapterOf(final String logicalPath) {
		final String lower = logicalPath.toLowerCase(Locale.ROOT);
		final int at = lower.indexOf(ANIMATION_FOLDER);
		if (at <= 0) {
			return null;
		}
		final int end = at - 1;
		final int start = lower.lastIndexOf('\\', end - 1) + 1;
		return (start < end) ? lower.substring(start, end) : null;
	}

	/**
	 * The animsets of a speaker in the order to try them: those in a chapter folder that the sound's path names first.
	 */
	List<String> candidates(final String speaker, final String soundPath) {
		final List<String> paths = (speaker == null) ? null : this.bySpeaker.get(speaker.toLowerCase(Locale.ROOT));
		if (paths == null) {
			return Collections.emptyList();
		}
		final String sound = (soundPath == null) ? "" : soundPath.replace('/', '\\').toLowerCase(Locale.ROOT);
		final List<String> ordered = new ArrayList<>(paths.size());
		for (final String path : paths) {
			final String chapter = chapterOf(path);
			if ((chapter != null) && sound.contains("\\" + chapter + "\\")) {
				ordered.add(path);
			}
		}
		for (final String path : paths) {
			if (!ordered.contains(path)) {
				ordered.add(path);
			}
		}
		return ordered;
	}

	/**
	 * @param speaker   the speaker's name when the caller knows it (the character's file name), tried before the one
	 *                  the line's name gives; may be null
	 * @param soundPath the sound file of the line, with or without folders
	 * @return the animation of the line, null when no animset of the speaker has it
	 */
	public synchronized FaceFxAnimSet.Anim find(final String speaker, final String soundPath) {
		final String animName = FaceFxAnimSet.animNameForSound(soundPath);
		if ((animName == null) || animName.isEmpty()) {
			return null;
		}
		final String key = ((speaker == null) ? "" : speaker.toLowerCase(Locale.ROOT)) + '|'
				+ animName.toLowerCase(Locale.ROOT);
		final Object known = this.answers.get(key);
		if (known != null) {
			return (known == NONE) ? null : (FaceFxAnimSet.Anim) known;
		}
		FaceFxAnimSet.Anim found = search(speaker, animName, soundPath);
		if (found == null) {
			final String fromLine = speakerOfLine(animName);
			if ((fromLine != null) && !fromLine.equalsIgnoreCase(speaker)) {
				found = search(fromLine, animName, soundPath);
			}
		}
		this.answers.put(key, (found == null) ? NONE : found);
		return found;
	}

	private FaceFxAnimSet.Anim search(final String speaker, final String animName, final String soundPath) {
		for (final String path : candidates(speaker, soundPath)) {
			final FaceFxAnimSet set = animset(path);
			if (set != null) {
				final FaceFxAnimSet.Anim anim = set.find(animName);
				if (anim != null) {
					return anim;
				}
			}
		}
		return null;
	}

	private FaceFxAnimSet animset(final String path) {
		FaceFxAnimSet set = this.animsets.get(path);
		if ((set != null) || this.unusable.contains(path)) {
			return set;
		}
		try {
			final byte[] bytes = this.loader.load(path);
			set = (bytes == null) ? null : FaceFxAnimSet.read(bytes);
		}
		catch (final IOException | RuntimeException e) {
			set = null;
		}
		if (set == null) {
			this.unusable.add(path);
		}
		else {
			this.animsets.put(path, set);
		}
		return set;
	}
}
