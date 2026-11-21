package com.etheller.warsmash.viewer5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * The helper threads keep preparing textures while the game thread is busy elsewhere, up to a limit on the bytes that
 * wait for their upload, and every texture is finished once, on the thread that asks for it.
 */
class TextureLoadQueueTest {
	@Test
	void helpersRunAheadWhileTheGameThreadIsAway() throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			final TextureLoadQueue queue = new TextureLoadQueue(executor, 2, 1000);
			final AtomicInteger prepared = new AtomicInteger();
			for (int i = 0; i < 10; i++) {
				queue.add(() -> {
					prepared.incrementAndGet();
					return 100;
				}, () -> {
				});
			}
			// the game thread never calls loadNext, yet all ten ready textures (1000 bytes) get prepared
			waitFor(() -> prepared.get() == 10);
			assertFalse(queue.isEmpty());
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void preparingStopsAtTheLimitAndGoesOnWhenTexturesAreFinished() throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			final TextureLoadQueue queue = new TextureLoadQueue(executor, 2, 250);
			final AtomicInteger prepared = new AtomicInteger();
			final List<Integer> finished = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				final int n = i;
				queue.add(() -> {
					prepared.incrementAndGet();
					return 100;
				}, () -> finished.add(n));
			}
			waitFor(() -> prepared.get() >= 4);
			Thread.sleep(100);
			// a texture starts while the finished ones hold less than 250: after 0, 100 and 200 bytes, none after 300
			assertEquals(4, prepared.get());
			while (!queue.isEmpty()) {
				queue.loadNext();
			}
			assertEquals(10, finished.size());
			assertEquals(10, prepared.get());
			for (int i = 0; i < 10; i++) {
				assertTrue(finished.contains(i));
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void finishRunsOnTheCallingThreadAndAFailedPreparationDoesNotBlockTheRest() throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(1);
		try {
			final TextureLoadQueue queue = new TextureLoadQueue(executor, 1, 1000);
			final Thread caller = Thread.currentThread();
			final AtomicInteger finished = new AtomicInteger();
			final CountDownLatch release = new CountDownLatch(1);
			queue.add(() -> {
				throw new IllegalStateException("broken texture");
			}, () -> {
				assertTrue(Thread.currentThread() == caller);
				finished.incrementAndGet();
			});
			queue.add(() -> {
				try {
					release.await(5, TimeUnit.SECONDS);
				}
				catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return 1;
			}, () -> {
				assertTrue(Thread.currentThread() == caller);
				finished.incrementAndGet();
			});
			release.countDown();
			final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (!queue.isEmpty() && (System.nanoTime() < end)) {
				queue.loadNext();
			}
			assertTrue(queue.isEmpty());
			assertEquals(2, finished.get());
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void closingDropsTheQueuedPreparationsAndFreesTheExecutor() throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(1);
		try {
			final TextureLoadQueue queue = new TextureLoadQueue(executor, 4, 1000);
			final AtomicInteger prepared = new AtomicInteger();
			final CountDownLatch started = new CountDownLatch(1);
			final CountDownLatch release = new CountDownLatch(1);
			queue.add(() -> {
				prepared.incrementAndGet();
				started.countDown();
				try {
					release.await(5, TimeUnit.SECONDS);
				}
				catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return 1;
			}, () -> {
			});
			for (int i = 0; i < 5; i++) {
				queue.add(() -> {
					prepared.incrementAndGet();
					return 1;
				}, () -> {
				});
			}
			assertTrue(started.await(5, TimeUnit.SECONDS));
			queue.close();
			release.countDown();
			// the work of another viewer on the same executor is not held up by the stale entries
			final CountDownLatch other = new CountDownLatch(1);
			executor.submit(other::countDown);
			assertTrue(other.await(5, TimeUnit.SECONDS));
			assertEquals(1, prepared.get());
			assertTrue(queue.isEmpty());
			queue.loadNext();
			queue.add(() -> {
				prepared.incrementAndGet();
				return 1;
			}, () -> {
			});
			assertTrue(queue.isEmpty());
			Thread.sleep(50);
			assertEquals(1, prepared.get());
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void aFinishedResultCountsAgainstTheLimitUntilItsUploadIsOver() throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(1);
		try {
			final TextureLoadQueue queue = new TextureLoadQueue(executor, 1, 100);
			final AtomicInteger prepared = new AtomicInteger();
			final AtomicInteger preparedDuringUpload = new AtomicInteger(-1);
			for (int i = 0; i < 2; i++) {
				queue.add(() -> {
					prepared.incrementAndGet();
					return 100;
				}, () -> {
					try {
						Thread.sleep(100);
					}
					catch (final InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					preparedDuringUpload.compareAndSet(-1, prepared.get());
				});
			}
			waitFor(() -> prepared.get() == 1);
			while (preparedDuringUpload.get() == -1) {
				queue.loadNext();
			}
			assertEquals(1, preparedDuringUpload.get());
			waitFor(() -> prepared.get() == 2);
		}
		finally {
			executor.shutdownNow();
		}
	}

	private static void waitFor(final java.util.function.BooleanSupplier condition) throws InterruptedException {
		final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!condition.getAsBoolean() && (System.nanoTime() < end)) {
			Thread.sleep(5);
		}
		assertTrue(condition.getAsBoolean());
	}
}
