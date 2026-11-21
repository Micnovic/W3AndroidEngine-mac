package com.etheller.interpreter.ast.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.etheller.interpreter.ast.execution.JassStackFrame;
import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.execution.instruction.BeginFunctionInstruction;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.warsmash.util.Log;

/**
 * Time per script function and per native, only while the {@code warsmash.jassprofile} system property names an
 * output file. Every call site is guarded by {@link #ENABLED}, a constant, so the profiler costs nothing otherwise.
 * <p>
 * Self time is the time spent in a function or native itself, inclusive time also covers what it calls. Only time
 * the script spends running counts: a thread that waits in TriggerSleepAction is not charged. Functions started
 * from a script thread by ExecuteFunc, ForGroup, an enum filter or a condition count as part of the native that
 * started them. A function or native that takes at least {@code warsmash.jassprofile.slow} milliseconds (default
 * 20, 0 = off) is also listed one by one with the game tick, so a spike can be traced to the call that caused it.
 * The tables are written when the process ends, every {@code warsmash.jassprofile.interval} seconds if set, and
 * on {@link #dump()}.
 */
public final class JassProfiler {
	public static final boolean ENABLED = System.getProperty("warsmash.jassprofile") != null;
	private static final int MAX_SLOW_LINES = 5000;
	private static final JassProfiler INSTANCE = ENABLED ? new JassProfiler() : null;

	private static final class Node {
		private final String name;
		private final boolean isNative;
		private long calls;
		private long selfNanos;
		private long inclusiveNanos;
		private long maxInclusiveNanos;

		private Node(final String name, final boolean isNative) {
			this.name = name;
			this.isNative = isNative;
		}
	}

	private static final class Entry {
		private final Node node;
		private final JassStackFrame frame;
		private final List<JassValue> arguments;
		private final boolean recursive;
		private final long startNanos = System.nanoTime();
		private long selfNanos;
		private long childNanos;
		private long heaviestChildNanos;
		private String heaviestChild;

		private Entry(final Node node, final JassStackFrame frame, final List<JassValue> arguments,
				final boolean recursive) {
			this.node = node;
			this.frame = frame;
			this.arguments = arguments;
			this.recursive = recursive;
		}
	}

	private static final class ThreadState {
		private final ArrayList<Entry> stack = new ArrayList<>();
		/** What was running when the current slice of this thread started: the thread's bottom call is its child. */
		private Entry sliceParent;
	}

	private final Map<String, Node> functions = new HashMap<>();
	private final Map<String, Node> natives = new HashMap<>();
	private final Map<JassThread, ThreadState> states = new java.util.IdentityHashMap<>();
	private final List<String> slowLines = new ArrayList<>();
	private final String fileName = System.getProperty("warsmash.jassprofile");
	private final long slowNanos = Long.getLong("warsmash.jassprofile.slow", 20L) * 1_000_000L;
	private final long intervalNanos = Long.getLong("warsmash.jassprofile.interval", 0L) * 1_000_000_000L;
	private final long startNanos = System.nanoTime();
	private long nextDump = System.nanoTime() + this.intervalNanos;
	private long slowCount;
	private long tick;
	private Entry top;
	private long last;

	private JassProfiler() {
		Runtime.getRuntime().addShutdownHook(new Thread(JassProfiler::dump, "jass-profile-dump"));
	}

	/** The simulation step the following calls belong to, for the list of slow calls. */
	public static void setTick(final int tick) {
		final JassProfiler p = INSTANCE;
		p.tick = tick;
		if ((p.intervalNanos > 0) && (System.nanoTime() >= p.nextDump)) {
			p.nextDump = System.nanoTime() + p.intervalNanos;
			dump();
		}
	}

	public static void sliceStart(final JassThread thread) {
		final JassProfiler p = INSTANCE;
		final long now = System.nanoTime();
		p.charge(now);
		final ThreadState state = p.states.computeIfAbsent(thread, t -> new ThreadState());
		state.sliceParent = p.top;
		final ArrayList<Entry> stack = state.stack;
		if (!stack.isEmpty()) {
			p.top = stack.get(stack.size() - 1);
		}
	}

	/**
	 * @param ended the thread will not run again (finished, stopped, or failed), so calls left open are closed
	 */
	public static void sliceEnd(final JassThread thread, final boolean ended) {
		final JassProfiler p = INSTANCE;
		final ThreadState state = p.states.get(thread);
		if (state == null) {
			return;
		}
		final long now = System.nanoTime();
		p.charge(now);
		if (ended || thread.isHalted()) {
			while (!state.stack.isEmpty()) {
				p.popEntry(state, now);
			}
		}
		p.top = state.sliceParent;
		state.sliceParent = null;
		if (state.stack.isEmpty()) {
			p.states.remove(thread);
		}
	}

	public static void enterFunction(final JassThread thread, final BeginFunctionInstruction begin) {
		final JassProfiler p = INSTANCE;
		final ThreadState state = p.states.get(thread);
		if (state == null) {
			return;
		}
		p.push(state, p.node(p.functions, begin.getName(), false), thread.stackFrame, null);
	}

	public static void enterNative(final JassThread thread, final String name, final List<JassValue> arguments) {
		final JassProfiler p = INSTANCE;
		final ThreadState state = p.states.get(thread);
		if (state == null) {
			return;
		}
		p.push(state, p.node(p.natives, name, true), null, arguments);
	}

	public static void exitNative(final JassThread thread) {
		final JassProfiler p = INSTANCE;
		final ThreadState state = p.states.get(thread);
		if ((state == null) || state.stack.isEmpty() || !state.stack.get(state.stack.size() - 1).node.isNative) {
			return;
		}
		p.popEntry(state, System.nanoTime());
	}

	/** A script function returns: its frame is the one that began it. */
	public static void exitFunction(final JassThread thread, final JassStackFrame finishing) {
		final JassProfiler p = INSTANCE;
		final ThreadState state = p.states.get(thread);
		if ((state == null) || state.stack.isEmpty()) {
			return;
		}
		if (state.stack.get(state.stack.size() - 1).frame == finishing) {
			p.popEntry(state, System.nanoTime());
		}
	}

	private Node node(final Map<String, Node> table, final String name, final boolean isNative) {
		Node node = table.get(name);
		if (node == null) {
			node = new Node(name, isNative);
			table.put(name, node);
		}
		return node;
	}

	private void charge(final long now) {
		if (this.top != null) {
			this.top.selfNanos += now - this.last;
		}
		this.last = now;
	}

	private void push(final ThreadState state, final Node node, final JassStackFrame frame,
			final List<JassValue> arguments) {
		charge(System.nanoTime());
		boolean recursive = false;
		for (final Entry open : state.stack) {
			if (open.node == node) {
				recursive = true;
				break;
			}
		}
		final Entry entry = new Entry(node, frame, arguments, recursive);
		node.calls++;
		state.stack.add(entry);
		this.top = entry;
	}

	private void popEntry(final ThreadState state, final long now) {
		charge(now);
		final ArrayList<Entry> stack = state.stack;
		final Entry entry = stack.remove(stack.size() - 1);
		final long inclusive = entry.selfNanos + entry.childNanos;
		final Node node = entry.node;
		node.selfNanos += entry.selfNanos;
		if (!entry.recursive) {
			node.inclusiveNanos += inclusive;
		}
		if (inclusive > node.maxInclusiveNanos) {
			node.maxInclusiveNanos = inclusive;
		}
		final Entry parent = stack.isEmpty() ? state.sliceParent : stack.get(stack.size() - 1);
		if (parent != null) {
			parent.childNanos += inclusive;
			if (inclusive > parent.heaviestChildNanos) {
				parent.heaviestChildNanos = inclusive;
				parent.heaviestChild = node.name;
			}
		}
		this.top = parent;
		if ((this.slowNanos > 0) && (inclusive >= this.slowNanos) && (this.slowCount < MAX_SLOW_LINES)) {
			this.slowCount++;
			this.slowLines.add(String.format(Locale.ROOT, "tick %d  %s %s  incl %.1f ms  self %.1f ms  wall %.1f ms  depth %d  heaviest child %s %.1f ms%s",
					this.tick, node.isNative ? "native" : "func  ", node.name, inclusive / 1e6, entry.selfNanos / 1e6,
					(now - entry.startNanos) / 1e6, stack.size(), entry.heaviestChild, entry.heaviestChildNanos / 1e6, describe(entry)));
		}
	}

	private static String describe(final Entry entry) {
		if ((entry.arguments == null) || entry.arguments.isEmpty()) {
			return "";
		}
		final StringBuilder text = new StringBuilder("  args (");
		for (int i = 0; i < entry.arguments.size(); i++) {
			if (i > 0) {
				text.append(", ");
			}
			final JassValue argument = entry.arguments.get(i);
			if (argument instanceof IntegerJassValue) {
				final int value = ((IntegerJassValue) argument).getValue();
				text.append(isRawcode(value) ? "'" + rawcode(value) + "'" : Integer.toString(value));
			}
			else if (argument instanceof StringJassValue) {
				text.append('"').append(argument).append('"');
			}
			else {
				text.append(argument);
			}
		}
		return text.append(')').toString();
	}

	private static boolean isRawcode(final int value) {
		for (int shift = 0; shift < 32; shift += 8) {
			final int c = (value >> shift) & 0xFF;
			if ((c < 0x30) || (c > 0x7A)) {
				return false;
			}
		}
		return true;
	}

	private static String rawcode(final int value) {
		return new String(new char[] { (char) ((value >> 24) & 0xFF), (char) ((value >> 16) & 0xFF),
				(char) ((value >> 8) & 0xFF), (char) (value & 0xFF) });
	}

	/** Writes the tables and the list of slow calls to the file named by the system property. */
	public static void dump() {
		if (!ENABLED) {
			return;
		}
		final JassProfiler p = INSTANCE;
		final StringBuilder text = new StringBuilder();
		try {
			final double wall = (System.nanoTime() - p.startNanos) / 1e9;
			text.append(String.format(Locale.ROOT, "JASS profile, %.1f s since the first script run, tick %d%n", wall,
					p.tick));
			table(text, "Script functions by self time", p.functions, true);
			table(text, "Script functions by inclusive time", p.functions, false);
			table(text, "Natives by self time", p.natives, true);
			table(text, "Natives by inclusive time", p.natives, false);
			text.append(String.format(Locale.ROOT, "%nSlow calls (>= %d ms; children come before their caller)%n",
					p.slowNanos / 1_000_000L));
			for (final String line : p.slowLines) {
				text.append(line).append('\n');
			}
			if (p.slowCount >= MAX_SLOW_LINES) {
				text.append("(list full)\n");
			}
		}
		catch (final RuntimeException e) {
			// Dumped from the shutdown hook while the simulation was still writing a table.
			text.append("(incomplete: ").append(e).append(")\n");
		}
		try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(p.fileName), StandardCharsets.UTF_8))) {
			out.print(text);
		}
		catch (final IOException e) {
			Log.warn("JASS profile not written: " + e);
		}
	}

	private static void table(final StringBuilder text, final String title, final Map<String, Node> nodes,
			final boolean bySelf) {
		final List<Node> sorted = new ArrayList<>(nodes.values());
		sorted.sort((a, b) -> Long.compare(bySelf ? b.selfNanos : b.inclusiveNanos,
				bySelf ? a.selfNanos : a.inclusiveNanos));
		text.append(String.format(Locale.ROOT, "%n%s%n%10s %10s %10s %10s %10s  %s%n", title, "calls", "self ms",
				"incl ms", "max ms", "self us/c", "name"));
		int rows = 0;
		for (final Node node : sorted) {
			if (rows++ >= 60) {
				break;
			}
			text.append(String.format(Locale.ROOT, "%10d %10.1f %10.1f %10.1f %10.1f  %s%n", node.calls,
					node.selfNanos / 1e6, node.inclusiveNanos / 1e6, node.maxInclusiveNanos / 1e6,
					(node.selfNanos / 1e3) / Math.max(1, node.calls), node.name));
		}
	}
}
