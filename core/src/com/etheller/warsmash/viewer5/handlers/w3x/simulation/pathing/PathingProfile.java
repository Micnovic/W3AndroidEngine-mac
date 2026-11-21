package com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.etheller.warsmash.util.Log;

/**
 * Where the pathfinding processor spends its time, split by part and by search, logged as PATHPROF lines while the
 * "warsmash.pathprof" system property is set (its value, when it is a file name, also receives the report). The
 * parts are timed exclusively: a part nested in another is taken from it. Nothing here changes what the simulation
 * does; with the property unset every hook is one test of a constant.
 */
public final class PathingProfile {
	public static final boolean ENABLED = System.getProperty("warsmash.pathprof") != null;
	/** Times every node test of the search (costly: the clock is read as often as a test is made). */
	public static final boolean FINE = ENABLED && !"coarse".equals(System.getProperty("warsmash.pathprof.mode"));

	public enum Part {
		OUTSIDE, BOOKKEEPING, GOAL_NEAREST, GOAL_BESIDE, APPROACH, REACH_AIM, GRAPH_UPDATE, REACH_FILL, OPEN_AROUND,
		ENCLOSED_FILL, SEARCH, TERRAIN_TEST, UNIT_TEST, SLIDE, CROSSES, BUILD_PATH, PARTIAL_PATH, CALLBACK, CAN_REACH
	}

	public enum Count {
		UPDATES, JOBS_QUEUED, JOBS_STARTED, JOBS_FINISHED, JOBS_RESTARTED, PREEMPTIONS, ITERATIONS, NODE_TESTS,
		NODE_MISSES, OPEN_ADDS, GRAPH_REPAIRS, GRAPH_FULL, GRAPH_BUILDS, FILLS, FILL_POINTS, ENCLOSED_CALLS,
		ENCLOSED_OPEN_SKIPS, ENCLOSED_FILL_CELLS, BUDGET_PAUSES, WAIT_OWNER, WAIT_RESERVED, SLIDES, CAN_REACH_CALLS, PREEMPT_WASTED_ITERATIONS
	}

	public enum Outcome {
		GOAL, PARTIAL, STAY, SAME_POINT, APPROACH_CELL, AIM_STAY
	}

	public static final class Job {
		private final long[] ns = new long[Part.values().length];
		private int queuedTick;
		private int starts;
		private int iterations;
		private int updatesWorked;
		private int endTick;
		private float distance;
		private String movement = "";
		private float collision;
		private int flags;
		private Outcome outcome;
		private boolean done;

		private long cost() {
			long total = 0;
			for (final Part part : Part.values()) {
				if ((part != Part.OUTSIDE) && (part != Part.CALLBACK)) {
					total += this.ns[part.ordinal()];
				}
			}
			return total;
		}

		private String type() {
			final StringBuilder type = new StringBuilder();
			type.append((this.flags & F_SHORT) != 0 ? "short" : "normal");
			type.append((this.flags & F_HUMAN) != 0 ? " human" : " cpu");
			type.append((this.flags & F_CORNERS) != 0 ? " corner" : " cell");
			if ((this.flags & F_MOVED) != 0) {
				type.append(" goal-moved");
			}
			if ((this.flags & F_ENCLOSED) != 0) {
				type.append(" enclosed");
			}
			if ((this.flags & F_DETOUR) != 0) {
				type.append(" detour");
			}
			if ((this.flags & F_SCRIPTED) != 0) {
				type.append(" scripted");
			}
			if ((this.flags & F_BLOCKED) != 0) {
				type.append(" goal-blocked");
			}
			type.append(' ').append(this.movement);
			type.append(' ').append(this.outcome);
			return type.toString();
		}
	}

	public static final int F_SHORT = 1;
	public static final int F_HUMAN = 2;
	public static final int F_CORNERS = 4;
	public static final int F_MOVED = 8;
	public static final int F_ENCLOSED = 16;
	public static final int F_DETOUR = 32;
	public static final int F_SCRIPTED = 64;
	public static final int F_BLOCKED = 128;

	private static final int PARTS = Part.values().length;
	private static final long REPORT_TICKS = 5000;
	private static final int TOP = 10;
	private static final double[] TICK_EDGES_MS = { 0.1, 0.5, 1, 2, 5, 10, 20, 50, 100 };

	private static long[] partNs = new long[PARTS];
	private static long[] counts = new long[Count.values().length];
	private static final int[] stack = new int[32];
	private static int depth;
	private static int current;
	private static long last;
	private static Job currentJob;
	private static long clockCalls;
	private static long updateStart;
	private static int tick = -1;
	private static int firstTick;
	private static long tickNs;
	private static long tickIterations;
	private static long tickMisses;
	private static Job sliceJob;
	private static long sliceStartCost;
	private static final List<Long> slices = new ArrayList<>();
	private static final int[] WORKERS = { 2, 3, 4 };
	private static final long[] makespanNs = new long[WORKERS.length];
	private static long sliceTotalNs;
	private static long multiSliceTicks;
	private static long[] sliceCountHistogram = new long[8];
	private static long[] sliceCountNs = new long[8];
	private static long tickWallStart;
	private static long wallTotal;
	private static final double[] WALL_EDGES_MS = { 5, 10, 20, 50, 100, 300 };
	private static final long[] wallCount = new long[WALL_EDGES_MS.length + 1];
	private static final long[] wallNs = new long[WALL_EDGES_MS.length + 1];
	private static final long[] wallPathNs = new long[WALL_EDGES_MS.length + 1];
	private static long[] tickParts = new long[PARTS];
	private static final long[] tickHistogramNs = new long[TICK_EDGES_MS.length + 1];
	private static final long[] tickHistogramCount = new long[TICK_EDGES_MS.length + 1];
	private static long pathTicks;
	private static final List<Job> topJobs = new ArrayList<>();
	private static final List<long[]> topTicks = new ArrayList<>();
	private static final Map<String, long[]> byType = new TreeMap<>();
	private static final List<Float> waitTicks = new ArrayList<>();
	private static int game;
	private static int lastReportTick;
	private static long jobsAll;
	private static final int[] distanceBuckets = { 0, 256, 512, 1024, 2048, 4096, 8192, Integer.MAX_VALUE };
	private static final long[][] byDistance = new long[distanceBuckets.length][4];
	private static double clockNanos;

	private PathingProfile() {
	}

	private static long now() {
		clockCalls++;
		return System.nanoTime();
	}

	public static void count(final Count count) {
		counts[count.ordinal()]++;
	}

	public static void count(final Count count, final long amount) {
		counts[count.ordinal()] += amount;
	}

	public static Job newJob(final int queuedTick) {
		final Job job = new Job();
		job.queuedTick = queuedTick;
		count(Count.JOBS_QUEUED);
		return job;
	}

	/** Switches the part being timed without remembering the old one (for the straight-line stretches of a loop). */
	public static void mark(final Part part) {
		final long time = now();
		partNs[current] += time - last;
		if (currentJob != null) {
			currentJob.ns[current] += time - last;
		}
		tickParts[current] += time - last;
		current = part.ordinal();
		last = time;
	}

	public static void enter(final Part part) {
		final long time = now();
		partNs[current] += time - last;
		if (currentJob != null) {
			currentJob.ns[current] += time - last;
		}
		tickParts[current] += time - last;
		stack[depth++] = current;
		current = part.ordinal();
		last = time;
	}

	public static void exit() {
		final long time = now();
		partNs[current] += time - last;
		if (currentJob != null) {
			currentJob.ns[current] += time - last;
		}
		tickParts[current] += time - last;
		current = stack[--depth];
		last = time;
	}

	public static void setJob(final Job job) {
		final long time = now();
		partNs[current] += time - last;
		if (currentJob != null) {
			currentJob.ns[current] += time - last;
		}
		tickParts[current] += time - last;
		last = time;
		endSlice();
		currentJob = job;
		sliceJob = job;
		sliceStartCost = job == null ? 0 : job.cost();
	}

	/** One search's work in one tick: what a worker thread would be given. */
	private static void endSlice() {
		if (sliceJob != null) {
			slices.add(Math.max(1, sliceJob.cost() - sliceStartCost));
			sliceJob = null;
		}
	}

	public static void beginUpdate(final int gameTick) {
		if (gameTick < tick) {
			endGame();
		}
		if (tick < 0) {
			firstTick = gameTick;
			lastReportTick = gameTick;
			calibrate();
		}
		if (gameTick != tick) {
			final long time = now();
			closeTick(tick >= 0 ? time - tickWallStart : -1);
			tick = gameTick;
			tickWallStart = time;
			tickIterations = counts[Count.ITERATIONS.ordinal()];
			tickMisses = counts[Count.NODE_MISSES.ordinal()];
		}
		counts[Count.UPDATES.ordinal()]++;
		depth = 0;
		last = now();
		updateStart = last;
		current = Part.BOOKKEEPING.ordinal();
		currentJob = null;
	}

	public static void endUpdate() {
		final long time = now();
		partNs[current] += time - last;
		tickParts[current] += time - last;
		if (currentJob != null) {
			currentJob.ns[current] += time - last;
		}
		endSlice();
		currentJob = null;
		tickNs += time - updateStart;
		current = Part.OUTSIDE.ordinal();
		depth = 0;
		last = time;
		if ((tick - lastReportTick) >= REPORT_TICKS) {
			lastReportTick = tick;
			report("progress");
		}
	}

	/** Time spent outside the pathing update is not counted: calls from elsewhere start a part of their own. */
	public static void enterExternal(final Part part) {
		if (current == Part.OUTSIDE.ordinal()) {
			depth = 0;
			last = now();
		}
		enter(part);
	}

	public static void exitExternal() {
		exit();
	}

	private static void calibrate() {
		final long start = System.nanoTime();
		long sink = 0;
		for (int i = 0; i < 200000; i++) {
			sink += System.nanoTime();
		}
		clockNanos = (System.nanoTime() - start) / 200000.0;
		if (sink == 42) {
			clockNanos++;
		}
	}

	/** wall is the time from this tick's pathing update to the next one's: the whole simulation step, about. */
	private static void closeTick(final long wall) {
		if (tick < 0) {
			return;
		}
		if (wall >= 0) {
			wallTotal += wall;
			int bucket = 0;
			while ((bucket < WALL_EDGES_MS.length) && ((wall / 1e6) >= WALL_EDGES_MS[bucket])) {
				bucket++;
			}
			wallCount[bucket]++;
			wallNs[bucket] += wall;
			wallPathNs[bucket] += tickNs;
		}
		if (!slices.isEmpty()) {
			long sum = 0;
			for (final long value : slices) {
				sum += value;
			}
			sliceTotalNs += sum;
			final int bucket = Math.min(slices.size(), sliceCountHistogram.length - 1);
			sliceCountHistogram[bucket]++;
			sliceCountNs[bucket] += sum;
			final long[] sorted = new long[slices.size()];
			for (int i = 0; i < sorted.length; i++) {
				sorted[i] = slices.get(i);
			}
			Arrays.sort(sorted);
			for (int w = 0; w < WORKERS.length; w++) {
				final long[] load = new long[WORKERS[w]];
				for (int i = sorted.length - 1; i >= 0; i--) {
					int least = 0;
					for (int k = 1; k < load.length; k++) {
						if (load[k] < load[least]) {
							least = k;
						}
					}
					load[least] += sorted[i];
				}
				long max = 0;
				for (final long value : load) {
					max = Math.max(max, value);
				}
				makespanNs[w] += max;
			}
			if (slices.size() > 1) {
				multiSliceTicks++;
			}
			slices.clear();
		}
		final double ms = tickNs / 1e6;
		if (tickNs > 0) {
			pathTicks++;
			int bucket = 0;
			while ((bucket < TICK_EDGES_MS.length) && (ms >= TICK_EDGES_MS[bucket])) {
				bucket++;
			}
			tickHistogramNs[bucket] += tickNs;
			tickHistogramCount[bucket]++;
			final long[] record = new long[PARTS + 4];
			record[0] = tick;
			record[1] = tickNs;
			for (int i = 0; i < PARTS; i++) {
				record[i + 2] = tickParts[i];
			}
			record[PARTS + 2] = counts[Count.ITERATIONS.ordinal()] - tickIterations;
			record[PARTS + 3] = counts[Count.NODE_MISSES.ordinal()] - tickMisses;
			topTicks.add(record);
			topTicks.sort((a, b) -> Long.compare(b[1], a[1]));
			while (topTicks.size() > TOP) {
				topTicks.remove(topTicks.size() - 1);
			}
		}
		tickNs = 0;
		Arrays.fill(tickParts, 0);
	}

	public static void jobStarted(final Job job) {
		count(Count.JOBS_STARTED);
		if (job.starts++ > 0) {
			count(Count.JOBS_RESTARTED);
		}
	}

	public static void jobWorked(final Job job) {
		job.updatesWorked++;
	}

	public static void outcome(final Job job, final Outcome outcome) {
		job.outcome = outcome;
	}

	/** Called when a search ends, before it leaves the queue. */
	public static void jobDone(final Job job, final int gameTick, final int iterations, final float distance,
			final String movement, final float collision, final int flags) {
		job.endTick = gameTick;
		job.iterations = iterations;
		job.distance = distance;
		job.movement = movement;
		job.collision = collision;
		job.flags = flags;
		job.done = true;
		if (job.outcome == null) {
			job.outcome = Outcome.PARTIAL;
		}
		count(Count.JOBS_FINISHED);
		jobsAll++;
		final long cost = job.cost();
		final long[] type = byType.computeIfAbsent(job.type(), key -> new long[5]);
		type[0]++;
		type[1] += cost;
		type[2] = Math.max(type[2], cost);
		type[3] += iterations;
		type[4] += (long) distance;
		int bucket = 0;
		while (distance >= distanceBuckets[bucket + 1]) {
			bucket++;
		}
		final long[] row = byDistance[bucket];
		row[0]++;
		row[1] += cost;
		row[2] += iterations;
		row[3] = Math.max(row[3], cost);
		waitTicks.add((float) (gameTick - job.queuedTick));
		if ((topJobs.size() < TOP) || (cost > topJobs.get(topJobs.size() - 1).cost())) {
			topJobs.add(job);
			topJobs.sort(Comparator.comparingLong(Job::cost).reversed());
			while (topJobs.size() > TOP) {
				topJobs.remove(topJobs.size() - 1);
			}
		}
	}

	private static void endGame() {
		closeTick(-1);
		report("game end");
		game++;
		partNs = new long[PARTS];
		counts = new long[Count.values().length];
		tickParts = new long[PARTS];
		Arrays.fill(tickHistogramNs, 0);
		Arrays.fill(tickHistogramCount, 0);
		pathTicks = 0;
		wallTotal = 0;
		sliceTotalNs = 0;
		multiSliceTicks = 0;
		Arrays.fill(makespanNs, 0);
		Arrays.fill(sliceCountHistogram, 0);
		Arrays.fill(sliceCountNs, 0);
		slices.clear();
		sliceJob = null;
		Arrays.fill(wallCount, 0);
		Arrays.fill(wallNs, 0);
		Arrays.fill(wallPathNs, 0);
		topJobs.clear();
		topTicks.clear();
		byType.clear();
		waitTicks.clear();
		for (final long[] row : byDistance) {
			Arrays.fill(row, 0);
		}
		jobsAll = 0;
		clockCalls = 0;
		tick = -1;
		tickNs = 0;
	}

	private static String ms(final long nanos) {
		return String.format(Locale.ROOT, "%.1f", nanos / 1e6);
	}

	private static void report(final String reason) {
		final StringBuilder out = new StringBuilder();
		final long updateTotal = totalCounted();
		final int ticks = Math.max(1, tick - firstTick + 1);
		out.append(String.format(Locale.ROOT, "PATHPROF game %d (%s) ticks %d..%d, path time %s ms = %.3f ms/tick, "
				+ "ticks with path work %d, clock %.1f ns x %d calls = ~%s ms overhead%n", game, reason, firstTick, tick,
				ms(updateTotal), (updateTotal / 1e6) / ticks, pathTicks, clockNanos, clockCalls,
				ms((long) (clockNanos * clockCalls))));
		for (final Part part : Part.values()) {
			if (part == Part.OUTSIDE) {
				continue;
			}
			final long value = partNs[part.ordinal()];
			out.append(String.format(Locale.ROOT, "PATHPROF part %-13s %10s ms %5.1f %%%n", part, ms(value),
					(100.0 * value) / Math.max(1, updateTotal)));
		}
		final StringBuilder counters = new StringBuilder("PATHPROF counts");
		for (final Count count : Count.values()) {
			counters.append(' ').append(count).append('=').append(counts[count.ordinal()]);
		}
		out.append(counters).append('\n');
		out.append("PATHPROF tick histogram (path ms in one tick: ticks, total ms, share)\n");
		long histogramTotal = 0;
		for (final long value : tickHistogramNs) {
			histogramTotal += value;
		}
		for (int i = 0; i < tickHistogramNs.length; i++) {
			final String edge = i == 0 ? String.format(Locale.ROOT, "< %.1f", TICK_EDGES_MS[0])
					: i == TICK_EDGES_MS.length ? String.format(Locale.ROOT, ">= %.0f", TICK_EDGES_MS[i - 1])
							: String.format(Locale.ROOT, "%.1f..%.1f", TICK_EDGES_MS[i - 1], TICK_EDGES_MS[i]);
			out.append(String.format(Locale.ROOT, "PATHPROF   %-10s %7d ticks %9s ms %5.1f %%%n", edge,
					tickHistogramCount[i], ms(tickHistogramNs[i]),
					(100.0 * tickHistogramNs[i]) / Math.max(1, histogramTotal)));
		}
		out.append(String.format(Locale.ROOT,
				"PATHPROF simulation steps by length (wall time between pathing updates; steps, wall ms, path ms, path share); "
						+ "all steps: wall %s ms, path share %.1f %%%n",
				ms(wallTotal), (100.0 * updateTotal) / Math.max(1, wallTotal)));
		for (int i = 0; i < wallCount.length; i++) {
			final String edge = i == 0 ? String.format(Locale.ROOT, "< %.0f", WALL_EDGES_MS[0])
					: i == WALL_EDGES_MS.length ? String.format(Locale.ROOT, ">= %.0f", WALL_EDGES_MS[i - 1])
							: String.format(Locale.ROOT, "%.0f..%.0f", WALL_EDGES_MS[i - 1], WALL_EDGES_MS[i]);
			out.append(String.format(Locale.ROOT, "PATHPROF   %-10s %7d steps %9s ms wall %9s ms path %5.1f %%%n", edge,
					wallCount[i], ms(wallNs[i]), ms(wallPathNs[i]), (100.0 * wallPathNs[i]) / Math.max(1, wallNs[i])));
		}
		out.append(String.format(Locale.ROOT, "PATHPROF searches worked per tick (a search's work in one tick = one slice): "
				+ "ticks with several slices %d%n", multiSliceTicks));
		for (int i = 1; i < sliceCountHistogram.length; i++) {
			if (sliceCountHistogram[i] > 0) {
				out.append(String.format(Locale.ROOT, "PATHPROF   %d%s slices %7d ticks %9s ms%n", i,
						i == (sliceCountHistogram.length - 1) ? "+" : "", sliceCountHistogram[i],
						ms(sliceCountNs[i])));
			}
		}
		for (int w = 0; w < WORKERS.length; w++) {
			out.append(String.format(Locale.ROOT,
					"PATHPROF ideal %d workers (slices of a tick spread over them, no overhead): %s ms of %s ms -> x%.2f%n",
					WORKERS[w], ms(makespanNs[w]), ms(sliceTotalNs), (double) sliceTotalNs / Math.max(1, makespanNs[w])));
		}
		out.append("PATHPROF worst ticks (tick, ms, parts over 5 %)\n");
		for (final long[] record : topTicks) {
			final StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
					"PATHPROF   tick %d %s ms (iterations %d, %.2f us each; node misses %d):", record[0],
					ms(record[1]), record[PARTS + 2], record[1] / 1e3 / Math.max(1, record[PARTS + 2]),
					record[PARTS + 3]));
			for (int i = 0; i < PARTS; i++) {
				if ((i != Part.OUTSIDE.ordinal()) && (record[i + 2] * 20 > record[1])) {
					line.append(' ').append(Part.values()[i]).append('=').append(ms(record[i + 2]));
				}
			}
			out.append(line).append('\n');
		}
		out.append("PATHPROF by search type (count, total ms, max ms, avg iterations, avg distance)\n");
		final List<Map.Entry<String, long[]>> types = new ArrayList<>(byType.entrySet());
		types.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
		int shown = 0;
		for (final Map.Entry<String, long[]> entry : types) {
			final long[] v = entry.getValue();
			out.append(String.format(Locale.ROOT, "PATHPROF   %-60s %7d %9s %8s %8.0f %8.0f%n", entry.getKey(), v[0],
					ms(v[1]), ms(v[2]), (double) v[3] / v[0], (double) v[4] / v[0]));
			if (++shown >= 25) {
				break;
			}
		}
		out.append("PATHPROF by distance (unit points: searches, total ms, avg iterations, max ms)\n");
		for (int i = 0; i < (distanceBuckets.length - 1); i++) {
			final long[] row = byDistance[i];
			if (row[0] > 0) {
				out.append(String.format(Locale.ROOT, "PATHPROF   %5d..%-10s %7d %9s %8.0f %8s%n", distanceBuckets[i],
						distanceBuckets[i + 1] == Integer.MAX_VALUE ? "" : String.valueOf(distanceBuckets[i + 1]),
						row[0], ms(row[1]), (double) row[2] / row[0], ms(row[3])));
			}
		}
		if (!waitTicks.isEmpty()) {
			final float[] sorted = new float[waitTicks.size()];
			for (int i = 0; i < sorted.length; i++) {
				sorted[i] = waitTicks.get(i);
			}
			Arrays.sort(sorted);
			out.append(String.format(Locale.ROOT,
					"PATHPROF queue wait ticks (queued to answered): p50 %.0f p90 %.0f p99 %.0f max %.0f over %d searches%n",
					sorted[sorted.length / 2], sorted[(int) (sorted.length * 0.9)],
					sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.99))], sorted[sorted.length - 1],
					sorted.length));
		}
		out.append("PATHPROF top searches by cost (ms, type, distance, iterations, starts, updates worked, wait ticks, parts)\n");
		for (final Job job : topJobs) {
			final StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
					"PATHPROF   %s ms | %s | dist %.0f | iter %d | starts %d | updates %d | wait %d | col %.0f |",
					ms(job.cost()), job.type(), job.distance, job.iterations, job.starts, job.updatesWorked,
					job.endTick - job.queuedTick, job.collision));
			for (int i = 0; i < PARTS; i++) {
				if ((i != Part.OUTSIDE.ordinal()) && (i != Part.CALLBACK.ordinal())
						&& (job.ns[i] * 20 > job.cost())) {
					line.append(' ').append(Part.values()[i]).append('=').append(ms(job.ns[i]));
				}
			}
			out.append(line).append('\n');
		}
		final String text = out.toString();
		Log.info(text);
		final String target = System.getProperty("warsmash.pathprof");
		if ((target != null) && !target.isEmpty() && !"true".equals(target) && !"1".equals(target)) {
			try {
				Files.write(Paths.get(target + ".g" + game + ".txt"), text.getBytes(StandardCharsets.UTF_8));
			}
			catch (final IOException e) {
				Log.warn(e);
			}
		}
	}

	private static long totalCounted() {
		long total = 0;
		for (final Part part : Part.values()) {
			if (part != Part.OUTSIDE) {
				total += partNs[part.ordinal()];
			}
		}
		return total;
	}

	static {
		if (ENABLED) {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				closeTick(-1);
				report("exit");
			}));
		}
	}
}
