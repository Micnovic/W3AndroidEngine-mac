package com.etheller.warsmash.parsers.facefx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The container of Reforged's FaceFX files ({@code *.animset_ingame}, {@code *.facefx_ingame}): an archive that begins
 * with {@code FACE}, the SDK version, the names of the maker and the project, the table of the classes whose objects
 * follow, and the table of names every object refers to by index. The readers of the two file kinds
 * ({@link FaceFxAnimSet}, {@link FaceFxActor}) take the rest of the stream from {@link #getNamesEnd()}.
 */
public final class FaceFxArchive {
	private static final int MAGIC = 0x45434146;
	private static final int MAX_CLASSES = 256;
	private static final int MAX_STRING = 4096;
	private static final int MAX_NAMES = 65536;
	private static final int MAX_NAME_RECORD_GAP = 256;

	public final int version;
	public final String creator;
	public final String project;
	public final int licenseeVersion;
	private final List<String> classNames;
	/** Offset of the first byte after the class table, where the name table begins. */
	public final int objectsOffset;
	private final byte[] bytes;
	private final String[] names;
	private final int namesEnd;

	private FaceFxArchive(final int version, final String creator, final String project, final int licenseeVersion,
			final List<String> classNames, final int objectsOffset, final byte[] bytes, final String[] names,
			final int namesEnd) {
		this.version = version;
		this.creator = creator;
		this.project = project;
		this.licenseeVersion = licenseeVersion;
		this.classNames = Collections.unmodifiableList(classNames);
		this.objectsOffset = objectsOffset;
		this.bytes = bytes;
		this.names = names;
		this.namesEnd = namesEnd;
	}

	/** @return the names of the classes in the archive, in the order of the table (FxObject, FxAnim, ...) */
	public List<String> getClassNames() {
		return this.classNames;
	}

	public boolean hasClass(final String name) {
		return this.classNames.contains(name);
	}

	/** @return true when the name table was read; the readers of the object data need it */
	public boolean hasNames() {
		return this.names != null;
	}

	public int getNameCount() {
		return (this.names == null) ? 0 : this.names.length;
	}

	/** @return the name with this index, or null when the index is outside the table */
	public String getName(final int index) {
		if ((this.names == null) || (index < 0) || (index >= this.names.length)) {
			return null;
		}
		return this.names[index];
	}

	/** @return the offset of the first byte after the name table: the object data begins somewhere behind it */
	public int getNamesEnd() {
		return this.namesEnd;
	}

	/** @return the whole file as little endian, positioned at 0 */
	public ByteBuffer data() {
		return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN);
	}

	/** @return the archive's header, class table and name table, or null when the data is not a FaceFX archive of this layout */
	public static FaceFxArchive read(final byte[] bytes) throws IOException {
		final ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		try {
			if ((data.remaining() < 12) || (data.getInt() != MAGIC)) {
				return null;
			}
			final int version = data.getInt();
			// 13 in every animset and 13+ in the actors; what it counts is unknown.
			final int headerCount = data.getInt();
			if ((headerCount < 0) || (headerCount > MAX_CLASSES)) {
				return null;
			}
			final String creator = readString(data, 1);
			final String project = readString(data, 1);
			final int licenseeVersion = data.getInt();
			// 2 bytes of unknown meaning, then the class count twice.
			data.getShort();
			final int classCount = data.getInt();
			if ((classCount < 0) || (classCount > MAX_CLASSES) || (data.getInt() != classCount)) {
				return null;
			}
			final List<String> classNames = new ArrayList<>();
			for (int i = 0; i < classCount; i++) {
				// A running figure (offset or size, unknown), 1, the class's number, the name.
				data.getInt();
				data.getInt();
				if (data.getInt() != i) {
					return null;
				}
				classNames.add(readString(data, 0));
				// The class's version, 1, three -1 and 2 bytes: the same 20 bytes for every class seen.
				data.getShort();
				data.getInt();
				data.position(data.position() + 12 + 2);
			}
			final int objectsOffset = data.position();
			String[] names = null;
			int namesEnd = objectsOffset;
			final int[] end = new int[1];
			names = readNames(bytes, objectsOffset, classCount, end);
			if (names != null) {
				namesEnd = end[0];
			}
			return new FaceFxArchive(version, creator, project, licenseeVersion, classNames, objectsOffset, bytes,
					names, namesEnd);
		}
		catch (final RuntimeException e) {
			return null;
		}
	}

	public static FaceFxArchive read(final ByteBuffer source) throws IOException {
		final byte[] bytes = new byte[source.remaining()];
		source.duplicate().get(bytes);
		return read(bytes);
	}

	/**
	 * The name table is a count followed by one record per name: the object's number (counting up from the number of
	 * classes), the index of the name, the length and the characters, then links to the neighbours in a tree whose
	 * length varies. The tree is skipped by looking for the next number in sequence.
	 */
	private static String[] readNames(final byte[] bytes, final int offset, final int firstId, final int[] endOut) {
		final ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		if (offset + 4 > bytes.length) {
			return null;
		}
		final int count = data.getInt(offset);
		if ((count < 0) || (count > MAX_NAMES)) {
			return null;
		}
		final String[] names = new String[count];
		int position = offset + 4;
		int end = position;
		for (int n = 0; n < count; n++) {
			final int id = firstId + n;
			boolean found = false;
			final int limit = Math.min(position + MAX_NAME_RECORD_GAP, bytes.length - 12);
			for (int q = position; q <= limit; q++) {
				if (data.getInt(q) != id) {
					continue;
				}
				final int index = data.getInt(q + 4);
				final int length = data.getInt(q + 8);
				if ((index < 0) || (index >= count) || (length < 0) || (length > MAX_STRING)
						|| (q + 12 + length > bytes.length) || (names[index] != null)) {
					continue;
				}
				boolean printable = true;
				for (int i = 0; i < length; i++) {
					if ((bytes[q + 12 + i] & 0xFF) < 32) {
						printable = false;
						break;
					}
				}
				if (!printable) {
					continue;
				}
				names[index] = new String(bytes, q + 12, length, StandardCharsets.ISO_8859_1);
				position = q + 12 + length;
				end = position;
				found = true;
				break;
			}
			if (!found) {
				return null;
			}
		}
		endOut[0] = end;
		return names;
	}

	/** A length, the characters, and for the maker and project names a zero the length counts too. */
	private static String readString(final ByteBuffer data, final int terminator) {
		final int length = data.getInt();
		if ((length < terminator) || (length > MAX_STRING)) {
			throw new IllegalArgumentException("string length " + length);
		}
		final byte[] bytes = new byte[length];
		data.get(bytes);
		return new String(bytes, 0, length - terminator, StandardCharsets.ISO_8859_1);
	}
}
