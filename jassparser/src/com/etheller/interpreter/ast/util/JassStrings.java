package com.etheller.interpreter.ast.util;

/**
 * JASS strings are byte strings holding UTF-8: lengths and substring offsets count bytes, and a substring may cut a
 * multi-byte character in two. A Java String cannot hold a stray byte, so each byte that is not part of a complete
 * UTF-8 sequence is kept as the lone low surrogate U+DC00 + byte (the bytes 0x80..0xFF), and {@link #concat} turns
 * halves that meet back into the character. Strings without such surrogates are ordinary Java text.
 */
public final class JassStrings {
	private JassStrings() {
	}

	private static boolean isStrayByte(final char c) {
		return (c >= 0xDC80) && (c <= 0xDCFF);
	}

	private static boolean isAscii(final String s) {
		for (int i = 0, n = s.length(); i < n; i++) {
			if (s.charAt(i) >= 0x80) {
				return false;
			}
		}
		return true;
	}

	public static int length(final String s) {
		if (s == null) {
			return 0;
		}
		int bytes = 0;
		for (int i = 0, n = s.length(); i < n; i++) {
			final char c = s.charAt(i);
			if (c < 0x80) {
				bytes++;
			}
			else if (c < 0x800) {
				bytes += 2;
			}
			else if (Character.isHighSurrogate(c) && (i + 1 < n) && Character.isLowSurrogate(s.charAt(i + 1))) {
				bytes += 4;
				i++;
			}
			else if (isStrayByte(c) || Character.isSurrogate(c)) {
				bytes++;
			}
			else {
				bytes += 3;
			}
		}
		return bytes;
	}

	public static byte[] toBytes(final String s) {
		final byte[] out = new byte[length(s)];
		int o = 0;
		for (int i = 0, n = s.length(); i < n; i++) {
			final char c = s.charAt(i);
			if (c < 0x80) {
				out[o++] = (byte) c;
			}
			else if (c < 0x800) {
				out[o++] = (byte) (0xC0 | (c >> 6));
				out[o++] = (byte) (0x80 | (c & 0x3F));
			}
			else if (Character.isHighSurrogate(c) && (i + 1 < n) && Character.isLowSurrogate(s.charAt(i + 1))) {
				final int cp = Character.toCodePoint(c, s.charAt(++i));
				out[o++] = (byte) (0xF0 | (cp >> 18));
				out[o++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
				out[o++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
				out[o++] = (byte) (0x80 | (cp & 0x3F));
			}
			else if (isStrayByte(c)) {
				out[o++] = (byte) (c - 0xDC00);
			}
			else if (Character.isSurrogate(c)) {
				out[o++] = '?';
			}
			else {
				out[o++] = (byte) (0xE0 | (c >> 12));
				out[o++] = (byte) (0x80 | ((c >> 6) & 0x3F));
				out[o++] = (byte) (0x80 | (c & 0x3F));
			}
		}
		return out;
	}

	private static boolean cont(final byte[] b, final int i, final int end) {
		return (i < end) && ((b[i] & 0xC0) == 0x80);
	}

	public static String fromBytes(final byte[] b, final int start, final int end) {
		final StringBuilder sb = new StringBuilder(end - start);
		int i = start;
		while (i < end) {
			final int b0 = b[i] & 0xFF;
			int cp = -1;
			int len = 1;
			if (b0 < 0x80) {
				cp = b0;
			}
			else if ((b0 >= 0xC2) && (b0 <= 0xDF) && cont(b, i + 1, end)) {
				cp = ((b0 & 0x1F) << 6) | (b[i + 1] & 0x3F);
				len = 2;
			}
			else if ((b0 >= 0xE0) && (b0 <= 0xEF) && cont(b, i + 1, end) && cont(b, i + 2, end)) {
				final int v = ((b0 & 0x0F) << 12) | ((b[i + 1] & 0x3F) << 6) | (b[i + 2] & 0x3F);
				if ((v >= 0x800) && ((v < 0xD800) || (v > 0xDFFF))) {
					cp = v;
					len = 3;
				}
			}
			else if ((b0 >= 0xF0) && (b0 <= 0xF4) && cont(b, i + 1, end) && cont(b, i + 2, end)
					&& cont(b, i + 3, end)) {
				final int v = ((b0 & 0x07) << 18) | ((b[i + 1] & 0x3F) << 12) | ((b[i + 2] & 0x3F) << 6)
						| (b[i + 3] & 0x3F);
				if ((v >= 0x10000) && (v <= 0x10FFFF)) {
					cp = v;
					len = 4;
				}
			}
			if (cp < 0) {
				sb.append((char) (0xDC00 + b0));
			}
			else {
				sb.appendCodePoint(cp);
			}
			i += len;
		}
		return sb.toString();
	}

	public static String substring(final String s, int start, int end) {
		final int length = length(s);
		if (start > length) {
			start = length;
		}
		if (start < 0) {
			start = 0;
		}
		if (end > length) {
			end = length;
		}
		if (end < start) {
			end = start;
		}
		if (isAscii(s)) {
			return s.substring(start, end);
		}
		return fromBytes(toBytes(s), start, end);
	}

	public static String concat(final String left, final String right) {
		if (left.isEmpty() || right.isEmpty()) {
			return left + right;
		}
		if (isStrayByte(left.charAt(left.length() - 1)) && isStrayByte(right.charAt(0))) {
			final byte[] bytes = toBytes(left + right);
			return fromBytes(bytes, 0, bytes.length);
		}
		return left + right;
	}

	/** Only ASCII letters change case, like the original. */
	public static String changeCase(final String s, final boolean upper) {
		char[] chars = null;
		for (int i = 0, n = s.length(); i < n; i++) {
			final char c = s.charAt(i);
			final char r = upper ? (((c >= 'a') && (c <= 'z')) ? (char) (c - 32) : c)
					: (((c >= 'A') && (c <= 'Z')) ? (char) (c + 32) : c);
			if (r != c) {
				if (chars == null) {
					chars = s.toCharArray();
				}
				chars[i] = r;
			}
		}
		return chars == null ? s : new String(chars);
	}
}
