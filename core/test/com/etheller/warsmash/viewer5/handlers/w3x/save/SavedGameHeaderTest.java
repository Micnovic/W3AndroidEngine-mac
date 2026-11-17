package com.etheller.warsmash.viewer5.handlers.w3x.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.launcher.ProjectInfo;

class SavedGameHeaderTest {
	private static byte[] written() throws IOException {
		final SavedGame.Header header = new SavedGame.Header();
		header.mapFile = "Maps/Test.w3x";
		header.localPlayerIndex = 2;
		header.positions = new SavedScriptPositions.Table();
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		header.write(new DataOutputStream(bytes));
		return bytes.toByteArray();
	}

	private static byte[] authorshipLine() throws IOException {
		final ByteArrayOutputStream line = new ByteArrayOutputStream();
		new DataOutputStream(line).writeUTF(ProjectInfo.AUTHORSHIP);
		return line.toByteArray();
	}

	@Test
	public void theHeaderNamesTheProjectAndReadsBack() throws IOException {
		final byte[] bytes = written();
		final byte[] line = authorshipLine();
		assertTrue(new String(bytes, StandardCharsets.ISO_8859_1).contains("W3AndroidEngine by nikoltu"));
		assertEquals(Arrays.toString(line), Arrays.toString(Arrays.copyOfRange(bytes, bytes.length - line.length, bytes.length)));
		final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
		final SavedGame.Header read = SavedGame.Header.read(in);
		assertEquals("Maps/Test.w3x", read.mapFile);
		assertEquals(2, read.localPlayerIndex);
		assertEquals(0, in.available(), "the authorship line is part of the header");
	}

	@Test
	public void aSaveFromBeforeTheAuthorshipLineStillReads() throws IOException {
		final byte[] now = written();
		final byte[] line = authorshipLine();
		final byte[] old = Arrays.copyOf(now, now.length - line.length);
		old[7] = 7;
		final DataInputStream in = new DataInputStream(new ByteArrayInputStream(old));
		final SavedGame.Header read = SavedGame.Header.read(in);
		assertEquals(7, read.formatVersion);
		assertEquals("Maps/Test.w3x", read.mapFile);
		assertEquals(0, in.available());
	}
}
