package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxNode;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget.UnitAnimationListenerImpl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitAnimationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;

class BufferedAnimationListenerTest {
	private static final int HANDLE = 0x100001;
	private static final PrimaryTag[] PRIMARY = PrimaryTag.values();
	private static final SecondaryTag[] SECONDARY = SecondaryTag.values();

	private static final sun.misc.Unsafe UNSAFE;
	static {
		try {
			final Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			UNSAFE = (sun.misc.Unsafe) field.get(null);
		}
		catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** An instance with no model: enough for the real listener's tag handling, which never reads it. */
	private static final class NoModelInstance extends MdxComplexInstance {
		NoModelInstance() {
			super(null);
		}

		@Override
		public MdxNode inefficientlyGetNodeByNameSearch(final String name) {
			return null;
		}
	}

	private static UnitAnimationListenerImpl realListener() throws InstantiationException {
		return new UnitAnimationListenerImpl((MdxComplexInstance) UNSAFE.allocateInstance(NoModelInstance.class), 1,
				1);
	}

	private static CWidget widget(final int handle) throws ReflectiveOperationException {
		final CWidget widget = (CWidget) UNSAFE.allocateInstance(CUnit.class);
		final Field field = CWidget.class.getDeclaredField("handleId");
		field.setAccessible(true);
		field.setInt(widget, handle);
		return widget;
	}

	private static final class ListSink implements RenderEventSink {
		final List<RenderEvent> events = new ArrayList<>();

		@Override
		public RenderEvent begin(final int code) {
			final RenderEvent event = new RenderEvent();
			event.code = code;
			event.tick = 3;
			event.order = this.events.size();
			return event;
		}

		@Override
		public void emit(final RenderEvent event) {
			this.events.add(event);
		}
	}

	/** Logs every call, with tag sets as sorted text, and answers the tag calls like the real listener does. */
	private static final class Recorder implements CUnitAnimationListener {
		final List<String> log = new ArrayList<>();
		final EnumSet<SecondaryTag> tags = EnumSet.noneOf(SecondaryTag.class);

		private static String s(final EnumSet<SecondaryTag> set) {
			return set == null ? "null" : set.toString();
		}

		@Override
		public void playAnimation(final boolean force, final PrimaryTag name, final EnumSet<SecondaryTag> secondary,
				final float speedRatio, final boolean rarity) {
			this.log.add("play " + force + " " + name + " " + s(secondary) + " " + speedRatio + " " + rarity);
		}

		@Override
		public void playAnimationWithDuration(final boolean force, final PrimaryTag name,
				final EnumSet<SecondaryTag> secondary, final float duration, final boolean rarity) {
			this.log.add("playDuration " + force + " " + name + " " + s(secondary) + " " + duration + " " + rarity);
		}

		@Override
		public void playWalkAnimation(final boolean force, final float speed, final boolean rarity) {
			this.log.add("walk " + force + " " + speed + " " + rarity);
		}

		@Override
		public void playAnimation(final boolean force, final int sequenceIndex, final float speedRatio,
				final boolean rarity) {
			this.log.add("sequence " + force + " " + sequenceIndex + " " + speedRatio + " " + rarity);
		}

		@Override
		public void queueAnimation(final PrimaryTag name, final EnumSet<SecondaryTag> secondary,
				final boolean rarity) {
			this.log.add("queue " + name + " " + s(secondary) + " " + rarity);
		}

		@Override
		public void playStopAnimation() {
			this.log.add("stop");
		}

		@Override
		public boolean addSecondaryTag(final SecondaryTag tag) {
			this.log.add("add " + tag);
			return this.tags.add(tag);
		}

		@Override
		public boolean removeSecondaryTag(final SecondaryTag tag) {
			this.log.add("remove " + tag);
			return this.tags.remove(tag);
		}

		@Override
		public void forceResetCurrentAnimation() {
			this.log.add("reset");
		}

		@Override
		public void replayEndedAnimation() {
			this.log.add("replayEnded");
		}

		@Override
		public EnumSet<SecondaryTag> getSecondaryTags() {
			return this.tags;
		}

		private static String t(final AbilityTarget target) {
			if (target instanceof CWidget) {
				return "w" + ((CWidget) target).getHandleId();
			}
			return "p" + target.getX() + "," + target.getY();
		}

		@Override
		public void lockTurretFacing(final AbilityTarget target) {
			this.log.add("lockTurret " + t(target));
		}

		@Override
		public void clearTurretFacing() {
			this.log.add("clearTurret");
		}

		@Override
		public void lockHeadFacing(final AbilityTarget target) {
			this.log.add("lockHead " + t(target));
		}

		@Override
		public void clearHeadFacing() {
			this.log.add("clearHead");
		}
	}

	private static EnumSet<SecondaryTag> randomSet(final Random random) {
		final EnumSet<SecondaryTag> set = EnumSet.noneOf(SecondaryTag.class);
		final int count = random.nextInt(4) == 0 ? random.nextInt(SECONDARY.length + 1) : random.nextInt(4);
		for (int i = 0; i < count; i++) {
			set.add(SECONDARY[random.nextInt(SECONDARY.length)]);
		}
		if (random.nextInt(8) == 0) {
			// The ends of the mask: first and last tag, across the 32-bit split.
			set.add(SECONDARY[0]);
			set.add(SECONDARY[SECONDARY.length - 1]);
			set.add(SECONDARY[31]);
			set.add(SECONDARY[32]);
		}
		return set;
	}

	private static PrimaryTag randomPrimary(final Random random) {
		final int n = random.nextInt(PRIMARY.length + 1);
		return n == PRIMARY.length ? null : PRIMARY[n];
	}

	private static float randomFloat(final Random random) {
		return random.nextInt(5) == 0 ? 0 : (random.nextFloat() - 0.3f) * 1000f;
	}

	/** One random call on the listener; returns what the call returned (or null) with the tag set after it. */
	private static String call(final CUnitAnimationListener l, final Random random, final Map<Integer, CWidget> widgets,
			final boolean everything) {
		final int kind = random.nextInt(everything ? 16 : 4);
		switch (kind) {
		case 0: {
			final SecondaryTag tag = SECONDARY[random.nextInt(SECONDARY.length)];
			return "add " + l.addSecondaryTag(tag);
		}
		case 1: {
			final SecondaryTag tag = SECONDARY[random.nextInt(SECONDARY.length)];
			return "remove " + l.removeSecondaryTag(tag);
		}
		case 2:
			return "tags " + l.getSecondaryTags();
		case 3: {
			final SecondaryTag tag = SECONDARY[random.nextInt(SECONDARY.length)];
			// Added twice in a row: the second call must report no change.
			return "add2 " + l.addSecondaryTag(tag) + " " + l.addSecondaryTag(tag);
		}
		case 4: {
			final EnumSet<SecondaryTag> set = randomSet(random);
			l.playAnimation(random.nextBoolean(), randomPrimary(random), set, randomFloat(random), random.nextBoolean());
			set.clear();
			return null;
		}
		case 5: {
			final EnumSet<SecondaryTag> set = randomSet(random);
			l.playAnimationWithDuration(random.nextBoolean(), randomPrimary(random), set, randomFloat(random),
					random.nextBoolean());
			set.add(SECONDARY[0]);
			return null;
		}
		case 6:
			l.playWalkAnimation(random.nextBoolean(), randomFloat(random), random.nextBoolean());
			return null;
		case 7:
			l.playAnimation(random.nextBoolean(), random.nextInt(40) - 2, randomFloat(random), random.nextBoolean());
			return null;
		case 8: {
			final EnumSet<SecondaryTag> set = randomSet(random);
			l.queueAnimation(randomPrimary(random), set, random.nextBoolean());
			set.clear();
			return null;
		}
		case 9:
			l.playStopAnimation();
			return null;
		case 10:
			l.forceResetCurrentAnimation();
			return null;
		case 11:
			l.replayEndedAnimation();
			return null;
		case 12:
			l.lockTurretFacing(target(random, widgets));
			return null;
		case 13:
			l.clearTurretFacing();
			return null;
		case 14:
			l.lockHeadFacing(target(random, widgets));
			return null;
		default:
			l.clearHeadFacing();
			return null;
		}
	}

	private static AbilityTarget target(final Random random, final Map<Integer, CWidget> widgets) {
		if (random.nextBoolean()) {
			return widgets.get(HANDLE + 1 + random.nextInt(widgets.size()));
		}
		return new AbilityPointTarget(randomFloat(random), randomFloat(random));
	}

	private static Map<Integer, CWidget> widgets() throws ReflectiveOperationException {
		final Map<Integer, CWidget> widgets = new HashMap<>();
		for (int i = 1; i <= 5; i++) {
			widgets.put(HANDLE + i, widget(HANDLE + i));
		}
		return widgets;
	}

	/**
	 * Tag calls on the proxy and on the real listener: every return value and the set after every call are equal, and
	 * the events replayed onto a second real listener leave it with the same tags.
	 */
	@Test
	void tagReturnsEqualTheRealListener() throws Exception {
		for (int seed = 0; seed < 300; seed++) {
			final ListSink sink = new ListSink();
			final BufferedAnimationListener proxy = new BufferedAnimationListener(sink, HANDLE);
			final UnitAnimationListenerImpl real = realListener();
			final UnitAnimationListenerImpl replayed = realListener();
			final Random a = new Random(seed);
			final Random b = new Random(seed);
			final Map<Integer, CWidget> widgets = widgets();
			for (int i = 0; i < 200; i++) {
				final String fromProxy = call(proxy, a, widgets, false);
				final String fromReal = call(real, b, widgets, false);
				assertEquals(fromReal, fromProxy, "seed " + seed + " call " + i);
				assertEquals(real.getSecondaryTags(), proxy.getSecondaryTags(), "seed " + seed + " call " + i);
			}
			final BufferedAnimationListener.Player player = new BufferedAnimationListener.Player(
					new BufferedAnimationListener.Player.Lookup() {
						@Override
						public CUnitAnimationListener listener(final int handle) {
							return handle == HANDLE ? replayed : null;
						}

						@Override
						public CWidget widget(final int handle) {
							return widgets.get(handle);
						}
					});
			for (final RenderEvent event : sink.events) {
				player.apply(event);
			}
			assertEquals(real.getSecondaryTags(), replayed.getSecondaryTags(), "replayed, seed " + seed);
		}
	}

	/**
	 * Every call, with arguments the caller changes afterwards: the proxy answers like a listener that applies the
	 * calls itself, and replaying its events gives the same call sequence (same arguments) as calling that listener.
	 */
	@Test
	void replayGivesTheSameCallSequence() throws Exception {
		for (int seed = 0; seed < 300; seed++) {
			final ListSink sink = new ListSink();
			final BufferedAnimationListener proxy = new BufferedAnimationListener(sink, HANDLE);
			final Recorder direct = new Recorder();
			final Recorder replayed = new Recorder();
			final Random a = new Random(seed);
			final Random b = new Random(seed);
			final Map<Integer, CWidget> widgets = widgets();
			for (int i = 0; i < 300; i++) {
				final String fromProxy = call(proxy, a, widgets, true);
				final String fromDirect = call(direct, b, widgets, true);
				assertEquals(fromDirect, fromProxy, "seed " + seed + " call " + i);
			}
			final BufferedAnimationListener.Player player = new BufferedAnimationListener.Player(
					new BufferedAnimationListener.Player.Lookup() {
						@Override
						public CUnitAnimationListener listener(final int handle) {
							return handle == HANDLE ? replayed : null;
						}

						@Override
						public CWidget widget(final int handle) {
							return widgets.get(handle);
						}
					});
			int order = 0;
			for (final RenderEvent event : sink.events) {
				assertEquals(order++, event.order);
				assertEquals(true, BufferedAnimationListener.isAnimationEvent(event.code));
				assertEquals(HANDLE, event.a);
				player.apply(event);
			}
			assertEquals(direct.log, replayed.log, "seed " + seed);
		}
	}

	@Test
	void seededTagsCountForReturnsButSendNothing() {
		final ListSink sink = new ListSink();
		final BufferedAnimationListener proxy = new BufferedAnimationListener(sink, HANDLE);
		proxy.seedSecondaryTags(EnumSet.of(SecondaryTag.WORK));
		assertEquals(0, sink.events.size());
		assertEquals(false, proxy.addSecondaryTag(SecondaryTag.WORK));
		assertEquals(true, proxy.removeSecondaryTag(SecondaryTag.WORK));
		assertNotSame(null, proxy.getSecondaryTags());
	}

	@Test
	void eventToAMissingPeerIsDropped() {
		final ListSink sink = new ListSink();
		final BufferedAnimationListener proxy = new BufferedAnimationListener(sink, HANDLE);
		proxy.playStopAnimation();
		new BufferedAnimationListener.Player(new BufferedAnimationListener.Player.Lookup() {
			@Override
			public CUnitAnimationListener listener(final int handle) {
				return null;
			}

			@Override
			public CWidget widget(final int handle) {
				return null;
			}
		}).apply(sink.events.get(0));
	}
}
