package com.etheller.warsmash.viewer5.handlers.blp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.OffHeapBlock;

/** The mip levels of an image in a GPU block format, the largest first. */
public final class CompressedMips {
	public static final int FORMAT_RG11_EAC = 0x9272;
	public static final int FORMAT_RGB8_ETC2 = 0x9274;
	public static final int FORMAT_RGBA8_ETC2_EAC = 0x9278;
	/** The block formats of the game's DDS files, which desktop GPUs read as they are. */
	public static final int FORMAT_RGBA_S3TC_DXT1 = 0x83F1;
	public static final int FORMAT_RGBA_S3TC_DXT3 = 0x83F2;
	public static final int FORMAT_RGBA_S3TC_DXT5 = 0x83F3;
	public static final int FORMAT_RG_RGTC2 = 0x8DBD;

	private final int format;
	private final List<int[]> sizes = new ArrayList<>();
	private final List<byte[]> blocks = new ArrayList<>();
	/** A level that is a view of {@link #owner}'s bytes has its blocks here and none in {@link #blocks}. */
	private final List<ByteBuffer> views = new ArrayList<>();
	private OffHeapBlock owner;

	public CompressedMips(final int format) {
		this.format = format;
	}

	/** Bytes of one 4x4 block of a format. */
	public static int blockBytes(final int format) {
		return (format == FORMAT_RGB8_ETC2) || (format == FORMAT_RGBA_S3TC_DXT1) ? 8 : 16;
	}

	public void add(final int width, final int height, final byte[] data) {
		this.sizes.add(new int[] { width, height });
		this.blocks.add(data);
		this.views.add(null);
	}

	/** A level whose blocks are these bytes of the block the levels are kept in, see {@link #keep}. */
	void add(final int width, final int height, final ByteBuffer blocksView) {
		this.sizes.add(new int[] { width, height });
		this.blocks.add(null);
		this.views.add(blocksView);
	}

	/** Takes over a block that the levels added as views point into; {@link #free()} gives it back. */
	void keep(final OffHeapBlock block) {
		this.owner = block;
	}

	/** The blocks of a level that is a view of a block, from position 0, or null when it is an array. */
	public ByteBuffer getView(final int level) {
		final ByteBuffer view = this.views.get(level);
		if (view != null) {
			view.clear();
		}
		return view;
	}

	/** Gives back the block the levels are views of; their buffers may not be used afterwards. */
	public void free() {
		if (this.owner != null) {
			this.owner.free();
			this.owner = null;
		}
	}

	public int getFormat() {
		return this.format;
	}

	public int count() {
		return this.blocks.size();
	}

	public int getWidth(final int level) {
		return this.sizes.get(level)[0];
	}

	public int getHeight(final int level) {
		return this.sizes.get(level)[1];
	}

	public byte[] getBlocks(final int level) {
		return this.blocks.get(level);
	}

	public long byteCount() {
		long bytes = 0;
		for (int level = 0; level < count(); level++) {
			bytes += this.blocks.get(level) != null ? this.blocks.get(level).length : this.views.get(level).capacity();
		}
		return bytes;
	}

	public void write(final DataOutputStream out) throws IOException {
		out.writeInt(this.format);
		out.writeInt(count());
		for (int level = 0; level < count(); level++) {
			out.writeInt(getWidth(level));
			out.writeInt(getHeight(level));
			out.writeInt(this.blocks.get(level).length);
			out.write(this.blocks.get(level));
		}
	}

	/** @throws IOException if the data are not whole levels of a known format */
	public static CompressedMips read(final DataInputStream in) throws IOException {
		final int format = in.readInt();
		if ((format != FORMAT_RG11_EAC) && (format != FORMAT_RGB8_ETC2) && (format != FORMAT_RGBA8_ETC2_EAC)) {
			throw new IOException("Unknown texture format " + Integer.toHexString(format));
		}
		final int count = in.readInt();
		if ((count < 1) || (count > 16)) {
			throw new IOException("Bad level count " + count);
		}
		final CompressedMips levels = new CompressedMips(format);
		for (int level = 0; level < count; level++) {
			final int width = in.readInt();
			final int height = in.readInt();
			final int length = in.readInt();
			if ((width < 1) || (height < 1) || (length != Etc2Encoder.byteCount(width, height, blockBytes(format)))) {
				throw new IOException("Bad level " + level);
			}
			final byte[] data = new byte[length];
			in.readFully(data);
			levels.add(width, height, data);
		}
		return levels;
	}
}
