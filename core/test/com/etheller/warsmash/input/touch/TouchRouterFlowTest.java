package com.etheller.warsmash.input.touch;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;

/**
 * Whole path from platform pointer events through the router to the screen that owns libGDX input, with a recording
 * screen in place of the game, so that the order and pen button settings are checked as the player meets them.
 */
class TouchRouterFlowTest {
	private static final class Screen implements InputProcessor, TouchTarget {
		final List<String> calls = new ArrayList<>();
		PointKind kind = PointKind.GROUND;
		boolean orderable = true;

		private static String name(final int button) {
			return button == Input.Buttons.LEFT ? "L" : button == Input.Buttons.RIGHT ? "R"
					: button == Input.Buttons.MIDDLE ? "M" : "?";
		}

		private String keys() {
			return (VirtualModifiers.isShiftDown() ? "+shift" : "") + (VirtualModifiers.isControlDown() ? "+ctrl" : "");
		}

		@Override
		public boolean touchDown(final int screenX, final int screenY, final int pointer, final int button) {
			this.calls.add("down" + name(button) + this.keys());
			return false;
		}

		@Override
		public boolean touchUp(final int screenX, final int screenY, final int pointer, final int button) {
			this.calls.add("up" + name(button) + this.keys());
			return false;
		}

		@Override
		public boolean touchDragged(final int screenX, final int screenY, final int pointer) {
			return false;
		}

		@Override
		public boolean mouseMoved(final int screenX, final int screenY) {
			return false;
		}

		@Override
		public boolean keyDown(final int keycode) {
			return false;
		}

		@Override
		public boolean keyUp(final int keycode) {
			return false;
		}

		@Override
		public boolean keyTyped(final char character) {
			return false;
		}

		@Override
		public boolean scrolled(final float amountX, final float amountY) {
			return false;
		}

		@Override
		public boolean touchCancelled(final int screenX, final int screenY, final int pointer, final int button) {
			return false;
		}

		@Override
		public PointKind pointKindAt(final int screenX, final int screenY) {
			return this.kind;
		}

		@Override
		public boolean hasOrderableSelection() {
			return this.orderable;
		}

		@Override
		public boolean hasActiveCommand() {
			return false;
		}

		@Override
		public boolean isPlacingBuilding() {
			return false;
		}

		@Override
		public boolean isOverClickableUi(final int screenX, final int screenY) {
			return false;
		}

		@Override
		public boolean isOverInventorySlot(final int screenX, final int screenY) {
			return false;
		}

		@Override
		public boolean isOverAutocastButton(final int screenX, final int screenY) {
			return false;
		}

		@Override
		public void screenToWorld(final int screenX, final int screenY, final float[] out) {
		}

		@Override
		public void worldToScreen(final float[] world, final float[] out) {
		}

		@Override
		public int touchOverlaySlotAt(final int screenX, final int screenY) {
			return -1;
		}

		@Override
		public void selectControlGroupSlot(final int slot) {
		}

		@Override
		public void assignControlGroupSlot(final int slot, final boolean add) {
		}

		@Override
		public void cancelPointerDrag() {
		}

		@Override
		public void endTouchHover() {
		}

		@Override
		public boolean skipCinematic() {
			return false;
		}
	}

	private final AtomicLong clock = new AtomicLong();
	private final Screen screen = new Screen();
	private Input savedInput;
	private TouchInputRouter router;

	@BeforeEach
	void setUp() {
		this.savedInput = Gdx.input;
		Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] { Input.class },
				(proxy, method, args) -> {
					if (method.getName().equals("getInputProcessor")) {
						return this.screen;
					}
					final Class<?> type = method.getReturnType();
					if (type == boolean.class) {
						return false;
					}
					if (type == int.class) {
						return 0;
					}
					if (type == float.class) {
						return 0f;
					}
					if (type == long.class) {
						return 0L;
					}
					return null;
				});
	}

	@AfterEach
	void tearDown() {
		Gdx.input = this.savedInput;
		TouchInputRouter.install(null);
		VirtualModifiers.setPenButtonHeld(false, PenButton.SHIFT);
	}

	private void start(final ControlScheme scheme, final TouchStyle style, final PenButton button) {
		final TouchSettings d = TouchSettings.defaults(1f, scheme);
		this.router = new TouchInputRouter(new TouchSettings(d.tapSlopPx, d.penSlopPx, d.tapMaxMs, d.longPressMs,
				d.twoFingerWindowMs, d.twoFingerTapMs, d.pinchStartRatio, d.pinchStepRatio, d.buildAimOffsetPx,
				d.ghostTapRadiusPx, d.palmTouchMajorPx, d.penFingerPolicy, d.palmRejection, scheme, style, 250, 32,
				button), this.clock::get, false);
		TouchInputRouter.install(this.router);
	}

	private void send(final PointerEvent.Tool tool, final PointerEvent.Action action, final float x, final float y,
			final int buttons, final long time) {
		this.clock.set(time);
		this.router.post(new PointerEvent(0, tool, action, x, y, buttons, 1, 10, 0, time));
		this.router.drain();
	}

	private void idle(final long time) {
		this.clock.set(time);
		this.router.drain();
	}

	private void finger(final PointerEvent.Action action, final long time) {
		send(PointerEvent.Tool.FINGER, action, 100, 100, 0, time);
	}

	@Test
	void doubleTapOnGroundGivesTheRightClick() {
		start(ControlScheme.TOUCH, TouchStyle.DOUBLE_TAP, PenButton.SHIFT);
		finger(PointerEvent.Action.DOWN, 1000);
		finger(PointerEvent.Action.UP, 1060);
		finger(PointerEvent.Action.DOWN, 1200);
		finger(PointerEvent.Action.UP, 1250);
		idle(2000);
		assertEquals(List.of("downL", "upL", "downR", "upR"), this.screen.calls);
	}

	@Test
	void doubleTapWhoseSecondTapLastsPastTheWindowStillGivesTheRightClick() {
		start(ControlScheme.TOUCH, TouchStyle.DOUBLE_TAP, PenButton.SHIFT);
		finger(PointerEvent.Action.DOWN, 1000);
		finger(PointerEvent.Action.UP, 1060);
		idle(1130);
		finger(PointerEvent.Action.DOWN, 1200);
		idle(1260);
		idle(1300);
		finger(PointerEvent.Action.UP, 1340);
		idle(2000);
		assertEquals(List.of("downL", "upL", "downR", "upR"), this.screen.calls);
	}

	@Test
	void doubleTapOnAnEnemyWhoseSecondTapLastsPastTheWindowGivesTheRightClick() {
		start(ControlScheme.TOUCH, TouchStyle.DOUBLE_TAP, PenButton.SHIFT);
		this.screen.kind = PointKind.ENEMY;
		finger(PointerEvent.Action.DOWN, 1000);
		finger(PointerEvent.Action.UP, 1060);
		idle(1130);
		finger(PointerEvent.Action.DOWN, 1200);
		idle(1260);
		idle(1300);
		finger(PointerEvent.Action.UP, 1340);
		idle(2000);
		assertEquals(List.of("downR", "upR"), this.screen.calls);
	}

	@Test
	void doubleTapOnAnOwnUnitStaysTwoLeftClicksWithAnOrderableSelection() {
		start(ControlScheme.TOUCH, TouchStyle.DOUBLE_TAP, PenButton.SHIFT);
		this.screen.kind = PointKind.OWN_UNIT;
		finger(PointerEvent.Action.DOWN, 1000);
		finger(PointerEvent.Action.UP, 1060);
		finger(PointerEvent.Action.DOWN, 1150);
		finger(PointerEvent.Action.UP, 1210);
		idle(2000);
		assertEquals(List.of("downL", "upL", "downL", "upL"), this.screen.calls);
	}

	@Test
	void doubleTapOnAnOwnUnitWhoseSecondTapLastsPastTheWindowStaysTwoLeftClicks() {
		start(ControlScheme.TOUCH, TouchStyle.DOUBLE_TAP, PenButton.SHIFT);
		this.screen.kind = PointKind.OWN_UNIT;
		finger(PointerEvent.Action.DOWN, 1000);
		finger(PointerEvent.Action.UP, 1060);
		idle(1130);
		finger(PointerEvent.Action.DOWN, 1200);
		idle(1300);
		finger(PointerEvent.Action.UP, 1340);
		idle(2000);
		assertEquals(List.of("downL", "upL", "downL", "upL"), this.screen.calls);
	}

	@Test
	void doubleTapOnAnEnemyGivesOnlyTheRightClick() {
		start(ControlScheme.TOUCH, TouchStyle.DOUBLE_TAP, PenButton.SHIFT);
		this.screen.kind = PointKind.ENEMY;
		finger(PointerEvent.Action.DOWN, 1000);
		finger(PointerEvent.Action.UP, 1060);
		finger(PointerEvent.Action.DOWN, 1200);
		finger(PointerEvent.Action.UP, 1250);
		idle(2000);
		assertEquals(List.of("downR", "upR"), this.screen.calls);
	}

	@Test
	void doubleTapStyleChosenLaterReachesTheRunningRouter() {
		start(ControlScheme.TOUCH, TouchStyle.TAP_HOLD, PenButton.SHIFT);
		this.router.setStyle(TouchStyle.DOUBLE_TAP);
		finger(PointerEvent.Action.DOWN, 1000);
		finger(PointerEvent.Action.UP, 1060);
		finger(PointerEvent.Action.DOWN, 1200);
		finger(PointerEvent.Action.UP, 1250);
		idle(2000);
		assertEquals(List.of("downL", "upL", "downR", "upR"), this.screen.calls);
	}

	@Test
	void tapHoldStyleKeepsTwoLeftClicks() {
		start(ControlScheme.TOUCH, TouchStyle.TAP_HOLD, PenButton.SHIFT);
		finger(PointerEvent.Action.DOWN, 1000);
		finger(PointerEvent.Action.UP, 1060);
		finger(PointerEvent.Action.DOWN, 1200);
		finger(PointerEvent.Action.UP, 1250);
		assertEquals(List.of("downL", "upL", "downL", "upL"), this.screen.calls);
	}

	private void pen(final PointerEvent.Action action, final int buttons, final long time) {
		send(PointerEvent.Tool.STYLUS, action, 100, 100, buttons, time);
	}

	@Test
	void penTapWithTheButtonHeldIsShiftClicked() {
		start(ControlScheme.TOUCH, TouchStyle.TAP_HOLD, PenButton.SHIFT);
		pen(PointerEvent.Action.HOVER_MOVE, 32, 1000);
		pen(PointerEvent.Action.DOWN, 32, 1010);
		pen(PointerEvent.Action.UP, 32, 1060);
		assertEquals(List.of("downL+shift", "upL+shift"), this.screen.calls);
		assertFalse(VirtualModifiers.isShiftDown());
	}

	@Test
	void heldPenTapOnAnEnemyKeepsItsShiftWhenTheWindowForTheSecondTapEnds() {
		start(ControlScheme.TOUCH, TouchStyle.DOUBLE_TAP, PenButton.SHIFT);
		this.screen.kind = PointKind.ENEMY;
		pen(PointerEvent.Action.DOWN, 32, 1010);
		pen(PointerEvent.Action.UP, 32, 1060);
		pen(PointerEvent.Action.HOVER_MOVE, 0, 1100);
		idle(2000);
		assertEquals(List.of("downL+shift", "upL+shift"), this.screen.calls);
		assertFalse(VirtualModifiers.isShiftDown());
	}

	@Test
	void penTapWithTheButtonReleasedJustBeforeLiftingIsStillShiftClicked() {
		start(ControlScheme.TOUCH, TouchStyle.TAP_HOLD, PenButton.SHIFT);
		pen(PointerEvent.Action.DOWN, 32, 1010);
		pen(PointerEvent.Action.UP, 0, 1060);
		assertEquals(List.of("downL+shift", "upL+shift"), this.screen.calls);
	}

	@Test
	void penTapWithTheButtonAsCtrlIsCtrlClicked() {
		start(ControlScheme.TOUCH, TouchStyle.TAP_HOLD, PenButton.CTRL);
		pen(PointerEvent.Action.DOWN, 2, 1010);
		pen(PointerEvent.Action.UP, 2, 1060);
		assertEquals(List.of("downL+ctrl", "upL+ctrl"), this.screen.calls);
	}

	@Test
	void penButtonPressedWhileHoveringStillAndNotMovingCountsAtOnce() {
		start(ControlScheme.TOUCH, TouchStyle.TAP_HOLD, PenButton.SHIFT);
		pen(PointerEvent.Action.HOVER_MOVE, 0, 1000);
		pen(PointerEvent.Action.BUTTONS, 32, 1100);
		assertTrue(VirtualModifiers.isShiftDown());
		pen(PointerEvent.Action.BUTTONS, 0, 1200);
		assertFalse(VirtualModifiers.isShiftDown());
	}

	@Test
	void penButtonHeldBeforeTheStrokeIsShiftWithTheMouseSchemeToo() {
		start(ControlScheme.MOUSE_KEYBOARD, TouchStyle.TAP_HOLD, PenButton.SHIFT);
		pen(PointerEvent.Action.DOWN, 32, 1010);
		pen(PointerEvent.Action.UP, 32, 1060);
		assertEquals(List.of("downL+shift", "upL+shift"), this.screen.calls);
	}

	@Test
	void penButtonAsRightClickTapsTheRightButton() {
		start(ControlScheme.TOUCH, TouchStyle.TAP_HOLD, PenButton.RIGHT_CLICK);
		pen(PointerEvent.Action.DOWN, 32, 1010);
		pen(PointerEvent.Action.UP, 32, 1060);
		assertEquals(List.of("downR", "upR"), this.screen.calls);
		this.screen.calls.clear();
		pen(PointerEvent.Action.DOWN, 0, 2010);
		pen(PointerEvent.Action.UP, 0, 2060);
		assertEquals(List.of("downL", "upL"), this.screen.calls);
	}

	@Test
	void penButtonAsRightClickWithTheMouseSchemeIsTheRightButton() {
		start(ControlScheme.MOUSE_KEYBOARD, TouchStyle.TAP_HOLD, PenButton.RIGHT_CLICK);
		pen(PointerEvent.Action.DOWN, 2, 1010);
		pen(PointerEvent.Action.UP, 2, 1060);
		assertEquals(List.of("downR", "upR"), this.screen.calls);
	}

	@Test
	void penButtonAsDragWithTheMouseSchemeKeepsATapWithoutMovementALeftClick() {
		start(ControlScheme.MOUSE_KEYBOARD, TouchStyle.TAP_HOLD, PenButton.DRAG);
		pen(PointerEvent.Action.DOWN, 32, 1010);
		pen(PointerEvent.Action.UP, 32, 1060);
		assertEquals(List.of("downL", "upL"), this.screen.calls);
	}

	@Test
	void penButtonAsDragWithTheMouseSchemePressesMiddleOnlyOnceTheTipMoves() {
		start(ControlScheme.MOUSE_KEYBOARD, TouchStyle.TAP_HOLD, PenButton.DRAG);
		pen(PointerEvent.Action.DOWN, 32, 1010);
		send(PointerEvent.Tool.STYLUS, PointerEvent.Action.MOVE, 101, 100, 32, 1020);
		assertEquals(List.of(), this.screen.calls);
		send(PointerEvent.Tool.STYLUS, PointerEvent.Action.MOVE, 160, 100, 32, 1030);
		send(PointerEvent.Tool.STYLUS, PointerEvent.Action.UP, 160, 100, 32, 1060);
		assertEquals(List.of("downM", "upM"), this.screen.calls);
	}

	@Test
	void rightClickIsOneOfTheNamedPenButtonActions() {
		assertEquals(PenButton.RIGHT_CLICK, PenButton.parse("rightclick", PenButton.SHIFT));
		assertEquals(3, PenButton.RIGHT_CLICK.ordinal());
	}

	@Test
	void penButtonChosenLaterReachesTheRunningRouter() {
		start(ControlScheme.TOUCH, TouchStyle.TAP_HOLD, PenButton.SHIFT);
		this.router.setPenButton(PenButton.CTRL);
		pen(PointerEvent.Action.DOWN, 32, 1010);
		pen(PointerEvent.Action.UP, 32, 1060);
		assertEquals(List.of("downL+ctrl", "upL+ctrl"), this.screen.calls);
	}

	@Test
	void theChoicesOfTheOptionsPageReachTheSettingsTheGameStartsWith(@org.junit.jupiter.api.io.TempDir final java.io.File folder)
			throws java.io.IOException {
		final java.io.File ini = new java.io.File(folder, "warsmash.ini");
		java.nio.file.Files.write(ini.toPath(), "[Emulator]\nEnableMusic=1\n\n[Touch]\nTapSlopDp=10\n"
				.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		final com.etheller.warsmash.launcher.EngineSettings engine = new com.etheller.warsmash.launcher.EngineSettings(
				folder, "p", ini);
		engine.setControlScheme(ControlScheme.TOUCH);
		engine.setTouchStyle(TouchStyle.DOUBLE_TAP);
		engine.setPenButton(PenButton.RIGHT_CLICK);
		final com.etheller.warsmash.units.DataTable table = new com.etheller.warsmash.units.DataTable(
				com.etheller.warsmash.util.StringBundle.EMPTY);
		try (java.io.InputStream stream = new java.io.FileInputStream(ini)) {
			table.readTXT(stream, true);
		}
		final TouchSettings settings = TouchSettings.fromIni(table.get("Touch"), 2f, ControlScheme.MOUSE_KEYBOARD);
		assertEquals(ControlScheme.TOUCH, settings.controlScheme);
		assertEquals(TouchStyle.DOUBLE_TAP, settings.touchStyle);
		assertEquals(PenButton.RIGHT_CLICK, settings.penButton);
		assertEquals(20f, settings.tapSlopPx);
	}
}
