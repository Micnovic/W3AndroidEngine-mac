package com.etheller.warsmash.datasources;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The positions of a list of paths, found by the path in any letter case and without the trailing separators the
 * listing ignores. It holds one table of positions and no copy of the paths: the entries are compared against the list
 * itself, which a game's data keeps anyway. A table of lower case copies took several times the memory of the list.
 */
final class PathIndex {
	private final List<String> paths;
	private final int[] table;
	private final int mask;
	/** The lower case form of the few paths that have characters other than ASCII; the others fold their letters. */
	private final Map<Integer, String> exotic = new HashMap<>();

	PathIndex(final List<String> paths) {
		this.paths = paths;
		int capacity = 16;
		while (capacity < (paths.size() * 2)) {
			capacity <<= 1;
		}
		this.table = new int[capacity];
		this.mask = capacity - 1;
		for (int i = 0; i < paths.size(); i++) {
			final String path = paths.get(i);
			final boolean plain = isAscii(path);
			final String form = plain ? path : path.toLowerCase(Locale.ROOT);
			if (!plain) {
				this.exotic.put(i, form);
			}
			int slot = mix(hash(null, form, form.length(), plain ? LETTERS : EXACT)) & this.mask;
			while (this.table[slot] != 0) {
				if (matches(this.table[slot] - 1, null, form, form.length(), plain ? LETTERS : EXACT)) {
					break;
				}
				slot = (slot + 1) & this.mask;
			}
			if (this.table[slot] == 0) {
				this.table[slot] = i + 1;
			}
		}
	}

	int size() {
		return this.paths.size();
	}

	/** @return the position of the first listed path that is this path, or -1 */
	int indexOf(final String path) {
		return indexOf(null, path);
	}

	/**
	 * @return the position of the listed path that is prefix + "\\" + path (just path for a null prefix), or -1. The
	 *         path may be as asked, not made lower case with backslashes: '/' and ':' in it count as backslashes.
	 */
	int indexOf(final String prefix, String path) {
		if (!isAscii(path)) {
			path = path.toLowerCase(Locale.US).replace('/', '\\').replace(':', '\\');
		}
		int end = ((prefix == null) ? 0 : prefix.length() + 1) + path.length();
		while ((end > 0) && isSeparator(charAt(prefix, path, end - 1), end - 1, prefix)) {
			end--;
		}
		String first = prefix;
		String second = path;
		int mode = QUERY;
		if (!isAscii(path) || ((prefix != null) && !isAscii(prefix))) {
			first = null;
			second = ((prefix == null) ? path : prefix + "\\" + path).substring(0, end).toLowerCase(Locale.ROOT);
			end = second.length();
			mode = EXACT;
		}
		int slot = mix(hash(first, second, end, mode)) & this.mask;
		while (true) {
			final int entry = this.table[slot];
			if (entry == 0) {
				return -1;
			}
			if (matches(entry - 1, first, second, end, mode)) {
				return entry - 1;
			}
			slot = (slot + 1) & this.mask;
		}
	}

	String get(final int index) {
		return this.paths.get(index);
	}

	private static char charAt(final String prefix, final String path, final int index) {
		if (prefix == null) {
			return path.charAt(index);
		}
		if (index < prefix.length()) {
			return prefix.charAt(index);
		}
		if (index == prefix.length()) {
			return '\\';
		}
		return path.charAt(index - prefix.length() - 1);
	}

	private static final int EXACT = 0;
	private static final int LETTERS = 1;
	/** Letters folded, and '/' and ':' as backslashes, except in the prefix, which the storage names as it is. */
	private static final int QUERY = 2;

	private static boolean isSeparator(final char c, final int index, final String prefix) {
		return (c == '\\') || (((prefix == null) || (index > prefix.length())) && ((c == '/') || (c == ':')));
	}

	private static char fold(final String prefix, final String path, final int k, final int mode) {
		final char c = charAt(prefix, path, k);
		if (mode == EXACT) {
			return c;
		}
		if ((mode == QUERY) && ((c == '/') || (c == ':')) && ((prefix == null) || (k > prefix.length()))) {
			return '\\';
		}
		return fold(c);
	}

	private static int hash(final String prefix, final String path, final int length, final int mode) {
		int hash = 0;
		for (int k = 0; k < length; k++) {
			hash = (31 * hash) + fold(prefix, path, k, mode);
		}
		return hash;
	}

	/** Whether the entry's lower case form is the first length characters of prefix + "\\" + path. */
	private boolean matches(final int entry, final String prefix, final String path, final int length,
			final int queryMode) {
		final String lowered = this.exotic.isEmpty() ? null : this.exotic.get(entry);
		final String listed = lowered != null ? lowered : this.paths.get(entry);
		if (listed.length() != length) {
			return false;
		}
		final boolean foldEntry = lowered == null;
		for (int k = 0; k < length; k++) {
			final char a = foldEntry ? fold(listed.charAt(k)) : listed.charAt(k);
			final char b = fold(prefix, path, k, queryMode);
			if (a != b) {
				return false;
			}
		}
		return true;
	}

	private static char fold(final char c) {
		return ((c >= 'A') && (c <= 'Z')) ? (char) (c + ('a' - 'A')) : c;
	}

	private static boolean isAscii(final String text) {
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) >= 128) {
				return false;
			}
		}
		return true;
	}

	private static int mix(final int hash) {
		final int h = hash * 0x9E3779B9;
		return h ^ (h >>> 15);
	}
}
