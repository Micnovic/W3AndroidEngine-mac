package com.etheller.warsmash.parsers.jass;

import java.util.function.Supplier;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.LocalUiLink;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderEvent;

/**
 * Where a script native hands over what only the render side can do: changing a peer or the world's look, reading what
 * a peer shows. Without a simulation thread the work runs in place. With one, a change that no script reads back is an
 * event of the frame being built ({@link #post}, run by the render thread in order with the world's own events), and
 * what the simulation depends on or the script reads back runs on the render thread while the caller waits
 * ({@link #run}, {@link #call}). A native that mixes simulation work with render work keeps the simulation part outside.
 */
final class RenderSide {
	/** The event code for a change of what is drawn that a native makes. */
	static final int WORLD = RenderEvent.SCRIPT_RENDER_COMMAND;
	static final int TEXT = RenderEvent.DISPLAY_TEXT;

	private final LocalUiLink link;

	private RenderSide(final LocalUiLink link) {
		this.link = link;
	}

	static RenderSide of(final War3MapViewer viewer) {
		return new RenderSide(viewer.localUiLink());
	}

	/** A change of what is drawn, run by the render thread in frame order; what it captures must be values. */
	void post(final int code, final Runnable change) {
		if (this.link == null) {
			change.run();
		}
		else {
			this.link.send(code, change);
		}
	}

	/** A change the caller needs done before it goes on. */
	void run(final String why, final Runnable change) {
		call(why, () -> {
			change.run();
			return null;
		});
	}

	/** A read of what is drawn. */
	<T> T call(final String why, final Supplier<T> read) {
		return this.link == null ? read.get() : this.link.onRender(why, read);
	}

	/** A native that is render work from beginning to end. */
	JassFunction wholly(final String name, final JassFunction function) {
		if (this.link == null) {
			return function;
		}
		return (arguments, globalScope, triggerScope) -> this.link.onRender(name,
				() -> function.call(arguments, globalScope, triggerScope));
	}
}
