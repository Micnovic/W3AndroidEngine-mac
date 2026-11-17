package com.etheller.warsmash.viewer5;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.etheller.warsmash.viewer5.gl.DataTexture;
import com.etheller.warsmash.common.FetchDataTypeName;
import com.etheller.warsmash.common.LoadGenericCallback;
import com.etheller.warsmash.datasources.AssetPathFallback;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.DataErrors;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.gl.ClientBuffer;
import com.etheller.warsmash.viewer5.gl.WebGL;
import com.etheller.warsmash.viewer5.handlers.ResourceHandler;
import com.etheller.warsmash.viewer5.handlers.ResourceHandlerConstructionParams;

public abstract class ModelViewer {
	public DataSource dataSource;
	public final CanvasProvider canvas;
	public List<Resource> resources;
	public Map<String, Resource> fetchCache;
	public int frameTime;
	public GL20 gl;
	public WebGL webGL;
	public List<Scene> scenes;
	private int visibleCells;
	private int visibleInstances;
	private int updatedParticles;
	public int frame;
	public final int rectBuffer;
	public ClientBuffer buffer;
	public boolean audioEnabled;
	private final Map<Model, List<TextureMapper>> textureMappers;
	private final Set<ResourceHandler> handlers;
	private final List<DataTexture> dataTextures = new ArrayList<>();
	/** The largest width and height of a model texture, 0 for no limit; see {@link #setTextureSizeLimit(int)}. */
	private int textureSizeLimit;
	private boolean textureCompression;
	private long textureBytes;
	private boolean deferTextureLoads;
	private final Deque<DeferredTexture> deferredTextureLoads = new ArrayDeque<>();

	public ModelViewer(final DataSource dataSource, final CanvasProvider canvas) {
		this.dataSource = dataSource;
		this.canvas = canvas;
		this.resources = new ArrayList<>();
		this.fetchCache = new HashMap<>();
		this.handlers = new HashSet<ResourceHandler>();
		this.frameTime = 1000 / 60;
		this.gl = Gdx.gl;
		this.webGL = new WebGL(this.gl);
		this.scenes = new ArrayList<>();
		this.visibleCells = 0;
		this.visibleInstances = 0;
		this.updatedParticles = 0;

		this.frame = 0;

		this.rectBuffer = this.gl.glGenBuffer();
		this.buffer = new ClientBuffer(this.gl);
		this.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, this.rectBuffer);
		final ByteBuffer temp = ByteBuffer.allocateDirect(6).order(ByteOrder.nativeOrder());
		temp.put((byte) 0);
		temp.put((byte) 1);
		temp.put((byte) 2);
		temp.put((byte) 0);
		temp.put((byte) 2);
		temp.put((byte) 3);
		temp.clear();
		this.gl.glBufferData(GL20.GL_ARRAY_BUFFER, temp.capacity(), temp, GL20.GL_STATIC_DRAW);
		this.audioEnabled = false;
		this.textureMappers = new HashMap<Model, List<TextureMapper>>();
	}

	/**
	 * Model and terrain textures larger than this are loaded from their first stored mip level within it (0 for no
	 * limit). Phones decode the block compressed images of the game data into plain RGBA, where one 2048 pixel HD
	 * texture alone takes 16 MB.
	 */
	public void setTextureSizeLimit(final int textureSizeLimit) {
		this.textureSizeLimit = Math.max(0, textureSizeLimit);
	}

	/**
	 * Whether the model textures are uploaded as ETC2 blocks, a quarter to a half of the memory of plain RGBA, see
	 * {@link com.etheller.warsmash.viewer5.handlers.blp.TextureTranscoder}. The caller makes sure the GPU reads them.
	 */
	public void setTextureCompression(final boolean textureCompression) {
		this.textureCompression = textureCompression;
	}

	/**
	 * While set, a texture file is read when its model asks for it but decoded and compressed later, on helper threads,
	 * and uploaded by {@link #loadNextDeferredTexture()}: decoding and compressing the textures of one model can take
	 * seconds, and a map's loading hands the frame back between them.
	 *
	 * @return the setting before
	 */
	public boolean setDeferTextureLoads(final boolean defer) {
		final boolean before = this.deferTextureLoads;
		this.deferTextureLoads = defer;
		return before;
	}

	/**
	 * Queues the rest of a texture's loading when deferring; false when it is to be done now.
	 *
	 * @param prepare runs on a helper thread and may not touch OpenGL or anything the game thread uses
	 * @param finish  runs on the game thread after prepare, in a frame of its own
	 */
	public boolean deferTextureLoad(final Runnable prepare, final Runnable finish) {
		if (!this.deferTextureLoads) {
			return false;
		}
		this.deferredTextureLoads.add(new DeferredTexture(prepare, finish));
		return true;
	}

	public boolean hasDeferredTextures() {
		return !this.deferredTextureLoads.isEmpty();
	}

	/**
	 * Finishes the first queued texture whose preparation is done, and starts the preparation of the next few; when
	 * none is done it waits a moment for the first.
	 */
	public void loadNextDeferredTexture() {
		final int window = TextureWorkers.WINDOW;
		int inFlight = 0;
		for (final DeferredTexture texture : this.deferredTextureLoads) {
			if (inFlight++ >= window) {
				break;
			}
			if (texture.future == null) {
				texture.future = TextureWorkers.EXECUTOR.submit(texture.prepare);
			}
		}
		inFlight = 0;
		for (final Iterator<DeferredTexture> iterator = this.deferredTextureLoads.iterator(); iterator.hasNext()
				&& (inFlight++ < window);) {
			final DeferredTexture texture = iterator.next();
			if (texture.future.isDone()) {
				iterator.remove();
				texture.finish.run();
				return;
			}
		}
		try {
			this.deferredTextureLoads.peekFirst().future.get(2, TimeUnit.MILLISECONDS);
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		catch (final java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
			// a failed preparation is seen as an unprepared texture by its finish
		}
	}

	private static final class DeferredTexture {
		private final Runnable prepare;
		private final Runnable finish;
		private Future<?> future;

		private DeferredTexture(final Runnable prepare, final Runnable finish) {
			this.prepare = prepare;
			this.finish = finish;
		}
	}

	/**
	 * The threads that decode and compress textures while a map loads. Each such texture holds its decoded levels on
	 * the heap, and the encoder spreads one texture over the other cores itself, so only a few are at work at once.
	 */
	private static final class TextureWorkers {
		static final int THREADS = Runtime.getRuntime().maxMemory() < (768L << 20) ? 1 : 2;
		static final int WINDOW = THREADS + 2;
		static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(THREADS, runnable -> {
			final Thread thread = new Thread(runnable, "texture-prepare");
			thread.setDaemon(true);
			thread.setPriority(Thread.NORM_PRIORITY - 1);
			return thread;
		});
	}

	public boolean isTextureCompression() {
		return this.textureCompression;
	}

	public int getTextureSizeLimit() {
		return this.textureSizeLimit;
	}

	/** Counts the texture memory this viewer's textures hold, as uploaded (all mip levels). */
	public void addTextureBytes(final long bytes) {
		this.textureBytes += bytes;
	}

	public long getTextureBytes() {
		return this.textureBytes;
	}

	public void setDataSource(final DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public boolean enableAudio() {
		this.audioEnabled = true;
		return this.audioEnabled;
	}

	public boolean addHandler(ResourceHandler handler) {
		if (handler != null) {

			// Allow to pass also the handler's module for convenience.
			if (handler.handler != null) {
				handler = handler.handler;
			}

			if (!this.handlers.contains(handler)) {
				// Check if the handler has a loader, and if so load it.
				if (handler.load && !handler.load(this)) {
					onResourceLoadError();
					return false;
				}

				this.handlers.add(handler);

				return true;
			}
		}
		return false;
	}

	public Scene addSimpleScene() {
		final Scene scene = new SimpleScene(this, createLightManager(true));

		this.scenes.add(scene);

		return scene;
	}

	public WorldScene addWorldScene() {
		final WorldScene scene = new WorldScene(this, createLightManager(false));

		this.scenes.add(scene);

		return scene;
	}

	public boolean removeScene(final Scene scene) {
		return this.scenes.remove(scene);
	}

	public void trackDataTexture(final DataTexture texture) {
		this.dataTextures.add(texture);
	}

	public void dispose() {
		for (final Resource resource : this.resources) {
			resource.dispose();
		}
		this.resources.clear();
		this.fetchCache.clear();
		for (final Scene scene : this.scenes) {
			scene.dispose();
		}
		this.scenes.clear();
		for (final DataTexture texture : this.dataTextures) {
			texture.dispose();
		}
		this.dataTextures.clear();
		this.gl.glDeleteBuffer(this.rectBuffer);
		this.buffer.dispose();
		this.webGL.dispose();
	}

	public void clear() {
		this.scenes.clear();
	}

	public Object[] findHandler(final String ext) {
		for (final ResourceHandler handler : this.handlers) {
			for (final String[] extension : handler.extensions) {
				if (extension[0].equals(ext)) {
					return new Object[] { handler, extension[1] };
				}
			}
		}
		return null;
	}

	public Resource load(final String src, final PathSolver pathSolver, final Object solverParams) {
		String finalSrc = src;
		String extension = "";
		boolean isFetch = false;

		// If a given path solver, resolve.
		if (pathSolver != null) {
			final SolvedPath solved = pathSolver.solve(src, solverParams);

			finalSrc = solved.getFinalSrc();
			if (!this.dataSource.has(finalSrc)) {
				final String fallback = AssetPathFallback.find(this.dataSource, finalSrc);
				if (fallback != null) {
					finalSrc = fallback;
				}
				else {
					DataErrors.report(DataErrors.MISSING_ASSET, finalSrc, "Attempting to load non-existant file: " + finalSrc);
				}
			}
			extension = solved.getExtension();
			if (finalSrc.length() > 4 && finalSrc.regionMatches(true, finalSrc.length() - 4, ".blp", 0, 4)
					&& finalSrc.equals(solved.getFinalSrc())) {
				// The latest data source with either name decides, so a .dds in an override folder wins over the
				// game's .blp; within one source the .blp stays first.
				final String ddsPath = finalSrc.substring(0, finalSrc.length() - 4) + ".dds";
				final String found = this.dataSource.findFirst(finalSrc, ddsPath);
				if (ddsPath.equals(found)) {
					finalSrc = ddsPath;
				}
			}
			if (!finalSrc.equals(solved.getFinalSrc())) {
				// Found under another name (a .dds beside it or in a graphics package), so it is read as that file's type.
				extension = finalSrc.substring(finalSrc.lastIndexOf('.'));
			}
			isFetch = solved.isFetch();

			if (!(extension instanceof String)) {
				throw new IllegalStateException("The path solver did not return an extension!");
			}

			if (extension.charAt(0) != '.') {
				extension = '.' + extension;
			}
			// Built-in texture sources
			// ---- TODO not using JS code here

			final Object[] handlerAndDataType = this.findHandler(extension.toLowerCase(Locale.ROOT));

			// Is there a handler for this file type?
			if (handlerAndDataType != null) {
				if (isFetch) {
					final Resource resource = this.fetchCache.get(finalSrc);

					if (resource != null) {
						return resource;
					}
				}

				final ResourceHandler handler = (ResourceHandler) handlerAndDataType[0];
				final Resource resource = handler.construct(new ResourceHandlerConstructionParams(this, handler,
						extension, pathSolver, isFetch ? finalSrc : ""));

				this.resources.add(resource);

//				if (isFetch) {
				this.fetchCache.put(finalSrc, resource);
//				}

				// TODO this is a synchronous hack, skipped some Ghostwolf code
				try {
					resource.loadData(this.dataSource.getResourceAsStream(finalSrc), null);
				}
				catch (final Exception e) {
					throw new IllegalStateException("Unable to load data: " + finalSrc, e);
				}

				return resource;
			}
			else {
				throw new IllegalStateException("Missing handler for: " + finalSrc);
			}
		}
		else {
			throw new IllegalStateException(
					"Could not resolve " + finalSrc + ". Did you forget to pass a path solver?");
		}

	}

	public boolean has(final String key) {
		return this.fetchCache.containsKey(key);
	}

	public Resource get(final String key) {
		return this.fetchCache.get(key);
	}

	public GenericResource loadGeneric(final String path, final FetchDataTypeName dataType,
			final LoadGenericCallback callback) {
		return loadGeneric(path, dataType, callback, this.dataSource);
	}

	/**
	 * Load something generic.
	 *
	 * Unlike load(), this does not use handlers or construct any internal objects.
	 *
	 * `dataType` can be one of: `"image"`, `"string"`, `"arrayBuffer"`, `"blob"`.
	 *
	 * If `callback` isn't given, the resource's `data` is the fetch data, according
	 * to `dataType`.
	 *
	 * If `callback` is given, the resource's `data` is the value returned by it
	 * when called with the fetch data.
	 *
	 * If `callback` returns a promise, the resource's `data` will be whatever the
	 * promise resolved to.
	 */
	public GenericResource loadGeneric(final String path, final FetchDataTypeName dataType,
			final LoadGenericCallback callback, final DataSource dataSource) {
		final Resource cachedResource = this.fetchCache.get(path);

		if (cachedResource != null) {
			// Technically also non-generic resources can be returned here, since the fetch
			// cache is shared.
			// That being said, this should be used for generic resources, and it makes the
			// typing a lot easier.
			return (GenericResource) cachedResource;
		}

		final GenericResource resource = new GenericResource(this, null, null, path, callback);

		this.resources.add(resource);
		this.fetchCache.put(path, resource);

		// TODO this is a synchronous hack, skipped some Ghostwolf code
		try {
			resource.loadData(dataSource.getResourceAsStream(path), null);
		}
		catch (final IOException e) {
			throw new IllegalStateException("Unable to load data: " + path);
		}

		return resource;

	}

	public void updateAndRender() {
		this.update();
		this.startFrame();
		this.render();
	}

//	public Resource loadGeneric(String path, String dataType, )

	public boolean unload(final Resource resource) {
		// TODO Auto-generated method stub
		final String fetchUrl = resource.fetchUrl;
		if (!"".equals(fetchUrl)) {
			this.fetchCache.remove(fetchUrl);
		}
		return this.resources.remove(resource);
	}

	public void update() {
		update(Gdx.graphics.getRawDeltaTime());
	}

	public void update(final float dt) {
		this.frame += 1;

		this.visibleCells = 0;
		this.visibleInstances = 0;
		this.updatedParticles = 0;

		for (final Scene scene : this.scenes) {
			scene.update(dt);

			this.visibleCells += scene.visibleCells;
			this.visibleInstances += scene.visibleInstances;
			this.updatedParticles += scene.updatedParticles;
		}
	}

	public void startFrame() {
		Gdx.gl.glScissor(0, 0, (int) this.canvas.getWidth(), (int) this.canvas.getHeight());
		this.gl.glDepthMask(true);
		// The scenes set their own clear colours: the bars beside a letterboxed view must not show the last of them.
		this.gl.glClearColor(0, 0, 0, 1);
		this.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
//		WarsmashGdxGame.bindDefaultVertexArray();
	}

	public void render() {
		for (final Scene scene : this.scenes) {
			scene.startFrame();
			scene.renderOpaque();
			scene.renderTranslucent();
		}
	}

	public TextureMapper baseTextureMapper(final ModelInstance instance) {
		final Model model = instance.model;
		List<TextureMapper> mappers = this.textureMappers.get(model);
		if (mappers == null) {
			mappers = new ArrayList<>();
			this.textureMappers.put(model, mappers);
		}
		if (mappers.isEmpty()) {
			mappers.add(new TextureMapper(model));
		}
		return mappers.get(0);
	}

	public TextureMapper changeTextureMapper(final ModelInstance instance, final Object key, final Texture texture) {
		final Map<Object, Texture> map = new HashMap<>(instance.textureMapper.textures);

		if (texture instanceof Texture) { // not null?
			map.put(key, texture);
		}
		else {
			map.remove(key);
		}

		final Model model = instance.model;
		final List<TextureMapper> mappers = this.textureMappers.get(model);

		for (final TextureMapper mapper : mappers) {
			if (mapper.textures.equals(map)) {
				return mapper;
			}
		}

		final TextureMapper mapper = new TextureMapper(model, map);

		mappers.add(mapper);

		return mapper;
	}

	private void onResourceLoadError() {
		Log.error("error, this, InvalidHandler, FailedToLoad");
	}

	public abstract SceneLightManager createLightManager(boolean simple);
}
