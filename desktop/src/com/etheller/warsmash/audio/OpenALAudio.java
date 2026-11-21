/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.etheller.warsmash.audio;

import static org.lwjgl.openal.AL10.AL_BUFFER;
import static org.lwjgl.openal.AL10.AL_BUFFERS_QUEUED;
import static org.lwjgl.openal.AL10.AL_NO_ERROR;
import static org.lwjgl.openal.AL10.AL_ORIENTATION;
import static org.lwjgl.openal.AL10.AL_PAUSED;
import static org.lwjgl.openal.AL10.AL_PLAYING;
import static org.lwjgl.openal.AL10.AL_POSITION;
import static org.lwjgl.openal.AL10.AL_SOURCE_STATE;
import static org.lwjgl.openal.AL10.AL_STOPPED;
import static org.lwjgl.openal.AL10.AL_VELOCITY;
import static org.lwjgl.openal.AL10.alDeleteSources;
import static org.lwjgl.openal.AL10.alGenSources;
import static org.lwjgl.openal.AL10.alGetError;
import static org.lwjgl.openal.AL10.alGetSourcei;
import static org.lwjgl.openal.AL10.alListener;
import static org.lwjgl.openal.AL10.alSourcePause;
import static org.lwjgl.openal.AL10.alSourcePlay;
import static org.lwjgl.openal.AL10.alSourceStop;
import static org.lwjgl.openal.AL10.alSourcei;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.audio.AudioRecorder;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.StreamUtils;
import com.etheller.warsmash.datasources.IndependentStream;
import com.etheller.warsmash.util.DataSourceFileHandle;
import com.etheller.warsmash.util.Log;

/** @author Nathan Sweet */
public class OpenALAudio implements Audio {
	private final int deviceBufferSize;
	private final int deviceBufferCount;
	private IntArray idleSources, allSources;
	private LongMap<Integer> soundIdToSource;
	private IntMap<Long> sourceToSoundId;
	private long nextSoundId = 0;
	private final ObjectMap<String, Class<? extends OpenALSound>> extensionToSoundClass = new ObjectMap();
	private final ObjectMap<String, Class<? extends OpenALMusic>> extensionToMusicClass = new ObjectMap();
	private OpenALSound[] recentSounds;
	private int mostRecetSound = -1;

	private final ConcurrentHashMap<String, CompletableFuture<OggPcm>> prefetchedOgg = new ConcurrentHashMap<>();
	// How many more sounds than one are to be made of a decoded file: each takes the same samples.
	private final Map<String, Integer> prefetchedOggExtraTakes = new HashMap<>();
	private final ConcurrentHashMap<String, CompletableFuture<byte[]>> prefetchedWav = new ConcurrentHashMap<>();
	// The bytes each prefetched file holds on the heap, the file's own until its decoding is done and then the decoded
	// samples. What does not fit the budget is decoded when its sound is made, so that a campaign script with hundreds
	// of lines cannot fill a small heap before the game starts.
	private final ConcurrentHashMap<String, Long> prefetchedSizes = new ConcurrentHashMap<>();
	// The files of the sounds that wait for them to be read ahead.
	private final java.util.Set<String> awaited = ConcurrentHashMap.newKeySet();
	private static final long PREFETCH_BUDGET = Math.max(16L << 20,
			Math.min(128L << 20, Runtime.getRuntime().maxMemory() / 16));
	private ExecutorService prefetchExecutor;
	/**
	 * A decoded sound of more than this many bytes (an ambient loop of tens of megabytes) is uploaded to OpenAL a
	 * piece at a time over later frames: the upload is a copy that holds the game thread for milliseconds.
	 */
	private static final int SPREAD_THRESHOLD = 3 << 20;
	private static final int SPREAD_CHUNK = 1 << 20;

	Array<OpenALMusic> music = new Array(false, 1, OpenALMusic.class);
	boolean noDevice = false;

	public OpenALAudio() {
		this(16, 9, 512);
	}

	public OpenALAudio(final int simultaneousSources, final int deviceBufferCount, final int deviceBufferSize) {
		this.deviceBufferSize = deviceBufferSize;
		this.deviceBufferCount = deviceBufferCount;

		registerSound("ogg", Ogg.Sound.class);
		registerMusic("ogg", Ogg.Music.class);
		registerSound("wav", Wav.Sound.class);
		registerMusic("wav", Wav.Music.class);
		registerSound("mp3", Mp3.Sound.class);
		registerMusic("mp3", Mp3.Music.class);
		registerSound("flac", Flac.Sound.class);
		registerMusic("flac", Flac.Music.class);

		try {
			AL.create();
		}
		catch (final LWJGLException ex) {
			this.noDevice = true;
			Log.error(ex);
			return;
		}

		this.allSources = new IntArray(false, simultaneousSources);
		for (int i = 0; i < simultaneousSources; i++) {
			final int sourceID = alGenSources();
			if (alGetError() != AL_NO_ERROR) {
				break;
			}
			this.allSources.add(sourceID);
		}
		this.idleSources = new IntArray(this.allSources);
		this.soundIdToSource = new LongMap<Integer>();
		this.sourceToSoundId = new IntMap<Long>();

		final FloatBuffer orientation = BufferUtils.createFloatBuffer(6)
				.put(new float[] { 0.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f }).flip();
		alListener(AL_ORIENTATION, orientation);
		final FloatBuffer velocity = BufferUtils.createFloatBuffer(3).put(new float[] { 0.0f, 0.0f, 0.0f }).flip();
		alListener(AL_VELOCITY, velocity);
		final FloatBuffer position = BufferUtils.createFloatBuffer(3).put(new float[] { 0.0f, 0.0f, 0.0f }).flip();
		alListener(AL_POSITION, position);

		this.recentSounds = new OpenALSound[simultaneousSources];
	}

	public void registerSound(final String extension, final Class<? extends OpenALSound> soundClass) {
		if (extension == null) {
			throw new IllegalArgumentException("extension cannot be null.");
		}
		if (soundClass == null) {
			throw new IllegalArgumentException("soundClass cannot be null.");
		}
		this.extensionToSoundClass.put(extension, soundClass);
	}

	public void registerMusic(final String extension, final Class<? extends OpenALMusic> musicClass) {
		if (extension == null) {
			throw new IllegalArgumentException("extension cannot be null.");
		}
		if (musicClass == null) {
			throw new IllegalArgumentException("musicClass cannot be null.");
		}
		this.extensionToMusicClass.put(extension, musicClass);
	}

	/**
	 * Where the data source allows, a file is opened and read on the helper thread too: the opener is looked up on the
	 * calling thread, as data sources are not for other threads, and is null when the file can only be read there.
	 */
	private static IndependentStream.Opener openerOf(final FileHandle file) {
		try {
			return (file instanceof DataSourceFileHandle) ? ((DataSourceFileHandle) file).opener() : null;
		}
		catch (final RuntimeException e) {
			return null;
		}
	}

	private ExecutorService prefetchExecutor() {
		if (this.prefetchExecutor == null) {
			this.prefetchExecutor = Executors.newFixedThreadPool(2, (runnable) -> {
				final Thread thread = new Thread(runnable, "Sound prefetch");
				thread.setDaemon(true);
				return thread;
			});
		}
		return this.prefetchExecutor;
	}

	private <T> CompletableFuture<T> runPrefetch(final Callable<T> task) {
		final CompletableFuture<T> future = new CompletableFuture<>();
		prefetchExecutor().execute(() -> {
			try {
				future.complete(task.call());
			}
			catch (final Exception | LinkageError e) {
				future.completeExceptionally(e);
			}
		});
		return future;
	}

	private static byte[] readAll(final IndependentStream.Opener opener) throws IOException {
		try (InputStream stream = opener.open()) {
			return StreamUtils.copyStreamToByteArray(stream);
		}
	}

	/**
	 * Decodes an ogg, FLAC or mp3 sound on another thread so that the newSound of it later only has to give the buffer to
	 * OpenAL, which stays on the calling thread; a wav is read there. The file is read on that thread too when the data
	 * source allows, else by this call.
	 */
	public void prefetchSound(final FileHandle file) {
		if (this.noDevice || (file == null) || (prefetchedBytes() >= PREFETCH_BUDGET)) {
			return;
		}
		final IndependentStream.Opener opener = openerOf(file);
		if ("wav".equalsIgnoreCase(file.extension())) {
			// Most of a wav's newSound is reading it out of the game data (an MPQ decompresses it).
			final String path = file.path();
			if (!this.prefetchedWav.containsKey(path)) {
				if (opener != null) {
					this.prefetchedSizes.put(path, 0L);
					this.prefetchedWav.put(path, runPrefetch(() -> {
						try {
							final byte[] bytes = readAll(opener);
							this.prefetchedSizes.replace(path, (long) bytes.length);
							return bytes;
						}
						catch (final IOException | RuntimeException e) {
							this.prefetchedSizes.remove(path);
							throw e;
						}
					}));
					return;
				}
				try {
					final byte[] bytes = file.readBytes();
					this.prefetchedWav.put(path, CompletableFuture.completedFuture(bytes));
					this.prefetchedSizes.put(path, (long) bytes.length);
				}
				catch (final RuntimeException e) {
					// Left to newSound, which reports it.
				}
			}
			return;
		}
		final boolean flac = "flac".equalsIgnoreCase(file.extension());
		final boolean mp3 = "mp3".equalsIgnoreCase(file.extension());
		if (!flac && !mp3 && !"ogg".equalsIgnoreCase(file.extension())) {
			return;
		}
		if (this.prefetchedOgg.containsKey(file.path())) {
			this.prefetchedOggExtraTakes.merge(file.path(), 1, Integer::sum);
			return;
		}
		byte[] readHere = null;
		if (opener == null) {
			try {
				readHere = file.readBytes();
			}
			catch (final RuntimeException e) {
				return;
			}
		}
		final byte[] given = readHere;
		final String path = file.path();
		this.prefetchedSizes.put(path, given != null ? given.length : 0L);
		this.prefetchedOgg.put(path, runPrefetch(() -> {
			try {
				final byte[] bytes = given != null ? given : readAll(opener);
				final java.io.ByteArrayInputStream stream = new java.io.ByteArrayInputStream(bytes);
				final OggPcm pcm = flac ? Flac.decode(bytes) : mp3 ? Mp3.decode(stream) : Ogg.decode(stream);
				pcm.prepare();
				this.prefetchedSizes.replace(path, (long) pcm.size());
				return pcm;
			}
			catch (final IOException | RuntimeException e) {
				this.prefetchedSizes.remove(path);
				throw e;
			}
		}));
	}

	/**
	 * Makes a sound without holding the game thread up for reading and decoding its file: those are done on the
	 * helper thread, and the sound is given to the callback on the game thread in a later frame (null when it could
	 * not be made).
	 *
	 * @return false when the sound is to be made with {@link #newSound} now: it is already read ahead, or its file can
	 *         only be read on the game thread
	 */
	public boolean newSoundLater(final FileHandle file, final Consumer<? super OpenALSound> ready) {
		if (this.noDevice || (file == null) || (Gdx.app == null)) {
			return false;
		}
		final String extension = file.extension().toLowerCase();
		final boolean wav = "wav".equals(extension);
		if (!wav && !"ogg".equals(extension) && !"flac".equals(extension) && !"mp3".equals(extension)) {
			return false;
		}
		final String path = file.path();
		CompletableFuture<?> pending = wav ? this.prefetchedWav.get(path) : this.prefetchedOgg.get(path);
		if (pending == null) {
			if ((openerOf(file) == null) || (prefetchedBytes() >= PREFETCH_BUDGET)) {
				return false;
			}
			prefetchSound(file);
			pending = wav ? this.prefetchedWav.get(path) : this.prefetchedOgg.get(path);
		}
		if ((pending == null) || (pending.isDone() && !(!wav && isSpread(path)))) {
			return false;
		}
		this.awaited.add(path);
		pending.whenComplete((result, failure) -> Gdx.app.postRunnable(() -> {
			this.awaited.remove(path);
			OpenALSound sound = null;
			try {
				if (!wav && isSpread(path)) {
					newSoundSpread(file, ready);
					return;
				}
				sound = newSound(file);
			}
			catch (final RuntimeException e) {
				Log.error(e);
			}
			ready.accept(sound);
		}));
		return true;
	}

	/** Whether the sound of this file, decoded ahead, is large enough to be uploaded a piece at a time. */
	private boolean isSpread(final String path) {
		final Long size = this.prefetchedSizes.get(path);
		return (size != null) && (size > SPREAD_THRESHOLD) && !this.prefetchedOggExtraTakes.containsKey(path);
	}

	/** Makes the sound of a large decoded file, a piece uploaded in each frame, and gives it to the callback when whole. */
	private void newSoundSpread(final FileHandle file, final Consumer<? super OpenALSound> ready) {
		final OggPcm pcm = takePrefetched(file);
		if (pcm == null) {
			// a sound made meanwhile by a play took it; decoding the file again here would be the stall avoided
			ready.accept(null);
			return;
		}
		final OpenALSound sound = new OpenALSound(this);
		sound.beginSpread(pcm, SPREAD_CHUNK);
		uploadSpread(sound, ready);
	}

	private void uploadSpread(final OpenALSound sound, final Consumer<? super OpenALSound> ready) {
		if (sound.uploadNextChunk()) {
			ready.accept(sound);
		}
		else {
			Gdx.app.postRunnable(() -> uploadSpread(sound, ready));
		}
	}

	/**
	 * @return the sound when its file has been read ahead, else null: nothing is waited for, and no read is started
	 */
	public OpenALSound newSoundIfReady(final FileHandle file) {
		if (this.noDevice || (file == null)) {
			return newSound(file);
		}
		final String extension = file.extension().toLowerCase();
		final boolean wav = "wav".equals(extension);
		if (!wav && !"ogg".equals(extension) && !"flac".equals(extension) && !"mp3".equals(extension)) {
			return newSound(file);
		}
		final CompletableFuture<?> pending = wav ? this.prefetchedWav.get(file.path())
				: this.prefetchedOgg.get(file.path());
		return (pending != null) && pending.isDone() && (wav || !isSpread(file.path())) ? newSound(file) : null;
	}

	private long prefetchedBytes() {
		long total = 0;
		for (final long size : this.prefetchedSizes.values()) {
			total += size;
		}
		return total;
	}

	/** @return the wav file read ahead, handing it over once, or null when there is none */
	byte[] takePrefetchedWav(final FileHandle file) {
		this.prefetchedSizes.remove(file.path());
		final CompletableFuture<byte[]> future = this.prefetchedWav.remove(file.path());
		if (future == null) {
			return null;
		}
		try {
			return future.get();
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
		catch (final ExecutionException | CancellationException e) {
			return null;
		}
	}

	/**
	 * Lets go of the sounds read or decoded ahead that nothing has asked for, such as the files of a script's sounds
	 * that it never makes, which would otherwise be held from game to game.
	 */
	public void dropPrefetched() {
		// A file that a sound waits for (newSoundLater) is taken when it is done, so it stays.
		this.prefetchedSizes.keySet().removeIf(path -> !this.awaited.contains(path));
		this.prefetchedOgg.entrySet().removeIf(entry -> {
			if (this.awaited.contains(entry.getKey())) {
				return false;
			}
			entry.getValue().cancel(false);
			return true;
		});
		this.prefetchedWav.entrySet().removeIf(entry -> {
			if (this.awaited.contains(entry.getKey())) {
				return false;
			}
			entry.getValue().cancel(false);
			return true;
		});
		this.prefetchedOggExtraTakes.keySet().removeIf(path -> !this.awaited.contains(path));
	}

	/** @return the sound decoded ahead for this file, handing it over once, or null when there is none */
	OggPcm takePrefetched(final FileHandle file) {
		final Integer extraTakes = this.prefetchedOggExtraTakes.remove(file.path());
		final CompletableFuture<OggPcm> future;
		if (extraTakes == null) {
			future = this.prefetchedOgg.remove(file.path());
			this.prefetchedSizes.remove(file.path());
		}
		else {
			future = this.prefetchedOgg.get(file.path());
			if (extraTakes > 1) {
				this.prefetchedOggExtraTakes.put(file.path(), extraTakes - 1);
			}
		}
		if (future == null) {
			return null;
		}
		try {
			return future.get();
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
		catch (final ExecutionException | CancellationException e) {
			return null;
		}
	}

	@Override
	public OpenALSound newSound(final FileHandle file) {
		if (file == null) {
			throw new IllegalArgumentException("file cannot be null.");
		}
		final Class<? extends OpenALSound> soundClass = this.extensionToSoundClass.get(file.extension().toLowerCase());
		if (soundClass == null) {
			throw new GdxRuntimeException("Unknown file extension for sound: " + file);
		}
		try {
			return soundClass.getConstructor(new Class[] { OpenALAudio.class, FileHandle.class }).newInstance(this,
					file);
		}
		catch (final Exception ex) {
			throw new GdxRuntimeException("Error creating sound " + soundClass.getName() + " for file: " + file, ex);
		}
	}

	@Override
	public OpenALMusic newMusic(final FileHandle file) {
		if (file == null) {
			throw new IllegalArgumentException("file cannot be null.");
		}
		final Class<? extends OpenALMusic> musicClass = this.extensionToMusicClass.get(file.extension().toLowerCase());
		if (musicClass == null) {
			throw new GdxRuntimeException("Unknown file extension for music: " + file);
		}
		try {
			return musicClass.getConstructor(new Class[] { OpenALAudio.class, FileHandle.class }).newInstance(this,
					file);
		}
		catch (final Exception ex) {
			throw new GdxRuntimeException("Error creating music " + musicClass.getName() + " for file: " + file, ex);
		}
	}

	int obtainSource(final boolean isMusic) {
		if (this.noDevice) {
			return 0;
		}
		for (int i = 0, n = this.idleSources.size; i < n; i++) {
			final int sourceId = this.idleSources.get(i);
			final int state = alGetSourcei(sourceId, AL_SOURCE_STATE);
			if ((state != AL_PLAYING) && (state != AL_PAUSED)) {
				return claimSource(i, isMusic);
			}
		}
		// A track that cannot start leaves the playlist quiet, so music takes a sound's source when all are busy.
		if (isMusic && (this.idleSources.size > 0)) {
			return claimSource(0, true);
		}
		return -1;
	}

	private int claimSource(final int index, final boolean isMusic) {
		final int sourceId = this.idleSources.get(index);
		if (isMusic) {
			this.idleSources.removeIndex(index);
		}
		// The id of the sound that last used the source must not reach the music, or the next sound, that gets it.
		if (this.sourceToSoundId.containsKey(sourceId)) {
			final long soundId = this.sourceToSoundId.get(sourceId);
			this.sourceToSoundId.remove(sourceId);
			this.soundIdToSource.remove(soundId);
		}
		if (!isMusic) {
			final long soundId = this.nextSoundId++;
			this.sourceToSoundId.put(sourceId, soundId);
			this.soundIdToSource.put(soundId, sourceId);
		}
		alSourceStop(sourceId);
		alSourcei(sourceId, AL_BUFFER, 0);
		AL10.alSourcef(sourceId, AL10.AL_GAIN, 1);
		AL10.alSourcef(sourceId, AL10.AL_PITCH, 1);
		AL10.alSource3f(sourceId, AL10.AL_POSITION, 0, 0, 1f);
		return sourceId;
	}

	void freeSource(final int sourceID) {
		if (this.noDevice) {
			return;
		}
		alSourceStop(sourceID);
		alSourcei(sourceID, AL_BUFFER, 0);
		if (this.sourceToSoundId.containsKey(sourceID)) {
			final long soundId = this.sourceToSoundId.remove(sourceID);
			this.soundIdToSource.remove(soundId);
		}
		this.idleSources.add(sourceID);
	}

	void freeBuffer(final int... bufferIDs) {
		if (this.noDevice) {
			return;
		}
		for (int i = 0, n = this.idleSources.size; i < n; i++) {
			final int sourceID = this.idleSources.get(i);
			// a source that played out a queue of buffers is stopped with them still queued and has no current one
			if (holds(sourceID, bufferIDs) || ((bufferIDs.length > 1) && (alGetSourcei(sourceID, AL_BUFFERS_QUEUED) > 0)
					&& (alGetSourcei(sourceID, AL_SOURCE_STATE) == AL_STOPPED))) {
				if (this.sourceToSoundId.containsKey(sourceID)) {
					final long soundId = this.sourceToSoundId.remove(sourceID);
					this.soundIdToSource.remove(soundId);
				}
				alSourceStop(sourceID);
				alSourcei(sourceID, AL_BUFFER, 0);
			}
		}
	}

	/** Whether the source plays one of the buffers (the one of a queue that it is at, for a sound played in pieces). */
	private static boolean holds(final int sourceID, final int[] bufferIDs) {
		final int current = alGetSourcei(sourceID, AL_BUFFER);
		for (final int id : bufferIDs) {
			if (id == current) {
				return true;
			}
		}
		return false;
	}

	void stopSourcesWithBuffer(final int... bufferIDs) {
		if (this.noDevice) {
			return;
		}
		for (int i = 0, n = this.idleSources.size; i < n; i++) {
			final int sourceID = this.idleSources.get(i);
			if (holds(sourceID, bufferIDs)) {
				if (this.sourceToSoundId.containsKey(sourceID)) {
					final long soundId = this.sourceToSoundId.remove(sourceID);
					this.soundIdToSource.remove(soundId);
				}
				alSourceStop(sourceID);
			}
		}
	}

	void pauseSourcesWithBuffer(final int... bufferIDs) {
		if (this.noDevice) {
			return;
		}
		for (int i = 0, n = this.idleSources.size; i < n; i++) {
			final int sourceID = this.idleSources.get(i);
			if (holds(sourceID, bufferIDs)) {
				alSourcePause(sourceID);
			}
		}
	}

	void resumeSourcesWithBuffer(final int... bufferIDs) {
		if (this.noDevice) {
			return;
		}
		for (int i = 0, n = this.idleSources.size; i < n; i++) {
			final int sourceID = this.idleSources.get(i);
			if (holds(sourceID, bufferIDs)) {
				if (alGetSourcei(sourceID, AL_SOURCE_STATE) == AL_PAUSED) {
					alSourcePlay(sourceID);
				}
			}
		}
	}

	public void update() {
		if (this.noDevice) {
			return;
		}
		for (int i = 0; i < this.music.size; i++) {
			this.music.items[i].update();
		}
	}

	public long getSoundId(final int sourceId) {
		if (!this.sourceToSoundId.containsKey(sourceId)) {
			return -1;
		}
		return this.sourceToSoundId.get(sourceId);
	}

	public void stopSound(final long soundId) {
		if (!this.soundIdToSource.containsKey(soundId)) {
			return;
		}
		final int sourceId = this.soundIdToSource.get(soundId);
		alSourceStop(sourceId);
	}

	public void pauseSound(final long soundId) {
		if (!this.soundIdToSource.containsKey(soundId)) {
			return;
		}
		final int sourceId = this.soundIdToSource.get(soundId);
		alSourcePause(sourceId);
	}

	public void resumeSound(final long soundId) {
		if (!this.soundIdToSource.containsKey(soundId)) {
			return;
		}
		final int sourceId = this.soundIdToSource.get(soundId);
		if (alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PAUSED) {
			alSourcePlay(sourceId);
		}
	}

	public void setSoundGain(final long soundId, final float volume) {
		if (!this.soundIdToSource.containsKey(soundId)) {
			return;
		}
		final int sourceId = this.soundIdToSource.get(soundId);
		AL10.alSourcef(sourceId, AL10.AL_GAIN, volume);
	}

	public void setSoundLooping(final long soundId, final boolean looping) {
		if (!this.soundIdToSource.containsKey(soundId)) {
			return;
		}
		final int sourceId = this.soundIdToSource.get(soundId);
		alSourcei(sourceId, AL10.AL_LOOPING, looping ? AL10.AL_TRUE : AL10.AL_FALSE);
	}

	public void setSoundPitch(final long soundId, final float pitch) {
		if (!this.soundIdToSource.containsKey(soundId)) {
			return;
		}
		final int sourceId = this.soundIdToSource.get(soundId);
		AL10.alSourcef(sourceId, AL10.AL_PITCH, pitch);
	}

	public void setSoundOffset(final long soundId, final float seconds) {
		if (!this.soundIdToSource.containsKey(soundId)) {
			return;
		}
		AL10.alSourcef(this.soundIdToSource.get(soundId), AL11.AL_SEC_OFFSET, seconds);
	}

	public void setSoundPan(final long soundId, final float pan, final float volume) {
		if (!this.soundIdToSource.containsKey(soundId)) {
			return;
		}
		final int sourceId = this.soundIdToSource.get(soundId);

		AL10.alSource3f(sourceId, AL10.AL_POSITION, MathUtils.cos(((pan - 1) * MathUtils.PI) / 2), 0,
				MathUtils.sin(((pan + 1) * MathUtils.PI) / 2));
		AL10.alSourcef(sourceId, AL10.AL_GAIN, volume);
	}

	public void setSoundPosition(final long soundId, final float x, final float y, final float z,
			final boolean is3DSound, final float maxDistance, final float refDistance) {
		if (!this.soundIdToSource.containsKey(soundId)) {
			return;
		}
		final int sourceId = this.soundIdToSource.get(soundId);

		AL10.alSource3f(sourceId, AL10.AL_POSITION, x, y, z);
		AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, maxDistance);
		AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, refDistance);
		AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 1.0f);
		AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, is3DSound ? AL10.AL_FALSE : AL10.AL_TRUE);
	}

	public void dispose() {
		if (this.noDevice) {
			return;
		}
		for (int i = 0, n = this.allSources.size; i < n; i++) {
			final int sourceID = this.allSources.get(i);
			final int state = alGetSourcei(sourceID, AL_SOURCE_STATE);
			if (state != AL_STOPPED) {
				alSourceStop(sourceID);
			}
			alDeleteSources(sourceID);
		}

		this.sourceToSoundId.clear();
		this.soundIdToSource.clear();

		AL.destroy();
		while (AL.isCreated()) {
			try {
				Thread.sleep(10);
			}
			catch (final InterruptedException e) {
				// Shutting down: keep waiting until OpenAL is gone.
			}
		}
	}

	@Override
	public boolean switchOutputDevice(final String deviceIdentifier) {
		return true;
	}

	@Override
	public String[] getAvailableOutputDevices() {
		return new String[0];
	}

	@Override
	public AudioDevice newAudioDevice(final int sampleRate, final boolean isMono) {
		if (this.noDevice) {
			return new AudioDevice() {
				@Override
				public void writeSamples(final float[] samples, final int offset, final int numSamples) {
				}

				@Override
				public void writeSamples(final short[] samples, final int offset, final int numSamples) {
				}

				@Override
				public void setVolume(final float volume) {
				}

				@Override
				public boolean isMono() {
					return isMono;
				}

				@Override
				public int getLatency() {
					return 0;
				}

				@Override
				public void dispose() {
				}

				@Override
				public void pause() {
				}

				@Override
				public void resume() {
				}
			};
		}
		return new OpenALAudioDevice(this, sampleRate, isMono, this.deviceBufferSize, this.deviceBufferCount);
	}

	@Override
	public AudioRecorder newAudioRecorder(final int samplingRate, final boolean isMono) {
		if (this.noDevice) {
			return new AudioRecorder() {
				@Override
				public void read(final short[] samples, final int offset, final int numSamples) {
				}

				@Override
				public void dispose() {
				}
			};
		}
		return new JavaSoundAudioRecorder(samplingRate, isMono);
	}

	/**
	 * Retains a list of the most recently played sounds and stops the sound played
	 * least recently if necessary for a new sound to play
	 */
	protected void retain(final OpenALSound sound, final boolean stop) {
		// Move the pointer ahead and wrap
		this.mostRecetSound++;
		this.mostRecetSound %= this.recentSounds.length;

		if (stop) {
			// Stop the least recent sound (the one we are about to bump off the buffer)
			if (this.recentSounds[this.mostRecetSound] != null) {
				this.recentSounds[this.mostRecetSound].stop();
			}
		}

		this.recentSounds[this.mostRecetSound] = sound;
	}

	/** Removes the disposed sound from the least recently played list */
	public void forget(final OpenALSound sound) {
		for (int i = 0; i < this.recentSounds.length; i++) {
			if (this.recentSounds[i] == sound) {
				this.recentSounds[i] = null;
			}
		}
	}
}
