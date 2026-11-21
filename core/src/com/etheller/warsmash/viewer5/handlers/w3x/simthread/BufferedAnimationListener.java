package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitAnimationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;

/**
 * The simulation's animation listener of a widget while the real one (which drives the model instance) lives on the
 * render thread. Every call is recorded as a {@link RenderEvent} naming the widget's handle, and {@link Player} applies
 * it to the real listener in order. The simulation never touches the render peer: the only things it reads back are the
 * secondary tags and whether adding or removing one changed them, and those are answered from {@link #getSecondaryTags()},
 * a set this proxy keeps itself by applying the same add / remove rules as the real listener.
 * <p>
 * Writer: the simulation thread only. The events carry copies (tag sets as bit masks), never the simulation's sets.
 * <p>
 * Tags the render side adds to the real listener on its own (a unit type's required tags, swimming, working) are not
 * seen here. Whoever creates the peer calls {@link #seedSecondaryTags} for the ones that are also meant to count for
 * the simulation's return values, as the shared set did before.
 */
public final class BufferedAnimationListener implements CUnitAnimationListener {
	// Shared codes: RenderEvent.ANIMATION_PLAY, ANIMATION_QUEUE, SECONDARY_TAG_ADDED, SECONDARY_TAG_REMOVED
	// (ANIMATION_PLAY and ANIMATION_QUEUE: a = handle, b = primary tag or -1, c = flags | high tag bits, d = low tag
	// bits, f0 = speed ratio; the tag events: a = handle, b = tag). The other calls have codes of their own.
	/** a = handle, b = primary tag (-1 none), c = flags | high tag bits, d = low tag bits, f0 = duration. */
	public static final int ANIMATION_PLAY_DURATION = 200;
	/** a = handle, c = flags, f0 = movement speed. */
	public static final int ANIMATION_PLAY_WALK = 201;
	/** a = handle, b = sequence index, c = flags, f0 = speed ratio. */
	public static final int ANIMATION_PLAY_SEQUENCE = 202;
	/** a = handle. */
	public static final int ANIMATION_PLAY_STOP = 203;
	/** a = handle. */
	public static final int ANIMATION_RESET = 204;
	/** a = handle. */
	public static final int ANIMATION_REPLAY_ENDED = 205;
	/** a = handle, b = target kind ({@link #TARGET_WIDGET}, {@link #TARGET_POINT}), c = widget handle, f0/f1 = x/y. */
	public static final int LOCK_TURRET = 206;
	/** a = handle. */
	public static final int CLEAR_TURRET = 207;
	/** Like {@link #LOCK_TURRET}. */
	public static final int LOCK_HEAD = 208;
	/** a = handle. */
	public static final int CLEAR_HEAD = 209;
	/** a = handle: the widget's peer has something to do again (see {@link CUnitAnimationListener#wake}). */
	public static final int WAKE = 210;

	public static final int TARGET_WIDGET = 0;
	public static final int TARGET_POINT = 1;

	// c of the play and queue events: bit 0 force, bit 1 allow rarity variations, the rest the high tag mask bits.
	private static final int FLAG_FORCE = 1;
	private static final int FLAG_RARITY = 2;
	private static final int MASK_SHIFT = 2;


	static {
		if (SecondaryTag.VALUES.length > 32 + (32 - MASK_SHIFT)) {
			throw new IllegalStateException("Secondary tags no longer fit an event");
		}
	}

	private final RenderEventSink sink;
	private final int handle;
	private final EnumSet<SecondaryTag> secondaryTags = EnumSet.noneOf(SecondaryTag.class);

	/** @param handle handle id of the unit or destructable the real listener belongs to */
	public BufferedAnimationListener(final RenderEventSink sink, final int handle) {
		this.sink = sink;
		this.handle = handle;
	}

	/** Sets tags in the mirror without an event: the render side applies them to the real listener itself. */
	public void seedSecondaryTags(final Iterable<SecondaryTag> tags) {
		for (final SecondaryTag tag : tags) {
			this.secondaryTags.add(tag);
		}
	}

	static long mask(final EnumSet<SecondaryTag> tags) {
		long mask = 0;
		if (tags != null) {
			for (final SecondaryTag tag : tags) {
				mask |= 1L << tag.ordinal();
			}
		}
		return mask;
	}

	private static int flags(final boolean force, final boolean rarity, final long mask) {
		return (force ? FLAG_FORCE : 0) | (rarity ? FLAG_RARITY : 0) | ((int) (mask >>> 32) << MASK_SHIFT);
	}

	private RenderEvent begin(final int code) {
		final RenderEvent event = this.sink.begin(code);
		event.a = this.handle;
		return event;
	}

	private static int primary(final PrimaryTag tag) {
		return tag == null ? -1 : tag.ordinal();
	}

	@Override
	public void playAnimation(final boolean force, final PrimaryTag animationName,
			final EnumSet<SecondaryTag> secondaryAnimationTags, final float speedRatio,
			final boolean allowRarityVariations) {
		final RenderEvent event = begin(RenderEvent.ANIMATION_PLAY);
		final long mask = mask(secondaryAnimationTags);
		event.b = primary(animationName);
		event.c = flags(force, allowRarityVariations, mask);
		event.d = (int) mask;
		event.f0 = speedRatio;
		this.sink.emit(event);
	}

	@Override
	public void playAnimationWithDuration(final boolean force, final PrimaryTag animationName,
			final EnumSet<SecondaryTag> secondaryAnimationTags, final float duration,
			final boolean allowRarityVariations) {
		final RenderEvent event = begin(ANIMATION_PLAY_DURATION);
		final long mask = mask(secondaryAnimationTags);
		event.b = primary(animationName);
		event.c = flags(force, allowRarityVariations, mask);
		event.d = (int) mask;
		event.f0 = duration;
		this.sink.emit(event);
	}

	@Override
	public void playWalkAnimation(final boolean force, final float currentMovementSpeed,
			final boolean allowRarityVariations) {
		final RenderEvent event = begin(ANIMATION_PLAY_WALK);
		event.c = flags(force, allowRarityVariations, 0);
		event.f0 = currentMovementSpeed;
		this.sink.emit(event);
	}

	@Override
	public void playAnimation(final boolean force, final int sequenceIndex, final float speedRatio,
			final boolean allowRarityVariations) {
		final RenderEvent event = begin(ANIMATION_PLAY_SEQUENCE);
		event.b = sequenceIndex;
		event.c = flags(force, allowRarityVariations, 0);
		event.f0 = speedRatio;
		this.sink.emit(event);
	}

	@Override
	public void queueAnimation(final PrimaryTag animationName, final EnumSet<SecondaryTag> secondaryAnimationTags,
			final boolean allowRarityVariations) {
		final RenderEvent event = begin(RenderEvent.ANIMATION_QUEUE);
		final long mask = mask(secondaryAnimationTags);
		event.b = primary(animationName);
		event.c = flags(false, allowRarityVariations, mask);
		event.d = (int) mask;
		this.sink.emit(event);
	}

	@Override
	public void playStopAnimation() {
		this.sink.emit(begin(ANIMATION_PLAY_STOP));
	}

	/** Answered from the mirror; the event is recorded either way, as the real listener applies the same rule. */
	@Override
	public boolean addSecondaryTag(final SecondaryTag secondaryTag) {
		final boolean changed = this.secondaryTags.add(secondaryTag);
		final RenderEvent event = begin(RenderEvent.SECONDARY_TAG_ADDED);
		event.b = secondaryTag.ordinal();
		this.sink.emit(event);
		return changed;
	}

	@Override
	public boolean removeSecondaryTag(final SecondaryTag secondaryTag) {
		final boolean changed = this.secondaryTags.remove(secondaryTag);
		final RenderEvent event = begin(RenderEvent.SECONDARY_TAG_REMOVED);
		event.b = secondaryTag.ordinal();
		this.sink.emit(event);
		return changed;
	}

	@Override
	public void forceResetCurrentAnimation() {
		this.sink.emit(begin(ANIMATION_RESET));
	}

	@Override
	public void replayEndedAnimation() {
		this.sink.emit(begin(ANIMATION_REPLAY_ENDED));
	}

	/** The mirror, live like the real listener's set. */
	@Override
	public EnumSet<SecondaryTag> getSecondaryTags() {
		return this.secondaryTags;
	}

	private void lock(final int code, final AbilityTarget target) {
		final RenderEvent event = begin(code);
		if (target instanceof CWidget) {
			event.b = TARGET_WIDGET;
			event.c = ((CWidget) target).getHandleId();
		}
		else {
			event.b = TARGET_POINT;
			event.f0 = target.getX();
			event.f1 = target.getY();
		}
		this.sink.emit(event);
	}

	@Override
	public void lockTurretFacing(final AbilityTarget target) {
		lock(LOCK_TURRET, target);
	}

	@Override
	public void clearTurretFacing() {
		this.sink.emit(begin(CLEAR_TURRET));
	}

	@Override
	public void lockHeadFacing(final AbilityTarget target) {
		lock(LOCK_HEAD, target);
	}

	@Override
	public void clearHeadFacing() {
		this.sink.emit(begin(CLEAR_HEAD));
	}

	@Override
	public void wake() {
		this.sink.emit(begin(WAKE));
	}

	/** True for the event codes {@link Player} applies. */
	public static boolean isAnimationEvent(final int code) {
		return (code >= RenderEvent.ANIMATION_PLAY && code <= RenderEvent.SECONDARY_TAG_REMOVED)
				|| (code >= ANIMATION_PLAY_DURATION && code <= WAKE);
	}

	/**
	 * Render thread: applies the events of {@link BufferedAnimationListener}s to the real listeners. The code that owns
	 * the peers hands it every event for which {@link BufferedAnimationListener#isAnimationEvent} is true.
	 */
	public static final class Player {
		/** The real listener and the simulation objects of the handles an event names. */
		public interface Lookup {
			/** Null when the widget has no peer (any more); the event is then dropped. */
			CUnitAnimationListener listener(int handle);

			CWidget widget(int handle);
		}

		private final Lookup lookup;
		/** The sets handed to the real listener are never changed, so one per mask is enough. */
		private final Map<Long, EnumSet<SecondaryTag>> sets = new HashMap<>();

		public Player(final Lookup lookup) {
			this.lookup = lookup;
		}

		private EnumSet<SecondaryTag> tags(final int low, final int c) {
			final long mask = (low & 0xFFFFFFFFL) | ((long) (c >>> MASK_SHIFT) << 32);
			EnumSet<SecondaryTag> set = this.sets.get(mask);
			if (set == null) {
				set = EnumSet.noneOf(SecondaryTag.class);
				for (final SecondaryTag tag : SecondaryTag.VALUES) {
					if ((mask & (1L << tag.ordinal())) != 0) {
						set.add(tag);
					}
				}
				this.sets.put(mask, set);
			}
			return set;
		}

		private static PrimaryTag primary(final int ordinal) {
			return ordinal < 0 ? null : PrimaryTag.VALUES[ordinal];
		}

		private AbilityTarget target(final RenderEvent e) {
			if (e.b == TARGET_WIDGET) {
				return this.lookup.widget(e.c);
			}
			return new AbilityPointTarget(e.f0, e.f1);
		}

		public void apply(final RenderEvent e) {
			final CUnitAnimationListener listener = this.lookup.listener(e.a);
			if (listener == null) {
				return;
			}
			final boolean force = (e.c & FLAG_FORCE) != 0;
			final boolean rarity = (e.c & FLAG_RARITY) != 0;
			switch (e.code) {
			case RenderEvent.ANIMATION_PLAY:
				listener.playAnimation(force, primary(e.b), tags(e.d, e.c), e.f0, rarity);
				break;
			case ANIMATION_PLAY_DURATION:
				listener.playAnimationWithDuration(force, primary(e.b), tags(e.d, e.c), e.f0, rarity);
				break;
			case ANIMATION_PLAY_WALK:
				listener.playWalkAnimation(force, e.f0, rarity);
				break;
			case ANIMATION_PLAY_SEQUENCE:
				listener.playAnimation(force, e.b, e.f0, rarity);
				break;
			case RenderEvent.ANIMATION_QUEUE:
				listener.queueAnimation(primary(e.b), tags(e.d, e.c), rarity);
				break;
			case ANIMATION_PLAY_STOP:
				listener.playStopAnimation();
				break;
			case RenderEvent.SECONDARY_TAG_ADDED:
				listener.addSecondaryTag(SecondaryTag.VALUES[e.b]);
				break;
			case RenderEvent.SECONDARY_TAG_REMOVED:
				listener.removeSecondaryTag(SecondaryTag.VALUES[e.b]);
				break;
			case ANIMATION_RESET:
				listener.forceResetCurrentAnimation();
				break;
			case ANIMATION_REPLAY_ENDED:
				listener.replayEndedAnimation();
				break;
			case LOCK_TURRET:
				listener.lockTurretFacing(target(e));
				break;
			case CLEAR_TURRET:
				listener.clearTurretFacing();
				break;
			case LOCK_HEAD:
				listener.lockHeadFacing(target(e));
				break;
			case CLEAR_HEAD:
				listener.clearHeadFacing();
				break;
			case WAKE:
				listener.wake();
				break;
			default:
				throw new IllegalArgumentException("Not an animation event: " + e.code);
			}
		}
	}
}
