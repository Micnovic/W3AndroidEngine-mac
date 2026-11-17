package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.parsers.w3x.doo.Doodad;
import com.etheller.warsmash.parsers.w3x.w3e.War3MapW3e;
import com.etheller.warsmash.util.ParseUtils;
import com.etheller.warsmash.util.War3ID;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;

/**
 * Scripts read the doodads from the map's doodad table in file order, whatever is drawn.
 */
class CDoodadTableTest {
	private static final War3ID TREE = War3ID.fromString("LTlt");
	private static final War3ID GATE = War3ID.fromString("DTg1");
	private static final float LIMIT = 0.3f;

	private static Doodad doodad(final War3ID id, final int variation, final float x, final float y, final float z,
			final float angle, final int flags) {
		final Doodad doodad = new Doodad();
		doodad.setId(id);
		doodad.setVariation(variation);
		doodad.getLocation()[0] = x;
		doodad.getLocation()[1] = y;
		doodad.getLocation()[2] = z;
		doodad.getScale()[0] = 1.5f;
		doodad.getScale()[1] = 2f;
		doodad.getScale()[2] = 2.5f;
		doodad.setAngle(angle);
		doodad.setFlags((short) flags);
		return doodad;
	}

	private static float[] onlyTrees(final War3ID id) {
		return TREE.equals(id) ? new float[] { LIMIT, LIMIT } : null;
	}

	/** Ground that rises with the column and is flat along the rows. */
	private static CTerrainData slope() throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (LittleEndianDataOutputStream out = new LittleEndianDataOutputStream(bytes)) {
			ParseUtils.writeWar3ID(out, War3ID.fromString("W3E!"));
			out.writeInt(11);
			out.write('L');
			out.writeInt(0);
			out.writeInt(0);
			out.writeInt(0);
			out.writeInt(8);
			out.writeInt(8);
			out.writeFloat(0);
			out.writeFloat(0);
			for (int row = 0; row < 8; row++) {
				for (int column = 0; column < 8; column++) {
					out.writeShort(8192 + (column * 600));
					out.writeShort(8192);
					out.write(0);
					out.write(0);
					out.write(2);
				}
			}
		}
		return new CTerrainData(new War3MapW3e(new LittleEndianDataInputStream(new ByteArrayInputStream(bytes.toByteArray()))), 'L');
	}

	@Test
	void listsShownDoodadsOnlyInFileOrder() {
		final List<Doodad> doo = new ArrayList<>();
		doo.add(doodad(TREE, 1, 10, 20, 30, 0.5f, 2));
		doo.add(doodad(GATE, 0, 1, 1, 1, 0, 2));
		doo.add(doodad(TREE, 2, 11, 21, 31, 0.25f, 0));
		doo.add(doodad(TREE, 3, 12, 22, 32, 1f, 3));
		final CDoodadTable table = CDoodadTable.build(doo, CDoodadTableTest::onlyTrees, null);
		assertEquals(2, table.size());
		assertEquals(1, table.get(0).variation);
		assertEquals(10, table.get(0).x);
		assertEquals(20, table.get(0).y);
		assertEquals(30, table.get(0).z);
		assertEquals(2.5f, table.get(0).scaleZ);
		assertEquals(0.5f, table.get(0).yaw);
		assertEquals(TREE, table.get(0).typeId);
		assertEquals(3, table.get(1).variation);
		assertNull(table.get(2));
		assertNull(table.get(-1));
	}

	@Test
	void withoutTerrainTheDoodadsStandUpright() {
		final CDoodadTable table = CDoodadTable.build(List.of(doodad(TREE, 0, 100, 100, 0, 0, 2)),
				CDoodadTableTest::onlyTrees, null);
		assertEquals(0f, table.get(0).pitch);
		assertEquals(0f, table.get(0).roll);
	}

	@Test
	void tiltFollowsTheGroundAndStaysWithinTheLimit() throws IOException {
		final CTerrainData terrain = slope();
		final CDoodadTable table = CDoodadTable.build(List.of(doodad(TREE, 0, 300, 300, 0, 0, 2),
				doodad(TREE, 0, 300, 300, 0, (float) (Math.PI / 2), 2)), CDoodadTableTest::onlyTrees, terrain);
		assertTrue(table.get(0).pitch > 0, "pitch " + table.get(0).pitch);
		assertTrue(table.get(0).pitch <= LIMIT);
		assertEquals(0f, table.get(0).roll, 1e-6f);
		assertEquals(0f, table.get(1).pitch, 1e-6f);
		assertTrue(Math.abs(table.get(1).roll) > 0);
	}
}
