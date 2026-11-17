package com.etheller.warsmash.viewer5.handlers.blp;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import com.etheller.warsmash.util.StreamBytes;

import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.RawOpenGLTextureResource;
import com.etheller.warsmash.viewer5.handlers.ResourceHandler;

public class BlpTexture extends RawOpenGLTextureResource {

	public BlpTexture(final ModelViewer viewer, final ResourceHandler handler, final String extension,
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

	/** Decodes and compresses the file on a helper thread; what is not a BLP or DDS is left to the game thread. */
	private void prepare(final byte[] bytes, final int limit, final boolean compress) {
		try {
			final ByteBuffer data = ByteBuffer.wrap(bytes);
			final boolean dds = DdsDecoder.isDds(data);
			if (!dds && !BlpDecoder.isBlp(data)) {
				return;
			}
			final MipLevels[] decoded = new MipLevels[1];
			final TextureTranscoder.Decoder decoder = () -> {
				decoded[0] = dds ? DdsDecoder.decode(data, limit, true).getLevels()
						: BlpDecoder.decode(data, limit, true).getLevels();
				return decoded[0];
			};
			if (compress) {
				this.preparedBlocks = TextureTranscoder.transcode(this.fetchUrl, bytes, limit, decoder,
						dds && DdsDecoder.isNormalMap(data));
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
				// A missing .blp can resolve to its .dds. Classic data stores the same colors in
				// both, so the .dds takes the plain RGBA format of the .blp.
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
		BufferedImage img;
		final ByteBuffer data = ByteBuffer.wrap(bytes);
		final boolean shadow = isShadowTexture();
		final int limit = shadow ? 0 : this.viewer.getTextureSizeLimit();
		final boolean dds = DdsDecoder.isDds(data);
		if (this.viewer.isTextureCompression() && !shadow && (dds || BlpDecoder.isBlp(data))) {
			final CompressedMips blocks = TextureTranscoder.transcode(this.fetchUrl, bytes, limit,
					() -> dds ? DdsDecoder.decode(data, limit, true).getLevels()
							: BlpDecoder.decode(data, limit, true).getLevels(),
					dds && DdsDecoder.isNormalMap(data));
			if (blocks != null) {
				update(blocks);
				return;
			}
		}
		if (dds) {
			// A missing .blp can resolve to its .dds. Classic data stores the same colors in
			// both, so the .dds takes the plain RGBA format of the .blp below.
			update(DdsDecoder.decode(data, limit, true).getLevels(), false);
			return;
		}
		if (BlpDecoder.isBlp(data)) {
			// The ImageIO plugin reads these colors as linear and the sRGB texture format
			// undoes that, so the raw colors in a plain RGBA texture match it.
			update(BlpDecoder.decode(data, limit, true).getLevels(), false);
			return;
		}
		if (!ImageUtils.IMAGE_IO_AVAILABLE) {
			Log.warn("No decoder for this BLP on this platform, texture left empty");
			return;
		}
		img = ImageIO.read(new ByteArrayInputStream(data.array()));
		update(img, true);
	}

}
