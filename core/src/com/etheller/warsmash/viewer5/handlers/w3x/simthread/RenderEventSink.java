package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

/**
 * Where the simulation sends what it used to call on the render controller directly.
 * <p>
 * Writer: the simulation thread only (in {@link SimThreadMode#SERIAL} the render thread running the step). Reader: the
 * implementation hands the events to the frame under construction, which the render thread reads later. Implementations
 * must not call GL, the interface or any render peer, and must not block.
 */
public interface RenderEventSink {
	/** A cleared event stamped with the current tick and the next order number; the caller fills it, then {@link #emit}s it. */
	RenderEvent begin(int code);

	/** Records the event in the frame under construction; ownership passes to the frame. */
	void emit(RenderEvent event);
}
