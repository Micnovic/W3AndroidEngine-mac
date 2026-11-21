package com.etheller.warsmash.viewer5.handlers.w3x.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;

/**
 * An effect that has no model instance (its model is gone, or was never kept) must still be saved with what shows
 * it again. A render effect is made without a viewer or a model, as a save only reads its own fields.
 */
class SavedEffectTest {
	private static Object field(final Object target, final Class<?> type, final String name) throws Exception {
		final Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void set(final Object target, final String name, final Object value) throws Exception {
		final Field field = RenderSpellEffect.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static RenderSpellEffect effect(final MdxComplexInstance instance, final float x, final float y,
			final float yaw) throws Exception {
		final Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
		final RenderSpellEffect effect = (RenderSpellEffect) unsafe.allocateInstance(RenderSpellEffect.class);
		set(effect, "animationQueue", RenderSpellEffect.STAND_THEN_DEATH);
		set(effect, "requiredAnimationNames", EnumSet.noneOf(SecondaryTag.class));
		set(effect, "scriptSubAnimations", EnumSet.noneOf(SecondaryTag.class));
		set(effect, "scriptScale", new float[] { 1, 1, 1 });
		set(effect, "scriptQueuedSequences", new java.util.ArrayList<Integer>());
		set(effect, "sequenceLoopMode", SequenceLoopMode.MODEL_LOOP);
		set(effect, "modelInstance", instance);
		effect.setOrigin("Abilities/Spells/Test/Test.mdx", null, null, x, y, yaw);
		return effect;
	}

	private static MdxComplexInstance instance(final float x, final float y, final float z) throws Exception {
		final Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
		final MdxComplexInstance instance = (MdxComplexInstance) unsafe.allocateInstance(MdxComplexInstance.class);
		instance.localLocation = new Vector3(x, y, z);
		return instance;
	}

	@Test
	void pendingEffectIsSavedWithItsOwnFields() throws Exception {
		final RenderSpellEffect effect = effect(instance(10, 20, 5), 10, 20, 1.5f);
		final SavedEffect saved = SavedEffect.of(effect);
		assertNotNull(saved);
		assertEquals("Abilities/Spells/Test/Test.mdx", field(saved, SavedEffect.class, "modelName"));
		assertEquals(10f, field(saved, SavedEffect.class, "x"));
		assertEquals(20f, field(saved, SavedEffect.class, "y"));
		assertEquals(5f, field(saved, SavedEffect.class, "z"));
		assertFalse((boolean) field(saved, SavedEffect.class, "ended"));
	}

	@Test
	void effectWithoutInstanceIsSavedWithoutFailing() throws Exception {
		final RenderSpellEffect effect = effect(null, 30, 40, 0.5f);
		set(effect, "lastLocalZ", 7f);
		final SavedEffect saved = SavedEffect.of(effect);
		assertEquals("Abilities/Spells/Test/Test.mdx", field(saved, SavedEffect.class, "modelName"));
		assertEquals(30f, field(saved, SavedEffect.class, "x"));
		assertEquals(40f, field(saved, SavedEffect.class, "y"));
		assertEquals(0.5f, field(saved, SavedEffect.class, "yaw"));
		assertEquals(7f, field(saved, SavedEffect.class, "z"));
		assertTrue((boolean) field(saved, SavedEffect.class, "ended"));
	}

	@Test
	void scriptMovesAndScaleSurviveTheModelBeingGone() throws Exception {
		final RenderSpellEffect effect = effect(null, 1, 2, 0);
		effect.setScriptMatrixScale(2, 3, 4);
		effect.setScriptOrientation(0.25f, 0.5f, 0.75f);
		effect.setScriptPosition(50f, 60f, null, false);
		final SavedEffect saved = SavedEffect.of(effect);
		assertEquals(50f, field(saved, SavedEffect.class, "x"));
		assertEquals(60f, field(saved, SavedEffect.class, "y"));
		assertEquals(2f, field(saved, SavedEffect.class, "scaleX"));
		assertEquals(3f, field(saved, SavedEffect.class, "scaleY"));
		assertEquals(4f, field(saved, SavedEffect.class, "scaleZ"));
		assertEquals(0.5f, field(saved, SavedEffect.class, "pitch"));
		assertEquals(0.75f, field(saved, SavedEffect.class, "roll"));
		assertEquals(PrimaryTag.STAND, RenderSpellEffect.STAND_THEN_DEATH[0]);
	}
}
