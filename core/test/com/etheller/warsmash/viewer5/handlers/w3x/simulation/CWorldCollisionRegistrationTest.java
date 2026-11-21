package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;

/**
 * A unit leaves and moves out of the collision trees it was put in, whatever its movement type or death state is by
 * then: a snared unit or one whose move ability is gone (movement type DISABLED), and a hero revived while its old
 * collision was still registered.
 */
class CWorldCollisionRegistrationTest {
	private static final float SIZE = 2048f;

	private static CWorldCollision collision() {
		return new CWorldCollision(new Rectangle(0, 0, SIZE, SIZE), 64f);
	}

	private static CUnit unit(final float x, final float y) {
		final CUnitType type = TestObjects.allocate(CUnitType.class);
		TestObjects.set(type, "collisionSize", 16f);
		TestObjects.set(type, "movementType", MovementType.FOOT);
		final CUnit unit = TestObjects.allocate(CUnit.class);
		TestObjects.set(unit, "unitType", type);
		TestObjects.set(unit, "placement", new CUnitPlacementState());
		TestObjects.set(unit, "death", new CUnitDeathState());
		TestObjects.set(unit, "movement", new CUnitMovementState());
		TestObjects.set(unit, "moveBehavior", TestObjects.allocate(CBehaviorMove.class));
		unit.setX(x);
		unit.setY(y);
		return unit;
	}

	private static void move(final CWorldCollision collision, final CUnit unit, final float x, final float y) {
		final float prevX = unit.getX();
		final float prevY = unit.getY();
		unit.setX(x);
		unit.setY(y);
		collision.translate(unit, x - prevX, y - prevY);
	}

	private static boolean blocksGround(final CWorldCollision collision, final float x, final float y) {
		return collision.intersectsAnythingOtherThan(new Rectangle(x - 8, y - 8, 16, 16), null, MovementType.FOOT,
				false);
	}

	private static List<CUnit> corpsesAt(final CWorldCollision collision, final float x, final float y) {
		final List<CUnit> found = new ArrayList<>();
		collision.enumCorpsesInRect(new Rectangle(x - 8, y - 8, 16, 16), (corpse) -> {
			found.add(corpse);
			return false;
		});
		return found;
	}

	private static List<CUnit> unitsAt(final CWorldCollision collision, final float x, final float y) {
		final List<CUnit> found = new ArrayList<>();
		collision.enumUnitsInRect(new Rectangle(x - 8, y - 8, 16, 16), (enumUnit) -> {
			found.add(enumUnit);
			return false;
		});
		return found;
	}

	@Test
	void snaredUnitMovesAndLeavesTheGroundTree() {
		final CWorldCollision collision = collision();
		final CUnit unit = unit(300, 300);
		collision.addUnit(unit);
		assertTrue(blocksGround(collision, 300, 300));

		unit.movement.moveDisabled = true;
		assertEquals(MovementType.DISABLED, unit.getMovementType());
		move(collision, unit, 1200, 1200);
		assertFalse(blocksGround(collision, 300, 300), "ground collision left behind where the snare began");
		assertTrue(blocksGround(collision, 1200, 1200), "a snared unit still blocks where it is");

		collision.removeUnit(unit);
		assertFalse(blocksGround(collision, 300, 300));
		assertFalse(blocksGround(collision, 1200, 1200), "a removed snared unit still blocks");
	}

	@Test
	void unitWithoutMoveAbilityLeavesTheGroundTree() {
		final CWorldCollision collision = collision();
		final CUnit unit = unit(300, 300);
		collision.addUnit(unit);

		TestObjects.set(unit, "moveBehavior", null);
		assertEquals(MovementType.DISABLED, unit.getMovementType());
		collision.removeUnit(unit);
		assertFalse(blocksGround(collision, 300, 300), "a removed unit without 'Amov' still blocks");

		// Given its move ability back and put in again, it is in the ground tree once.
		TestObjects.set(unit, "moveBehavior", TestObjects.allocate(CBehaviorMove.class));
		collision.addUnit(unit);
		collision.removeUnit(unit);
		assertFalse(blocksGround(collision, 300, 300));
	}

	@Test
	void unitPutInWhileDisabledEntersTheGroundTreeOnceItMovesAgain() {
		final CWorldCollision collision = collision();
		final CUnit unit = unit(300, 300);
		unit.movement.moveDisabled = true;
		collision.addUnit(unit);
		assertFalse(blocksGround(collision, 300, 300));

		unit.movement.moveDisabled = false;
		move(collision, unit, 600, 600);
		assertTrue(blocksGround(collision, 600, 600));
		collision.removeUnit(unit);
		assertFalse(blocksGround(collision, 600, 600));
	}

	/** ReviveHero inside the hero's own death trigger: its living collision is still registered. */
	@Test
	void heroRevivedBeforeItsCollisionWasTakenOut() {
		final CWorldCollision collision = collision();
		final CUnit hero = unit(300, 300);
		collision.addUnit(hero);

		revive(collision, hero, 1200, 1200);
		assertFalse(blocksGround(collision, 300, 300), "revived hero still blocks where it died");
		assertTrue(unitsAt(collision, 300, 300).isEmpty());
		assertTrue(blocksGround(collision, 1200, 1200));
		assertEquals(1, unitsAt(collision, 1200, 1200).size());

		collision.removeUnit(hero);
		assertFalse(blocksGround(collision, 300, 300));
		assertFalse(blocksGround(collision, 1200, 1200));
		assertTrue(unitsAt(collision, 300, 300).isEmpty());
		assertTrue(unitsAt(collision, 1200, 1200).isEmpty());
	}

	/** A raisable hero's bones are put in the corpse tree; reviving it must take them out of there. */
	@Test
	void heroRevivedFromItsBones() {
		final CWorldCollision collision = collision();
		final CUnit hero = unit(300, 300);
		collision.addUnit(hero);
		collision.removeUnit(hero);
		hero.death.corpse = true;
		hero.death.boneCorpse = true;
		collision.addUnit(hero);
		assertEquals(1, corpsesAt(collision, 300, 300).size());

		revive(collision, hero, 1200, 1200);
		assertTrue(corpsesAt(collision, 300, 300).isEmpty(), "bones of a revived hero still lie where it died");
		assertFalse(blocksGround(collision, 300, 300));
		assertTrue(blocksGround(collision, 1200, 1200));

		collision.removeUnit(hero);
		assertFalse(blocksGround(collision, 1200, 1200));
		assertTrue(unitsAt(collision, 1200, 1200).isEmpty());
		assertTrue(corpsesAt(collision, 300, 300).isEmpty());
	}

	/** The collision steps of CUnitProduction.reviveHeroAtPoint, in its order. */
	private static void revive(final CWorldCollision collision, final CUnit hero, final float x, final float y) {
		collision.removeUnit(hero);
		hero.death.corpse = false;
		hero.death.boneCorpse = false;
		hero.death.freshCorpse = false;
		hero.setX(x);
		hero.setY(y);
		collision.addUnit(hero);
		move(collision, hero, x, y);
	}

	/** The order the revive code had: put in again where it now is without being taken out first. */
	@Test
	void addingARegisteredUnitAgainReplacesItsRegistration() {
		final CWorldCollision collision = collision();
		final CUnit hero = unit(300, 300);
		collision.addUnit(hero);
		collision.removeUnit(hero);
		hero.death.corpse = true;
		hero.death.boneCorpse = true;
		collision.addUnit(hero);

		hero.death.corpse = false;
		hero.death.boneCorpse = false;
		hero.setX(1200);
		hero.setY(1200);
		collision.addUnit(hero);
		move(collision, hero, 1200, 1200);
		assertTrue(corpsesAt(collision, 300, 300).isEmpty());
		assertFalse(blocksGround(collision, 300, 300));
		assertTrue(blocksGround(collision, 1200, 1200));
		assertEquals(1, unitsAt(collision, 1200, 1200).size());

		collision.removeUnit(hero);
		assertFalse(blocksGround(collision, 1200, 1200));
		assertTrue(unitsAt(collision, 1200, 1200).isEmpty());
	}
}
