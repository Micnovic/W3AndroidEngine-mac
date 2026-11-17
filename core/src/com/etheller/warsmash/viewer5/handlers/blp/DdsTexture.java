package com.etheller.warsmash.viewer5.handlers.blp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

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

	@Override
	protected void load(final InputStream src, final Object options) {
		try {
			final byte[] bytes = StreamBytes.readAll(src);
			if (!isShadowTexture()) {
				final int limit = this.viewer.getTextureSizeLimit();
				final boolean compress = this.viewer.isTextureCompression();
				if (this.viewer.deferTextureLoad(() -> prepare(bytes, limit, compress), () -> finishLoad(bytes))) {
					return;
				}
			}
			decodeAndUpload(bytes);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
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
	}

	private void finishLoad(final byte[] bytes) {
		final CompressedMips blocks = this.preparedBlocks;
		final MipLevels levels = this.preparedLevels;
		final Exception failure = this.prepareError;
		this.preparedBlocks = null;
		this.preparedLevels = null;
		this.prepareError = null;
		if (isDisposed()) {
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
		final int limit = shadow ? 0 : this.viewer.getTextureSizeLimit();
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
