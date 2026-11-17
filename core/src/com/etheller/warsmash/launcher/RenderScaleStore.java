package com.etheller.warsmash.launcher;

/**
 * Where the render scale the launcher's Options page shows is kept, for a platform whose in-game Options dialog sets the
 * same choice. The platform installs one at start; without one the dialog offers no render scale.
 */
public interface RenderScaleStore {
	/** The render scale in percent. */
	int percent();

	/** Keeps the render scale (percent) for this and the next games. */
	void set(int percent);

	final class Holder {
		private static volatile RenderScaleStore installed;

		private Holder() {
		}

		public static void install(final RenderScaleStore store) {
			installed = store;
		}

		/** The platform's store, or null. */
		public static RenderScaleStore get() {
			return installed;
		}
	}
}
