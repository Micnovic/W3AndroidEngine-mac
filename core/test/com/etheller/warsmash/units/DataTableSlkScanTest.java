package com.etheller.warsmash.units;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The SLK records are scanned in place; these are the scans as they were written with split and substring, which the
 * in-place ones have to answer exactly like.
 */
class DataTableSlkScanTest {
	private static final String[] PARTS = { "C", "F", "E", "B", "P", "O", "ID", "X", "Y", "K", "N", "D0", "X1", "Y1",
			"X12", "Y300", "X-4", "Y+7", "X0012", "X99999999999", "Xa", "Y", "K\"a;b\"", "K12", "K\"x,y\"", "PWXL",
			"X\u0661", "", ";", "K" };

	private static boolean splitPosition(final String record, final int[] position) {
		final int valueIndex = record.indexOf(";K");
		final String[] fields = (valueIndex == -1 ? record : record.substring(0, valueIndex)).split(";");
		for (int i = 1; i < fields.length; i++) {
			final String field = fields[i];
			if ((field.length() > 1) && ((field.charAt(0) == 'X') || (field.charAt(0) == 'Y'))) {
				try {
					position[field.charAt(0) == 'X' ? 0 : 1] = Integer.parseInt(field.substring(1));
				}
				catch (final NumberFormatException e) {
					// not a coordinate
				}
			}
		}
		return record.startsWith("C;") && (valueIndex != -1);
	}

	private static int splitColumn(final String record) {
		final int valueIndex = record.indexOf(";K");
		final String[] fields = (valueIndex == -1 ? record : record.substring(0, valueIndex)).split(";");
		for (final String field : fields) {
			if (field.startsWith("X")) {
				try {
					return Integer.parseInt(field.substring(1));
				}
				catch (final NumberFormatException e) {
					return -1;
				}
			}
		}
		return -1;
	}

	private static boolean readLineRowsStartingAfterTheKey(final String text) throws IOException {
		final int[] position = { 1, 1 };
		int row = 1;
		boolean rowStartedOffKey = false;
		final BufferedReader reader = new BufferedReader(new StringReader(text));
		String record;
		while ((record = reader.readLine()) != null) {
			if (record.startsWith("E")) {
				break;
			}
			if ((record.startsWith("C;") || record.startsWith("F;")) && splitPosition(record, position)) {
				if (position[1] != row) {
					row = position[1];
					rowStartedOffKey = (row > 1) && (position[0] != 1);
				}
				else if (rowStartedOffKey && (position[0] == 1)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String record(final Random random) {
		final StringBuilder record = new StringBuilder();
		final int fields = random.nextInt(6);
		for (int i = 0; i < fields; i++) {
			if ((i > 0) || random.nextBoolean()) {
				record.append(';');
			}
			record.append(PARTS[random.nextInt(PARTS.length)]);
		}
		return record.toString();
	}

	@Test
	void recordsAreScannedAsWithSplit() {
		final Random random = new Random(7);
		final int[] split = { 1, 1 };
		final int[] scanned = { 1, 1 };
		for (int i = 0; i < 200000; i++) {
			final String record = record(random);
			assertEquals(splitPosition(record, split), DataTable.slkPosition(record, scanned), record);
			assertArrayEquals(split, scanned, record);
			assertEquals(splitColumn(record), DataTable.slkColumn(record), record);
		}
	}

	@Test
	void tablesAreScannedAsLineByLine() throws IOException {
		final Random random = new Random(11);
		final String[] lineEnds = { "\r\n", "\n", "\r", "\r\r\n", "\n\n" };
		for (int i = 0; i < 20000; i++) {
			final List<String> records = new ArrayList<>();
			final int count = random.nextInt(12);
			for (int k = 0; k < count; k++) {
				final int x = 1 + random.nextInt(3);
				final int y = 1 + random.nextInt(3);
				records.add(random.nextInt(4) == 0 ? record(random)
						: "C" + (random.nextBoolean() ? ";X" + x : "") + (random.nextBoolean() ? ";Y" + y : "")
								+ (random.nextInt(5) == 0 ? "" : ";K" + k));
			}
			final StringBuilder text = new StringBuilder();
			for (final String record : records) {
				text.append(record).append(lineEnds[random.nextInt(lineEnds.length)]);
			}
			if (random.nextBoolean() && (text.length() > 0)) {
				text.setLength(text.length() - 1);
			}
			final String table = text.toString();
			assertEquals(readLineRowsStartingAfterTheKey(table), DataTable.hasRowsStartingAfterTheKey(table), table);
		}
	}
}
