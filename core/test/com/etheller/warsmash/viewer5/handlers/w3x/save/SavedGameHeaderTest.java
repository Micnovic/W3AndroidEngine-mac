package com.etheller.warsmash.viewer5.handlers.w3x.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

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
		// The campaign game mark of format 9 sits right before the line.
		final byte[] old = Arrays.copyOf(now, now.length - line.length - 1);
		old[7] = 7;
		final DataInputStream in = new DataInputStream(new ByteArrayInputStream(old));
		final SavedGame.Header read = SavedGame.Header.read(in);
		assertEquals(7, read.formatVersion);
		assertEquals("Maps/Test.w3x", read.mapFile);
		assertEquals(0, in.available());
	}

	@Test
	public void theCampaignMarkIsKeptAndAnOlderSaveIsToldByItsFolder() throws IOException {
		final SavedGame.Header header = new SavedGame.Header();
		header.mapFile = "Downloads/Custom.w3x";
		header.campaignGame = true;
		header.positions = new SavedScriptPositions.Table();
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		header.write(new DataOutputStream(bytes));
		final byte[] now = bytes.toByteArray();
		assertTrue(SavedGame.Header.read(new DataInputStream(new ByteArrayInputStream(now))).campaignGame);

		final byte[] line = authorshipLine();
		final int at = now.length - line.length;
		final byte[] old = new byte[now.length - 1];
		System.arraycopy(now, 0, old, 0, at - 1);
		System.arraycopy(now, at, old, at - 1, line.length);
		old[7] = 8;
		final SavedGame.Header read = SavedGame.Header.read(new DataInputStream(new ByteArrayInputStream(old)));
		assertEquals(8, read.formatVersion);
		assertFalse(read.campaignGame);
		assertEquals("Downloads/Custom.w3x", read.mapFile);
	}

	@Test
	public void aDamagedLengthIsRefusedBeforeItBecomesAnAllocation() throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(Integer.MAX_VALUE);
		out.writeInt(-5);
		final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
		assertThrows(IOException.class, () -> SavedGame.readBlock(in, 100));
		assertThrows(IOException.class, () -> SavedGame.readBlock(in, 100));

		final ByteArrayOutputStream good = new ByteArrayOutputStream();
		final DataOutputStream goodOut = new DataOutputStream(good);
		goodOut.writeInt(3);
		goodOut.write(new byte[] { 1, 2, 3 });
		assertEquals(3, SavedGame.readBlock(new DataInputStream(new ByteArrayInputStream(good.toByteArray())), 7).length);
	}

	@Test
	public void aTrailerSayingMoreThanDeflateCanPackIsRefused() throws IOException {
		final ByteArrayOutputStream packed = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(packed)) {
			gzip.write(new byte[5000]);
		}
		final byte[] ok = packed.toByteArray();
		assertEquals(5000, SavedGame.gunzipFully(ok).length);
		final byte[] forged = ok.clone();
		forged[forged.length - 1] = 0x7f;
		assertThrows(IOException.class, () -> SavedGame.gunzipFully(forged));
	}
}
