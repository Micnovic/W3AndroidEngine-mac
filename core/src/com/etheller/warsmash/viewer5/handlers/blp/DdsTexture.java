package com.etheller.warsmash.viewer5.handlers.blp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import com.etheller.warsmash.datasources.IndependentStream;
import com.etheller.warsmash.util.OffHeapBlock;
import com.etheller.warsmash.util.StreamBytes;

import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.RawOpenGLTextureResource;
import com.etheller.warsmash.viewer5.handlers.ResourceHandler;

public class DdsTexture extends RawOpenGLTextureResource {

	public DdsTexture(final ModelViewer viewer, final ResourceHandler handler, final String extension,
			final PathSolver pathSolver, final String fetchUrl) {
		super(viewer, extension, pathSolver, fetchUrl, handler);
	}

	@Override
	protected void lateLoad() {

	}

	/** What {@link #prepare} left for {@link #finishLoad}: blocks or decoded levels, or the error that stopped it. */
	private CompressedMips preparedBlocks;
	private MipLevels preparedLevels;
	private Exception prepareError;
	/** The file, kept for the game thread only while the helper thread has not prepared it. */
	private byte[] fileBytes;
	/** The file of a texture that is queued before its helper has read it, outside the Java heap. */
	private OffHeapBlock queuedFile;

	/** The queued file as an array for the helper thread; it stays in {@link #fileBytes} for what the helper cannot do. */
	private byte[] queuedBytes() {
		final OffHeapBlock block = this.queuedFile;
		if (block == null) {
			return this.fileBytes;
		}
		final byte[] bytes = new byte[block.buffer().capacity()];
		block.buffer().duplicate().get(bytes);
		this.queuedFile = null;
		block.free();
		this.fileBytes = bytes;
		return bytes;
	}

	@Override
	protected void load(final InputStream src, final Object options) {
		try {
			final boolean shadow = isShadowTexture();
			if (!shadow && (src instanceof IndependentStream)) {
				// the helper reads the file too, which is most of what a texture costs on the game thread
				final int limit = this.viewer.getTextureSizeRule();
				final boolean compress = this.viewer.isTextureCompression();
				final boolean blocks = this.viewer.isBlockTextures();
				if (this.viewer.deferTextureLoad(() -> {
					try (InputStream stream = src) {
						if (decodesOffHeap(compress)) {
							prepareOffHeap(OffHeapBlock.readAll(stream), limit, blocks);
						}
						else {
							this.fileBytes = StreamBytes.readAll(stream);
							prepare(this.fileBytes, limit, compress);
						}
					}
					catch (final IOException | RuntimeException e) {
						this.prepareError = e;
					}
					catch (final OutOfMemoryError e) {
						this.fileBytes = null;
						this.prepareError = new RuntimeException("Out of memory reading " + this.fetchUrl, e);
					}
					return heldBytes();
				}, () -> finishLoad(null))) {
					setPending(true);
					return;
				}
			}
			final byte[] bytes = StreamBytes.readAll(src);
			if (!shadow) {
				final int limit = this.viewer.getTextureSizeRule();
				final boolean compress = this.viewer.isTextureCompression();
				final boolean blocks = this.viewer.isBlockTextures();
				// Queued files wait for a helper until it starts them: on Android that is kept outside the Java heap.
				if (OffHeapBlock.directBuffersInHeap() || blocks) {
					this.queuedFile = OffHeapBlock.copyOf(bytes);
				}
				else {
					this.fileBytes = bytes;
				}
				if (this.viewer.deferTextureLoad(() -> {
					if (decodesOffHeap(compress) && (this.queuedFile != null)) {
						final OffHeapBlock file = this.queuedFile;
						this.queuedFile = null;
						prepareOffHeap(file, limit, blocks);
					}
					else {
						prepare(queuedBytes(), limit, compress);
					}
					return heldBytes();
				}, () -> finishLoad(null))) {
					setPending(true);
					return;
				}
				if (this.queuedFile != null) {
					this.queuedFile.free();
					this.queuedFile = null;
				}
				this.fileBytes = null;
			}
			decodeAndUpload(bytes);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * The file and every decoded level passed through the Java heap as arrays, and the levels again as direct buffers
	 * for the upload, which kept the collector busy through a map's loading (and on Android is what ran a phone out of
	 * heap); the helper reads the file into a block outside the heap and decodes into such blocks. Compressed textures
	 * are encoded from arrays.
	 */
	private static boolean decodesOffHeap(final boolean compress) {
		return !compress;
	}

	/** Makes the texture of the file in the block; the block goes to the result that keeps its bytes, else is freed. */
	private void prepareOffHeap(final OffHeapBlock file, final int limit, final boolean blocks) {
		boolean kept = false;
		try {
			if (blocks) {
				this.preparedBlocks = DdsDecoder.blocks(file, limit);
				kept = this.preparedBlocks != null;
			}
			if (!kept) {
				this.preparedLevels = DdsDecoder.decode(file.buffer(), limit, true, true).getLevels();
			}
		}
		catch (final IOException | RuntimeException e) {
			this.prepareError = e;
		}
		catch (final OutOfMemoryError e) {
			this.preparedLevels = null;
			this.prepareError = new RuntimeException("Out of memory preparing " + this.fetchUrl, e);
		}
		finally {
			if (!kept) {
				file.free();
			}
		}
	}

	/** Decodes and compresses the file on a helper thread. */
	private void prepare(final byte[] bytes, final int limit, final boolean compress) {
		try {
			final ByteBuffer file = ByteBuffer.wrap(bytes);
			final MipLevels[] decoded = new MipLevels[1];
			final TextureTranscoder.Decoder decoder = () -> {
				decoded[0] = DdsDecoder.decode(file, limit, true).getLevels();
				return decoded[0];
			};
			if (compress) {
				this.preparedBlocks = TextureTranscoder.transcode(this.fetchUrl, bytes, limit, decoder,
						DdsDecoder.isNormalMap(file));
				if (this.preparedBlocks != null) {
					return;
				}
			}
			this.preparedLevels = decoded[0] != null ? decoded[0] : decoder.decode();
		}
		catch (final IOException | RuntimeException e) {
			this.prepareError = e;
		}
		catch (final OutOfMemoryError e) {
			// the helper thread dies otherwise, and the texture would stay unfinished
			this.preparedBlocks = null;
			this.preparedLevels = null;
			this.prepareError = new RuntimeException("Out of memory preparing " + this.fetchUrl, e);
		}
	}

	/**
	 * The bytes the helper thread's result holds until the game thread uploads it. Once prepared (or failed) the file
	 * itself is let go: dozens of textures wait for their upload when units of new types appear, and their files
	 * kept beside the results filled a phone's heap.
	 */
	private long heldBytes() {
		if ((this.preparedBlocks != null) || (this.preparedLevels != null) || (this.prepareError != null)) {
			this.fileBytes = null;
		}
		return preparedBytes() + (this.fileBytes != null ? this.fileBytes.length : 0);
	}

	private long preparedBytes() {
		return this.preparedBlocks != null ? this.preparedBlocks.byteCount()
				: this.preparedLevels != null ? this.preparedLevels.byteCount() : 0;
	}

	private void finishLoad(final byte[] readBytes) {
		final byte[] bytes = readBytes != null ? readBytes : this.fileBytes;
		this.fileBytes = null;
		final CompressedMips blocks = this.preparedBlocks;
		final MipLevels levels = this.preparedLevels;
		final Exception failure = this.prepareError;
		this.preparedBlocks = null;
		this.preparedLevels = null;
		this.prepareError = null;
		setPending(false);
		if (isDisposed()) {
			if (levels != null) {
				levels.free();
			}
			if (blocks != null) {
				blocks.free();
			}
			return;
		}
		try {
			if (failure != null) {
				error(failure);
			}
			else if (blocks != null) {
				update(blocks);
			}
			else if (levels != null) {
				update(levels, false);
			}
			else {
				decodeAndUpload(bytes);
			}
		}
		catch (final IOException | RuntimeException e) {
			error(e);
		}
	}

	private void decodeAndUpload(final byte[] bytes) throws IOException {
		final ByteBuffer file = ByteBuffer.wrap(bytes);
		final boolean shadow = isShadowTexture();
		final int limit = shadow ? 0 : this.viewer.getTextureSizeRule();
		if (this.viewer.isBlockTextures() && !shadow) {
			final OffHeapBlock block = OffHeapBlock.copyOf(bytes);
			final CompressedMips blocks = DdsDecoder.blocks(block, limit);
			if (blocks != null) {
				update(blocks);
				return;
			}
			block.free();
		}
		if (this.viewer.isTextureCompression() && !shadow) {
			final CompressedMips blocks = TextureTranscoder.transcode(this.fetchUrl, bytes, limit,
					() -> DdsDecoder.decode(file, limit, true).getLevels(), DdsDecoder.isNormalMap(file));
			if (blocks != null) {
				update(blocks);
				return;
			}
		}
		final DdsDecoder image = DdsDecoder.decode(file, limit, true);
		update(image.getLevels(), false);
	}

}
