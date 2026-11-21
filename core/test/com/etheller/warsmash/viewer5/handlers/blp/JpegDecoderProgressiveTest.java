package com.etheller.warsmash.viewer5.handlers.blp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.junit.jupiter.api.Test;

class JpegDecoderProgressiveTest {
	private static byte[] encode(final BufferedImage image, final boolean progressive, final float quality)
			throws IOException {
		final ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		final ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(quality);
		param.setProgressiveMode(progressive ? ImageWriteParam.MODE_DEFAULT : ImageWriteParam.MODE_DISABLED);
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
			writer.setOutput(out);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		writer.dispose();
		return bytes.toByteArray();
	}

	private static BufferedImage noise(final int width, final int height, final int type, final long seed) {
		final BufferedImage image = new BufferedImage(width, height, type);
		final Random random = new Random(seed);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				// Smooth areas with some noise, so that blocks have both few and many coefficients.
				final int base = ((x * 5) + (y * 3)) & 0xFF;
				final int value = (random.nextInt(8) == 0 ? random.nextInt(256) : base);
				image.setRGB(x, y, (value << 16) | (((value * 3) & 0xFF) << 8) | ((value * 7) & 0xFF));
			}
		}
		return image;
	}

	private static void assertSameDecoding(final BufferedImage image, final float quality) throws IOException {
		final JpegDecoder baseline = JpegDecoder.decode(encode(image, false, quality));
		final byte[] progressiveBytes = encode(image, true, quality);
		final JpegDecoder progressive = JpegDecoder.decode(progressiveBytes);
		assertEquals(baseline.getWidth(), progressive.getWidth());
		assertEquals(baseline.getHeight(), progressive.getHeight());
		assertEquals(baseline.getComponentCount(), progressive.getComponentCount());
		assertArrayEquals(baseline.getComponents(), progressive.getComponents());
	}

	@Test
	void progressiveGrayImageDecodesLikeTheBaselineOne() throws IOException {
		assertSameDecoding(noise(37, 29, BufferedImage.TYPE_BYTE_GRAY, 1), 0.8f);
	}

	@Test
	void progressiveColorImageDecodesLikeTheBaselineOne() throws IOException {
		assertSameDecoding(noise(50, 41, BufferedImage.TYPE_INT_RGB, 2), 0.9f);
		assertSameDecoding(noise(64, 64, BufferedImage.TYPE_INT_RGB, 3), 0.5f);
	}
}
