package com.etheller.warsmash.parsers.w3x.w3i;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;

class War3MapW3iTest {
	private static void string(final LittleEndianDataOutputStream out, final String value) throws IOException {
		out.write(value.getBytes("UTF-8"));
		out.write(0);
	}

	/** Writes a minimal map information file with two players and one force. */
	private static byte[] file(final int version, final int editor) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final LittleEndianDataOutputStream out = new LittleEndianDataOutputStream(bytes);
		out.writeInt(version);
		out.writeInt(3);
		out.writeInt(editor);
		if (version >= 27) {
			out.writeInt(1);
			out.writeInt(31);
			out.writeInt(0);
			out.writeInt(11527);
		}
		string(out, "name");
		string(out, "author");
		string(out, "description");
		string(out, "players");
		for (int i = 0; i < 8; i++) {
			out.writeFloat(i);
		}
		for (int i = 0; i < 6; i++) {
			out.writeInt(i);
		}
		out.writeInt(0);
		out.write('L');
		out.writeInt(-1);
		if (version > 24) {
			string(out, "");
		}
		string(out, "");
		string(out, "");
		string(out, "");
		out.writeInt(0);
		if (version > 24) {
			string(out, "");
		}
		string(out, "");
		string(out, "");
		string(out, "");
		if (version > 24) {
			out.writeInt(0);
			out.writeFloat(3000);
			out.writeFloat(5000);
			out.writeFloat(0.5f);
			out.write(new byte[] { 0, 0, 0, (byte) 255 });
			out.writeInt(0);
			string(out, "");
			out.write(0);
			out.write(new byte[] { (byte) 255, (byte) 255, (byte) 255, (byte) 255 });
		}
		if (version > 27) {
			out.write(new byte[4]);
		}
		if (version > 30) {
			out.writeInt(3);
			out.writeInt(1);
		}
		out.writeInt(2);
		for (int i = 0; i < 2; i++) {
			out.writeInt(i);
			out.writeInt(1);
			out.writeInt(i + 1);
			out.writeInt(0);
			string(out, "p" + i);
			out.writeFloat(100 + i);
			out.writeFloat(200 + i);
			out.writeInt(0);
			out.writeInt(2 - i);
			if (version > 30) {
				out.writeInt(0);
				out.writeInt(0);
			}
		}
		out.writeInt(1);
		out.writeInt(0);
		out.writeInt(-1);
		string(out, "force");
		return bytes.toByteArray();
	}

	private static War3MapW3i parse(final int version, final int editor) throws IOException {
		return new War3MapW3i(new LittleEndianDataInputStream(new ByteArrayInputStream(file(version, editor))));
	}

	private static void assertPlayers(final War3MapW3i info) {
		assertEquals(2, info.getPlayers().size());
		assertEquals("p1", info.getPlayers().get(1).getName());
		assertEquals(2, info.getPlayers().get(1).getRace());
		assertEquals(101f, info.getPlayers().get(1).getStartLocation()[0]);
		assertEquals(1, info.getPlayers().get(1).getAllyHighPriorities());
		assertEquals(1, info.getForces().size());
	}

	@Test
	void version25HasNoGameVersion() throws IOException {
		final War3MapW3i info = parse(25, 6034);
		assertEquals("name", info.getName());
		assertPlayers(info);
	}

	@Test
	void version27CarriesTheGameVersionButNoLuaBytes() throws IOException {
		final War3MapW3i info = parse(27, 6071);
		assertEquals("name", info.getName());
		assertPlayers(info);
	}

	@Test
	void version28CarriesTheGameVersionAndLuaBytes() throws IOException {
		assertPlayers(parse(28, 6072));
	}

	@Test
	void version31CarriesEnemyPriorities() throws IOException {
		assertPlayers(parse(31, 6105));
	}
}
