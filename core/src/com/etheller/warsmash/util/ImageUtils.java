package com.etheller.warsmash.util;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.etheller.warsmash.util.SessionResources;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.viewer5.handlers.blp.BlpDecoder;
import com.etheller.warsmash.viewer5.handlers.blp.DdsDecoder;
import com.etheller.warsmash.viewer5.handlers.tga.TgaFile;

/**
 * Uses AWT stuff
 *
 */
public final class ImageUtils {
	private static final int BYTES_PER_PIXEL = 4;
	/**
	 * False on platforms without javax.imageio (Android), where formats that only
	 * the ImageIO plugins decode are skipped instead of failing.
	 */
	public static final boolean IMAGE_IO_AVAILABLE = isClassPresent("javax.imageio.ImageIO");
	public static final String DEFAULT_ICON_PATH = "ReplaceableTextures\\CommandButtons\\BTNTemp.blp";

	public static Texture getAnyExtensionTexture(final DataSource dataSource, final String path) {
		BufferedImage image;
		if ((recordedTextures != null) && (Thread.currentThread() == recordingThread)) {
			recordedTextures.add(path);
		}
		final Prefetch prefetched = takePrefetched(dataSource, path);
		if (prefetched != null) {
			try {
				final RgbaImage ready = prefetched.decoded;
				cleanConsoleTileEdges(path, ready.getWidth(), ready.getHeight(), ready.getPixels());
				return getTexture(ready.getWidth(), ready.getHeight(), ready.getPixels());
			}
			finally {
				prefetched.release();
			}
		}
		try {
			final DdsDecoder ddsImage = findDdsInsteadOfBlp(dataSource, path);
			if (ddsImage != null) {
				cleanConsoleTileEdges(path, ddsImage.getWidth(), ddsImage.getHeight(), ddsImage.getRgba());
				return getTexture(ddsImage.getWidth(), ddsImage.getHeight(), ddsImage.getRgba());
			}
			final BlpDecoder blpImage = findDecodableBlp(dataSource, path);
			if (blpImage != null) {
				// Raw palette colors in a plain texture match the ImageIO path, which reads
				// them as linear and then uploads with an sRGB format.
				cleanConsoleTileEdges(path, blpImage.getWidth(), blpImage.getHeight(), blpImage.getRgba());
				return getTexture(blpImage.getWidth(), blpImage.getHeight(), blpImage.getRgba());
			}
			final ArgbImage tgaImage = findTgaInsteadOfBlp(dataSource, path);
			if (tgaImage != null) {
				return getTextureNoColorCorrection(tgaImage);
			}
			if (!IMAGE_IO_AVAILABLE && path.toLowerCase(Locale.ROOT).endsWith(".blp") && dataSource.has(path)) {
				Log.warn("No decoder for " + path + " on this platform");
				return null;
			}
			final AnyExtensionImage imageInfo = getAnyExtensionImageFixRGB(dataSource, path, "texture");
			image = imageInfo.getImageData();
			if (image != null) {
				return ImageUtils.getTexture(image, imageInfo.isNeedsSRGBFix());
			}
		}
		catch (final IOException e) {
			return null;
		}
		return null;
	}

	private static final Pattern CONSOLE_TILE = Pattern
			.compile("ui[\\\\/]console[\\\\/][^\\\\/]+[\\\\/][^\\\\/]*uitile0\\d\\.[a-z]+", Pattern.CASE_INSENSITIVE);

	/**
	 * The console tiles are drawn side by side, and the outermost pixel column of each carries a faint alpha even where
	 * the picture is open (the portrait, the minimap), which shows as a dark line along the joint. The column takes the
	 * values of its neighbour, so the tile keeps its size and the open parts stay open.
	 */
	private static void cleanConsoleTileEdges(final String path, final int width, final int height,
			final ByteBuffer rgba) {
		if ((width < 4) || rgba.isReadOnly() || !CONSOLE_TILE.matcher(path).matches()) {
			return;
		}
		final int base = rgba.position();
		for (int y = 0; y < height; y++) {
			final int row = base + (y * width * 4);
			for (int k = 0; k < 4; k++) {
				rgba.put(row + k, rgba.get(row + 4 + k));
				rgba.put(row + ((width - 1) * 4) + k, rgba.get(row + ((width - 2) * 4) + k));
			}
		}
	}

	private static final String UI_TEXTURE_LIST = "ui-textures.lst";
	private static final int MAX_UI_TEXTURES = 400;
	private static final Map<String, Prefetch> PREFETCHED = new ConcurrentHashMap<>();
	private static ExecutorService prefetchExecutor;
	/** The paths textured during the game screen's setup, on the thread that began it; null outside it. */
	private static Set<String> recordedTextures;
	private static Thread recordingThread;

	private static final class Prefetch {
		private final DataSource dataSource;
		private Future<RgbaImage> future;
		/** The decoded pixels; kept outside the Java heap on Android, where this holds tens of megabytes. */
		private RgbaImage decoded;
		private OffHeapBlock pixels;
		private boolean released;

		private Prefetch(final DataSource dataSource) {
			this.dataSource = dataSource;
		}

		/** Keeps the decoded image, unless this prefetch is already let go. */
		private synchronized RgbaImage keep(final RgbaImage image) {
			if (this.released || (image == null)) {
				return null;
			}
			final ByteBuffer source = image.getPixels().duplicate();
			this.pixels = OffHeapBlock.allocate(source.remaining());
			this.pixels.buffer().put(source).clear();
			this.decoded = new RgbaImage(image.getWidth(), image.getHeight(), this.pixels.buffer(), false);
			return this.decoded;
		}

		/** Waits for the decoding; false when it failed or was cancelled. */
		private boolean await() {
			try {
				return this.future.get() != null;
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
			catch (final Exception e) {
				return false;
			}
		}

		private synchronized void release() {
			this.released = true;
			this.decoded = null;
			if (this.pixels != null) {
				this.pixels.free();
				this.pixels = null;
			}
		}
	}

	/**
	 * The paths the last game screen's setup textured, which the loading screen of the next game decodes ahead.
	 * Empty before the first game or where the file cannot be read.
	 */
	public static List<String> readUiTextureList() {
		final List<String> paths = new ArrayList<>();
		try {
			if ((Gdx.files != null) && Gdx.files.local(UI_TEXTURE_LIST).exists()) {
				for (final String line : Gdx.files.local(UI_TEXTURE_LIST).readString("UTF-8").split("\n")) {
					final String path = line.trim();
					if (!path.isEmpty() && (paths.size() < MAX_UI_TEXTURES)) {
						paths.add(path);
					}
				}
			}
		}
		catch (final RuntimeException e) {
			// Nothing is decoded ahead; the screen's setup loads them itself.
		}
		return paths;
	}

	/** Starts recording the texture paths loaded from here until {@link #endUiTextureRecording()}. */
	public static void beginUiTextureRecording() {
		recordingThread = Thread.currentThread();
		recordedTextures = new LinkedHashSet<>();
	}

	/** Saves the recorded paths for the next game's loading screen and drops the textures decoded ahead and unused. */
	public static void endUiTextureRecording() {
		final Set<String> recorded = recordedTextures;
		recordedTextures = null;
		dropPrefetched();
		if ((recorded == null) || recorded.isEmpty() || (Gdx.files == null)) {
			return;
		}
		try {
			final StringBuilder text = new StringBuilder();
			int count = 0;
			for (final String path : recorded) {
				if (count++ == MAX_UI_TEXTURES) {
					break;
				}
				text.append(path).append('\n');
			}
			final String content = text.toString();
			final com.badlogic.gdx.files.FileHandle file = Gdx.files.local(UI_TEXTURE_LIST);
			if (!file.exists() || !content.equals(file.readString("UTF-8"))) {
				file.writeString(content, false, "UTF-8");
			}
		}
		catch (final RuntimeException e) {
			// The next game decodes nothing ahead.
		}
	}

	/**
	 * Reads the file on the calling thread, as data sources are not for other threads, and decodes it on another one
	 * so that the getAnyExtensionTexture of the same path only has to upload it. Takes the same file the lookup there
	 * does, and leaves paths that need ImageIO to it.
	 */
	public static void prefetchTexture(final DataSource dataSource, final String path) {
		if (!path.toLowerCase(Locale.ROOT).endsWith(".blp")) {
			return;
		}
		final Prefetch existing = PREFETCHED.get(path);
		if ((existing != null) && (existing.dataSource == dataSource)) {
			return;
		}
		try {
			final String basePath = path.substring(0, path.length() - 4);
			final String ddsPath = basePath + ".dds";
			final String found = dataSource.findFirst(path, basePath + ".tga", ddsPath);
			final boolean dds = ddsPath.equals(found);
			if (!dds && !path.equals(found)) {
				return;
			}
			final ByteBuffer read = readFully(dataSource, dds ? ddsPath : path);
			if ((read == null) || (!dds && !BlpDecoder.isBlp(read))) {
				return;
			}
			// The file waits for a helper outside the Java heap too; the decoded one is kept by the prefetch itself.
			final OffHeapBlock file = OffHeapBlock.copyOf(read.array());
			final Prefetch prefetch = new Prefetch(dataSource);
			prefetch.future = prefetchExecutor().submit(() -> {
				final ByteBuffer data;
				try {
					final byte[] bytes = new byte[file.buffer().capacity()];
					file.buffer().duplicate().get(bytes);
					data = ByteBuffer.wrap(bytes);
				}
				finally {
					file.free();
				}
				if (dds) {
					final DdsDecoder image = DdsDecoder.decode(data, 0, false);
					return image == null ? null
							: prefetch.keep(new RgbaImage(image.getWidth(), image.getHeight(), image.getRgba(), false));
				}
				final BlpDecoder image = BlpDecoder.decode(data, 0, false);
				return prefetch.keep(new RgbaImage(image.getWidth(), image.getHeight(), image.getRgba(), false));
			});
			PREFETCHED.put(path, prefetch);
		}
		catch (final IOException | RuntimeException e) {
			// Left to getAnyExtensionTexture, which reports it.
		}
	}

	private static synchronized ExecutorService prefetchExecutor() {
		if (prefetchExecutor == null) {
			prefetchExecutor = Executors.newFixedThreadPool(2, runnable -> {
				final Thread thread = new Thread(runnable, "Texture prefetch");
				thread.setDaemon(true);
				thread.setPriority(Thread.MIN_PRIORITY + 1);
				return thread;
			});
		}
		return prefetchExecutor;
	}

	private static Prefetch takePrefetched(final DataSource dataSource, final String path) {
		if (PREFETCHED.isEmpty()) {
			return null;
		}
		final Prefetch prefetch = PREFETCHED.get(path);
		if ((prefetch == null) || (prefetch.dataSource != dataSource)) {
			return null;
		}
		PREFETCHED.remove(path);
		if (!prefetch.await()) {
			prefetch.release();
			return null;
		}
		return prefetch;
	}

	/** Lets go of what was decoded ahead and never asked for. */
	public static void dropPrefetched() {
		for (final Prefetch prefetch : PREFETCHED.values()) {
			prefetch.future.cancel(false);
			prefetch.release();
		}
		PREFETCHED.clear();
	}

	public static AnyExtensionImage getAnyExtensionImageFixRGB(final DataSource dataSource, final String path,
			final String errorType) throws IOException {
		if (path.toLowerCase(Locale.ROOT).endsWith(".blp")) {
			try (InputStream stream = dataSource.getResourceAsStream(path)) {
				if (stream == null) {
					final String tgaPath = path.substring(0, path.length() - 4) + ".tga";
					try (final InputStream tgaStream = dataSource.getResourceAsStream(tgaPath)) {
						if (tgaStream != null) {
							final BufferedImage tgaData = TgaFile.readTGA(tgaPath, tgaStream);
							return new AnyExtensionImage(false, tgaData);
						}
						else {
							final String ddsPath = path.substring(0, path.length() - 4) + ".dds";
							try (final InputStream ddsStream = dataSource.getResourceAsStream(ddsPath)) {
								if (ddsStream != null) {
									final BufferedImage image = ImageIO.read(ddsStream);
									return new AnyExtensionImage(false, image);
								}
								else {
									throw new IllegalStateException("Missing " + errorType + ": " + path);
								}
							}
						}
					}
				}
				else {
					final BufferedImage image = ImageIO.read(stream);
					return new AnyExtensionImage(true, image);
				}
			}
		}
		else {
			throw new IllegalStateException("Missing " + errorType + ": " + path);
		}
	}

	/**
	 * The TGA a BLP path resolves to, decoded without java.awt (a map's preview picture is often a TGA, and Android
	 * has no BufferedImage); null when the path resolves to something else.
	 */
	private static ArgbImage findTgaInsteadOfBlp(final DataSource dataSource, final String path) throws IOException {
		if (!path.toLowerCase(Locale.ROOT).endsWith(".blp")) {
			return null;
		}
		final String tgaPath = path.substring(0, path.length() - 4) + ".tga";
		if (!tgaPath.equals(dataSource.findFirst(path, tgaPath))) {
			return null;
		}
		try (InputStream stream = dataSource.getResourceAsStream(tgaPath)) {
			return stream == null ? null : TgaFile.readTGAPixels(tgaPath, stream);
		}
	}

	/**
	 * Follows the lookup order of getAnyExtensionImageFixRGB (BLP, then TGA, then
	 * DDS) and decodes the DDS without java.awt when that is what the path resolves
	 * to.
	 */
	private static DdsDecoder findDdsInsteadOfBlp(final DataSource dataSource, final String path)
			throws IOException {
		return findDdsInsteadOfBlp(dataSource, path, 0);
	}

	private static DdsDecoder findDdsInsteadOfBlp(final DataSource dataSource, final String path, final int maxSize)
			throws IOException {
		if (!path.toLowerCase(Locale.ROOT).endsWith(".blp")) {
			return null;
		}
		final String basePath = path.substring(0, path.length() - 4);
		final String ddsPath = basePath + ".dds";
		// In one data source the order above holds; a later source (an override folder) wins with any of the three.
		final String found = dataSource.findFirst(path, basePath + ".tga", ddsPath);
		if ((found != null) && !found.equals(ddsPath)) {
			return null;
		}
		final ByteBuffer ddsData = readFully(dataSource, ddsPath);
		return ddsData == null ? null : DdsDecoder.decode(ddsData, maxSize, false);
	}

	/**
	 * Reads through getResourceAsStream, which unlike read also sees the files inside
	 * a map archive.
	 */
	private static ByteBuffer readFully(final DataSource dataSource, final String path) throws IOException {
		try (InputStream stream = dataSource.getResourceAsStream(path)) {
			return stream == null ? null : ByteBuffer.wrap(StreamBytes.readAll(stream));
		}
	}

	private static boolean isClassPresent(final String className) {
		try {
			Class.forName(className, false, ImageUtils.class.getClassLoader());
			return true;
		}
		catch (final ClassNotFoundException e) {
			return false;
		}
	}

	private static BlpDecoder findDecodableBlp(final DataSource dataSource, final String path) throws IOException {
		return findDecodableBlp(dataSource, path, 0);
	}

	private static BlpDecoder findDecodableBlp(final DataSource dataSource, final String path, final int maxSize)
			throws IOException {
		if (!path.toLowerCase(Locale.ROOT).endsWith(".blp")) {
			return null;
		}
		final ByteBuffer blpData = readFully(dataSource, path);
		if (blpData == null) {
			return null;
		}
		return BlpDecoder.isBlp(blpData) ? BlpDecoder.decode(blpData, maxSize, false) : null;
	}

	/**
	 * @param rgba width * height RGBA pixels, rows from the top
	 */
	public static Texture getTexture(final int width, final int height, final ByteBuffer rgba) {
		final Pixmap pixmap = new Pixmap(width, height, Format.RGBA8888);
		final ByteBuffer pixels = pixmap.getPixels();
		pixels.position(0);
		pixels.put(rgba.duplicate());
		pixels.position(0);
		// An unmanaged texture has its pixels on the GPU once constructed; only the native copy would stay behind.
		final Texture texture = SessionResources.track(new Texture(pixmap));
		pixmap.dispose();
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		return texture;
	}

	/**
	 * Like getAnyExtensionImageFixRGB, but returns RGBA bytes and decodes DDS and BLP without
	 * java.awt.
	 *
	 * @param sRGBCorrect whether to apply the sRGB correction of
	 *                    AnyExtensionImage.getRGBCorrectImageData
	 */
	public static RgbaImage getAnyExtensionRgbaImage(final DataSource dataSource, final String path,
			final String errorType, final boolean sRGBCorrect) throws IOException {
		return getAnyExtensionRgbaImage(dataSource, path, errorType, sRGBCorrect, 0);
	}

	/**
	 * @param maxSize the largest width and height, 0 for no limit: a BLP or DDS larger than that is read from its first
	 *                stored mip level within it
	 */
	public static RgbaImage getAnyExtensionRgbaImage(final DataSource dataSource, final String path,
			final String errorType, final boolean sRGBCorrect, final int maxSize) throws IOException {
		final DdsDecoder ddsImage = findDdsInsteadOfBlp(dataSource, path, maxSize);
		if (ddsImage != null) {
			return new RgbaImage(ddsImage.getWidth(), ddsImage.getHeight(), ddsImage.getRgba(), false);
		}
		final BlpDecoder blpImage = findDecodableBlp(dataSource, path, maxSize);
		if (blpImage != null) {
			// The raw colors in a plain RGBA texture match both ImageIO variants: the
			// sRGB corrected one keeps them, the other is uploaded with an sRGB format.
			return new RgbaImage(blpImage.getWidth(), blpImage.getHeight(), blpImage.getRgba(), false);
		}
		final AnyExtensionImage imageInfo = getAnyExtensionImageFixRGB(dataSource, path, errorType);
		final BufferedImage image = sRGBCorrect ? imageInfo.getRGBCorrectImageData() : imageInfo.getImageData();
		return new RgbaImage(image.getWidth(), image.getHeight(), (ByteBuffer) getTextureBuffer(image),
				imageInfo.isNeedsSRGBFix());
	}

	public static final class RgbaImage {
		private final int width;
		private final int height;
		private final ByteBuffer pixels;
		private final boolean needsSRGBFix;

		public RgbaImage(final int width, final int height, final ByteBuffer pixels, final boolean needsSRGBFix) {
			this.width = width;
			this.height = height;
			this.pixels = pixels;
			this.needsSRGBFix = needsSRGBFix;
		}

		public int getWidth() {
			return this.width;
		}

		public int getHeight() {
			return this.height;
		}

		/**
		 * @return width * height RGBA pixels, rows from the top
		 */
		public ByteBuffer getPixels() {
			return this.pixels;
		}

		public boolean isNeedsSRGBFix() {
			return this.needsSRGBFix;
		}
	}

	public static final class AnyExtensionImage {
		private final boolean needsSRGBFix;
		private final BufferedImage imageData;

		public AnyExtensionImage(final boolean needsSRGBFix, final BufferedImage imageData) {
			this.needsSRGBFix = needsSRGBFix;
			this.imageData = imageData;
		}

		public BufferedImage getImageData() {
			return this.imageData;
		}

		public BufferedImage getRGBCorrectImageData() {
			return this.needsSRGBFix ? forceBufferedImagesRGB(this.imageData) : this.imageData;
		}

		public boolean isNeedsSRGBFix() {
			return this.needsSRGBFix;
		}
	}

	public static Texture getTexture(final BufferedImage image, final boolean sRGBFix) {
		final int[] pixels = new int[image.getWidth() * image.getHeight()];
		image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

		// 4
		// for
		// RGBA,
		// 3
		// for
		// RGB

		final Pixmap pixmap = sRGBFix ? new Pixmap(image.getWidth(), image.getHeight(), Format.RGBA8888) {
			@Override
			public int getGLInternalFormat() {
				return GL30.GL_SRGB8_ALPHA8;
			}
		} : new Pixmap(image.getWidth(), image.getHeight(), Format.RGBA8888);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				final int pixel = pixels[(y * image.getWidth()) + x];
				pixmap.drawPixel(x, y, (pixel << 8) | (pixel >>> 24));
			}
		}
		// An unmanaged texture has its pixels on the GPU once constructed; only the native copy would stay behind.
		final Texture texture = SessionResources.track(new Texture(pixmap));
		pixmap.dispose();
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		return texture;
	}

	public static Texture getTextureNoColorCorrection(final BufferedImage image) {
		final int[] pixels = new int[image.getWidth() * image.getHeight()];
		image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

		// 4
		// for
		// RGBA,
		// 3
		// for
		// RGB

		final Pixmap pixmap = new Pixmap(image.getWidth(), image.getHeight(), Format.RGBA8888);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				final int pixel = pixels[(y * image.getWidth()) + x];
				pixmap.drawPixel(x, y, (pixel << 8) | (pixel >>> 24));
			}
		}
		// An unmanaged texture has its pixels on the GPU once constructed; only the native copy would stay behind.
		final Texture texture = SessionResources.track(new Texture(pixmap));
		pixmap.dispose();
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		return texture;
	}

	/**
	 * Reads a data image such as a pathing map. A BLP gives its stored values, as a TGA does; other formats need
	 * javax.imageio.
	 */
	public static ArgbImage getArgbImage(final InputStream stream, final String path) throws IOException {
		if (stream == null) {
			throw new IllegalStateException("Missing image: " + path);
		}
		final ByteBuffer data = ByteBuffer.wrap(StreamBytes.readAll(stream));
		if (BlpDecoder.isBlp(data)) {
			final BlpDecoder blp = BlpDecoder.decode(data);
			final ByteBuffer rgba = blp.getRgba();
			final int[] pixels = new int[blp.getWidth() * blp.getHeight()];
			for (int i = 0; i < pixels.length; i++) {
				final int offset = i * 4;
				pixels[i] = ((rgba.get(offset + 3) & 0xFF) << 24) | ((rgba.get(offset) & 0xFF) << 16)
						| ((rgba.get(offset + 1) & 0xFF) << 8) | (rgba.get(offset + 2) & 0xFF);
			}
			return new ArgbImage(blp.getWidth(), blp.getHeight(), pixels);
		}
		if (!IMAGE_IO_AVAILABLE) {
			throw new IllegalStateException("No decoder for " + path + " on this platform");
		}
		final BufferedImage image = ImageIO.read(new ByteArrayInputStream(data.array()));
		final int[] pixels = new int[image.getWidth() * image.getHeight()];
		image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
		return new ArgbImage(image.getWidth(), image.getHeight(), pixels);
	}

	public static Texture getTextureNoColorCorrection(final ArgbImage image) {
		final Pixmap pixmap = new Pixmap(image.getWidth(), image.getHeight(), Format.RGBA8888);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				final int pixel = image.getRGB(x, y);
				pixmap.drawPixel(x, y, (pixel << 8) | (pixel >>> 24));
			}
		}
		// An unmanaged texture has its pixels on the GPU once constructed; only the native copy would stay behind.
		final Texture texture = SessionResources.track(new Texture(pixmap));
		pixmap.dispose();
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		return texture;
	}

	public static Buffer getTextureBuffer(final BufferedImage image) {

		final int imageWidth = image.getWidth();
		final int imageHeight = image.getHeight();
		final int[] pixels = new int[imageWidth * imageHeight];
		image.getRGB(0, 0, imageWidth, imageHeight, pixels, 0, imageWidth);

		final ByteBuffer buffer = ByteBuffer.allocateDirect(imageWidth * imageHeight * BYTES_PER_PIXEL)
				.order(ByteOrder.nativeOrder());
		// 4
		// for
		// RGBA,
		// 3
		// for
		// RGB

		for (int y = 0; y < imageHeight; y++) {
			for (int x = 0; x < imageWidth; x++) {
				final int pixel = pixels[(y * imageWidth) + x];
				buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red component
				buffer.put((byte) ((pixel >> 8) & 0xFF)); // Green component
				buffer.put((byte) (pixel & 0xFF)); // Blue component
				buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha component.
				// Only for RGBA
			}
		}

		buffer.flip();
		return buffer;
	}

	/**
	 * Convert an input buffered image into sRGB color space using component values
	 * directly instead of performing a color space conversion.
	 *
	 * @param in Input image to be converted.
	 * @return Resulting sRGB image.
	 */
	public static BufferedImage forceBufferedImagesRGB(final BufferedImage in) {
		// Resolve input ColorSpace.
		final ColorSpace inCS = in.getColorModel().getColorSpace();
		final ColorSpace sRGBCS = ColorSpace.getInstance(ColorSpace.CS_sRGB);
		if (inCS == sRGBCS) {
			// Already is sRGB.
			return in;
		}
		if (inCS.getNumComponents() != sRGBCS.getNumComponents()) {
			throw new IllegalArgumentException("Input color space has different number of components from sRGB.");
		}

		// Draw input.
		final ColorModel lRGBModel = new ComponentColorModel(inCS, true, false, Transparency.TRANSLUCENT,
				DataBuffer.TYPE_BYTE);
		final ColorModel sRGBModel = new ComponentColorModel(sRGBCS, true, false, Transparency.TRANSLUCENT,
				DataBuffer.TYPE_BYTE);
		final BufferedImage lRGB = new BufferedImage(lRGBModel,
				lRGBModel.createCompatibleWritableRaster(in.getWidth(), in.getHeight()), false, null);
		for (int i = 0; i < in.getWidth(); i++) {
			for (int j = 0; j < in.getHeight(); j++) {
				lRGB.setRGB(i, j, in.getRGB(i, j));
			}
		}

		// Convert to sRGB.
		final BufferedImage sRGB = new BufferedImage(sRGBModel, lRGB.getRaster(), false, null);

		return sRGB;
	}

	private ImageUtils() {
	}
}
