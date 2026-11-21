package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

/**
 * Lets each kind of notice of the simulation through once per game step; for notices that only make the interface
 * show the step's frame again, where one per step does what any number would. Used by one thread.
 */
public final class OncePerStep {
	/** Per kind, the step it last went through in plus one; 0 for never. */
	private final int[] passedSteps;

	public OncePerStep(final int kinds) {
		this.passedSteps = new int[kinds];
	}

	/** Whether this is the first notice of the kind in the step. */
	public boolean first(final int kind, final int step) {
		final int mark = step + 1;
		if (this.passedSteps[kind] == mark) {
			return false;
		}
		this.passedSteps[kind] = mark;
		return true;
	}
}
