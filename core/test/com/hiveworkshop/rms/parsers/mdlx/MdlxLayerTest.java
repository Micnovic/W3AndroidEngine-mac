package com.hiveworkshop.rms.parsers.mdlx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import com.hiveworkshop.rms.util.BinaryReader;

class MdlxLayerTest {
	/** A reforged layer (version 1100) whose last texture is the last thing in the file. */
	@Test
	void layerEndingWithItsLastTextureReadsWithoutAFlipbookTag() {
		final ByteBuffer buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(0); // size, set below
		buffer.putInt(0); // filter mode
		buffer.putInt(0); // flags
		buffer.putInt(3); // texture id
		buffer.putInt(-1); // texture animation id
		buffer.putInt(0); // coord id
		buffer.putFloat(1f); // alpha
		buffer.putFloat(0f); // emissive gain
		buffer.putFloat(0f).putFloat(0f).putFloat(0f); // fresnel color
		buffer.putFloat(0f); // fresnel opacity
		buffer.putFloat(0f); // fresnel team color
		buffer.putInt(0); // shader type
		buffer.putInt(2); // texture count
		buffer.putInt(4).putInt(0);
		buffer.putInt(5).putInt(1);
		final int length = buffer.position();
		buffer.putInt(0, length);
		final byte[] bytes = new byte[length];
		buffer.position(0);
		buffer.get(bytes);

		final MdlxLayer layer = new MdlxLayer();
		layer.readMdx(new BinaryReader(ByteBuffer.wrap(bytes)), 1100);

		assertEquals(2, layer.textures.size());
		assertEquals(5, layer.textures.get(1).textureId);
	}

	/** A file that says 1300 with layers in the first layout, which end after the alpha. */
	@Test
	void layerOfTheFirstLayoutInANewerFileReadsAsItIs() {
		final ByteBuffer buffer = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(28);
		buffer.putInt(3); // filter mode
		buffer.putInt(0x11); // flags
		buffer.putInt(2); // texture id
		buffer.putInt(-1); // texture animation id
		buffer.putInt(0); // coord id
		buffer.putFloat(1f); // alpha

		final MdlxLayer layer = new MdlxLayer();
		layer.readMdx(new BinaryReader(buffer), 1300);

		assertEquals(2, layer.textureId);
		assertEquals(1f, layer.alpha);
		assertEquals(0, layer.textures.size());
	}
}
