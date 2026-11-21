package com.etheller.warsmash.parsers.jass;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.parsers.jass.triggers.LocationJass;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.CustomCameraSetup;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.LocalUiLink;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CBlendMode;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CCameraField;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CTexMapFlags;

/**
 * Camera, camera setup and cinematic filter natives.
 */
final class CameraNatives extends CommonNatives {
	/** How long reads go to the render thread after a command that moves the camera at its own rate. */
	private static final float UNTIMED_MOVE_SECONDS = 8f;
	private static final float FOLLOW_SECONDS = 30f;
	private boolean cameraAllowsHotkeyTargetLock = true;

	CameraNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		registerCamera();
		registerCameraSetups();
		registerCineFilter();
	}

	/**
	 * The setup the camera is given. The camera keeps the object it is handed and the script goes on changing its own,
	 * so a camera on another thread gets a copy; the same thread keeps getting the object itself.
	 */
	private CustomCameraSetup appliedSetup(final CustomCameraSetup setup) {
		if ((setup == null) || !this.link.isQueued()) {
			return setup;
		}
		final CustomCameraSetup copy = new CustomCameraSetup(setup.getAoa(), setup.getFov(), setup.getRotation(),
				setup.getRoll(), setup.getDistance(), setup.getFarZ(), setup.getNearZ(), setup.getHeight());
		copy.setField(CCameraField.LOCAL_PITCH, setup.getLocalPitch());
		copy.setField(CCameraField.LOCAL_YAW, setup.getLocalYaw());
		copy.setField(CCameraField.LOCAL_ROLL, setup.getLocalRoll());
		copy.setDestPositionX(setup.getDestPositionX());
		copy.setDestPositionY(setup.getDestPositionY());
		copy.setLabel(setup.getLabel());
		copy.setCameraType(setup.getCameraType());
		return copy;
	}

	private void registerCamera() {
		jassProgramVisitor.getJassNativeManager().createNative("GetCameraMargin",
				(arguments, globalScope, triggerScope) -> {
					final int whichMargin = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final Rectangle playableMapArea = war3MapViewer.terrain.getPlayableMapArea();
					switch (whichMargin) {
					case 0:// CAMERA_MARGIN_LEFT
						return RealJassValue
								.of(war3MapViewer.terrain.getDefaultCameraBounds()[0] - playableMapArea.x);
					case 1:// CAMERA_MARGIN_RIGHT
						return RealJassValue.of((playableMapArea.x + playableMapArea.width)
								- war3MapViewer.terrain.getDefaultCameraBounds()[2]);
					case 2:// CAMERA_MARGIN_TOP
						return RealJassValue.of((playableMapArea.y + playableMapArea.height)
								- war3MapViewer.terrain.getDefaultCameraBounds()[3]);
					case 3:// CAMERA_MARGIN_BOTTOM
						return RealJassValue
								.of(war3MapViewer.terrain.getDefaultCameraBounds()[1] - playableMapArea.y);
					default:
						return RealJassValue.ZERO;
					}
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetCameraBoundMinX",
				(arguments, globalScope, triggerScope) -> {
					return RealJassValue.of(this.link.view().boundsX());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetCameraBoundMinY",
				(arguments, globalScope, triggerScope) -> {
					return RealJassValue.of(this.link.view().boundsY());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetCameraBoundMaxX",
				(arguments, globalScope, triggerScope) -> {
					final LocalUiLink.View view = this.link.view();
					return RealJassValue.of(view.boundsX() + view.boundsWidth());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetCameraBoundMaxY",
				(arguments, globalScope, triggerScope) -> {
					final LocalUiLink.View view = this.link.view();
					return RealJassValue.of(view.boundsY() + view.boundsHeight());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCameraBounds",
				(arguments, globalScope, triggerScope) -> {
					final double x1 = arguments.get(0).visit(RealJassValueVisitor.getInstance());
					final double y1 = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					final double x2 = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final double y2 = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final double x3 = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					final double y3 = arguments.get(5).visit(RealJassValueVisitor.getInstance());
					final double x4 = arguments.get(6).visit(RealJassValueVisitor.getInstance());
					final double y4 = arguments.get(7).visit(RealJassValueVisitor.getInstance());
					final float left = (float) Math.min(Math.min(x1, x2), Math.min(x3, x4));
					final float bottom = (float) Math.min(Math.min(y1, y2), Math.min(y3, y4));
					final float right = (float) Math.max(Math.max(x1, x2), Math.max(x3, x4));
					final float top = (float) Math.max(Math.max(y1, y2), Math.max(y3, y4));
					final Rectangle bounds = new Rectangle(left, bottom, right - left, top - bottom);
					this.link.sendCamera(() -> meleeUI.getCameraManager().setCameraBounds(bounds));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCameraTargetController",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float xoffset = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float yoffset = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final boolean inheritOrientation = arguments.get(3)
							.visit(BooleanJassValueVisitor.getInstance());
					// the unit's peer belongs to the render side: it is looked up there
					this.link.sendCamera(() -> meleeUI.getCameraManager().setTargetController(
							war3MapViewer.getRenderPeer(whichUnit), xoffset, yoffset, inheritOrientation),
							FOLLOW_SECONDS);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCameraPosition",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					this.link.sendCamera(() -> meleeUI.getCameraManager().jumpTo(x, y));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PanCameraTo",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					this.link.sendCamera(() -> meleeUI.getCameraManager().panTo(x, y), UNTIMED_MOVE_SECONDS);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCameraQuickPosition",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					this.link.sendCamera(() -> meleeUI.getCameraManager().setQuickPosition(x, y));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PanCameraToTimed",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float duration = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					this.link.sendCamera(() -> meleeUI.getCameraManager().panToTimed(x, y, duration), duration);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PanCameraToWithZ",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float zOffsetDest = arguments.get(2).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					this.link.sendCamera(() -> {
						meleeUI.getCameraManager().panTo(x, y);
						meleeUI.getCameraManager().setTargetZOffset(zOffsetDest);
					}, UNTIMED_MOVE_SECONDS);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PanCameraToTimedWithZ",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float zOffsetDest = arguments.get(2).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					final float duration = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					this.link.sendCamera(() -> {
						meleeUI.getCameraManager().panToTimed(x, y, duration);
						meleeUI.getCameraManager().setTargetZOffset(zOffsetDest, duration);
					}, duration);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetCameraField",
				(arguments, globalScope, triggerScope) -> {
					final CCameraField field = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (field == null) {
						return RealJassValue.of(0.0f);
					}
					// The player's own camera: see the camera position natives.
					return RealJassValue
							.of(war3MapViewer.replayedLocalValue(this.link.view().cameraField(field)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCameraField",
				(arguments, globalScope, triggerScope) -> {
					final CCameraField field = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float value = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float duration = arguments.get(2).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					if (field != null) {
						this.link.sendCamera(() -> meleeUI.getCameraManager().setField(field, value, duration), duration);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("AdjustCameraField",
				(arguments, globalScope, triggerScope) -> {
					final CCameraField field = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float offset = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float duration = arguments.get(2).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					if (field != null) {
						this.link.sendCamera(() -> meleeUI.getCameraManager().adjustField(field, offset, duration), duration);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("StopCamera",
				(arguments, globalScope, triggerScope) -> {
					this.link.sendCamera(() -> meleeUI.getCameraManager().stopPan());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ResetToGameCamera",
				(arguments, globalScope, triggerScope) -> {
					final float forceDuration = arguments.get(0).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					this.link.sendCamera(() -> meleeUI.getCameraManager().resetToGameCamera(forceDuration), forceDuration);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCameraFieldControlledByInput",
				(arguments, globalScope, triggerScope) -> {
					final CCameraField field = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean controlled = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (field != null) {
						this.link.sendCamera(
								() -> meleeUI.getCameraManager().setFieldControlledByInput(field, controlled));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetCameraFieldControlledByInput",
				(arguments, globalScope, triggerScope) -> {
					final CCameraField field = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue
							.of((field == null) || this.link.view().fieldControlledByInput(field));
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzCameraSetCameraType",
				(arguments, globalScope, triggerScope) -> {
					final int cameraType = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					this.link.sendCamera(() -> meleeUI.getCameraManager().setCameraType(cameraType));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzCameraGetCameraType",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(this.link.view().cameraType()));
		jassProgramVisitor.getJassNativeManager().createNative("BlzSetCameraAllowsHotkeyTargetLock",
				(arguments, globalScope, triggerScope) -> {
					this.cameraAllowsHotkeyTargetLock = arguments.get(0)
							.visit(BooleanJassValueVisitor.getInstance());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzGetCameraAllowsHotkeyTargetLock",
				(arguments, globalScope, triggerScope) -> BooleanJassValue
						.of(this.cameraAllowsHotkeyTargetLock));
	}

	private void registerCameraSetups() {
		final HandleJassType locationType = type("location");
		final HandleJassType camerasetupType = type("camerasetup");

		jassProgramVisitor.getJassNativeManager().createNative("CreateCameraSetup",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(camerasetupType, new CustomCameraSetup(304, 70, 90, 0, 1650, 5000, 100, 0));
				});
		jassProgramVisitor.getJassNativeManager().createNative("CameraSetupGetField",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final CCameraField cameraField = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					if ((cameraSetup != null) && (cameraField != null)) {
						return RealJassValue.of(cameraSetup.getField(cameraField));
					}
					return RealJassValue.of(0.0f);
				});
		jassProgramVisitor.getJassNativeManager().createNative("CameraSetupSetField",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final CCameraField cameraField = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					float fieldValue = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					// Measured on 1.29: a setup keeps a negative value as 0 (a roll of -9 reads back as 0, not 351),
					// and values over 360 as set. The local angles are newer than that and are kept as they are.
					if ((fieldValue < 0) && (cameraField != CCameraField.LOCAL_PITCH)
							&& (cameraField != CCameraField.LOCAL_YAW) && (cameraField != CCameraField.LOCAL_ROLL)) {
						fieldValue = 0;
					}
					if ((cameraSetup != null) && (cameraField != null)) {
						cameraSetup.setField(cameraField, fieldValue);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzCameraSetupSetLabel",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final String label = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					if (cameraSetup != null) {
						cameraSetup.setLabel(label == null ? "" : label);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzCameraSetupGetLabel",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					return new StringJassValue(cameraSetup == null ? "" : cameraSetup.getLabel());
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzCameraSetupSetCameraType",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final int cameraType = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (cameraSetup != null) {
						cameraSetup.setCameraType(cameraType);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzCameraSetupGetCameraType",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(cameraSetup == null ? 0 : cameraSetup.getCameraType());
				});
		jassProgramVisitor.getJassNativeManager().createNative("CameraSetupSetDestPosition",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (cameraSetup != null) {
						cameraSetup.setDestPositionX(x);
						cameraSetup.setDestPositionY(y);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("CameraSetupGetDestPositionLoc",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					if (cameraSetup != null) {
						return new HandleJassValue(locationType, new LocationJass(cameraSetup.getDestPositionX(),
								cameraSetup.getDestPositionY(), this.simulation.getHandleIdAllocator().createHandleId()));
					}
					return locationType.getNullValue();
				});
		jassProgramVisitor.getJassNativeManager().createNative("CameraSetupGetDestPositionX",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					if (cameraSetup != null) {
						return RealJassValue.of(cameraSetup.getDestPositionX());
					}
					return RealJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("CameraSetupGetDestPositionY",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					if (cameraSetup != null) {
						return RealJassValue.of(cameraSetup.getDestPositionY());
					}
					return RealJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("CameraSetupApply",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final boolean doPan = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final boolean panTimed = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final CustomCameraSetup applied = appliedSetup(cameraSetup);
					this.link.sendCamera(() -> meleeUI.getCameraManager().applyCameraSetup(applied, doPan, panTimed),
							UNTIMED_MOVE_SECONDS);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("CameraSetupApplyWithZ",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final float zOffset = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final CustomCameraSetup applied = appliedSetup(cameraSetup);
					this.link.sendCamera(() -> {
						meleeUI.getCameraManager().applyCameraSetup(applied, true, true);
						meleeUI.getCameraManager().setTargetZOffset(zOffset);
					}, UNTIMED_MOVE_SECONDS);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("CameraSetupApplyForceDuration",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final boolean doPan = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final float forceDuration = arguments.get(2).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					final CustomCameraSetup applied = appliedSetup(cameraSetup);
					this.link.sendCamera(() -> meleeUI.getCameraManager().applyCameraSetupForceDuration(applied, doPan,
							forceDuration), forceDuration);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("CameraSetupApplyForceDurationWithZ",
				(arguments, globalScope, triggerScope) -> {
					final CustomCameraSetup cameraSetup = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final float zOffset = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float forceDuration = arguments.get(2).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					final CustomCameraSetup applied = appliedSetup(cameraSetup);
					this.link.sendCamera(() -> {
						meleeUI.getCameraManager().applyCameraSetupForceDuration(applied, true, forceDuration);
						meleeUI.getCameraManager().setTargetZOffset(zOffset, forceDuration);
					}, forceDuration);
					return null;
				});
	}

	private void registerCineFilter() {
		jassProgramVisitor.getJassNativeManager().createNative("SetCineFilterTexture",
				(arguments, globalScope, triggerScope) -> {
					final String texture = nullable(arguments, 0, StringJassValueVisitor.getInstance());
					this.link.sendCamera(() -> meleeUI.getCineFilter().setTexture(texture));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCineFilterBlendMode",
				(arguments, globalScope, triggerScope) -> {
					final CBlendMode mode = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					this.link.sendCamera(() -> meleeUI.getCineFilter().setBlendMode(mode));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCineFilterTexMapFlags",
				(arguments, globalScope, triggerScope) -> {
					final Object flags = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int texMapFlags = flags instanceof CTexMapFlags ? ((CTexMapFlags) flags).ordinal() : 0;
					this.link.sendCamera(() -> meleeUI.getCineFilter().setTexMapFlags(texMapFlags));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCineFilterStartUV",
				(arguments, globalScope, triggerScope) -> {
					final float u1 = realArg(arguments, 0), v1 = realArg(arguments, 1), u2 = realArg(arguments, 2),
							v2 = realArg(arguments, 3);
					this.link.sendCamera(() -> meleeUI.getCineFilter().setStartUV(u1, v1, u2, v2));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCineFilterEndUV",
				(arguments, globalScope, triggerScope) -> {
					final float u1 = realArg(arguments, 0), v1 = realArg(arguments, 1), u2 = realArg(arguments, 2),
							v2 = realArg(arguments, 3);
					this.link.sendCamera(() -> meleeUI.getCineFilter().setEndUV(u1, v1, u2, v2));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCineFilterStartColor",
				(arguments, globalScope, triggerScope) -> {
					final int r = intArg(arguments, 0), g = intArg(arguments, 1), b = intArg(arguments, 2),
							a = intArg(arguments, 3);
					this.link.sendCamera(() -> meleeUI.getCineFilter().setStartColor(r, g, b, a));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCineFilterEndColor",
				(arguments, globalScope, triggerScope) -> {
					final int r = intArg(arguments, 0), g = intArg(arguments, 1), b = intArg(arguments, 2),
							a = intArg(arguments, 3);
					this.link.sendCamera(() -> meleeUI.getCineFilter().setEndColor(r, g, b, a));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCineFilterDuration",
				(arguments, globalScope, triggerScope) -> {
					final float duration = realArg(arguments, 0);
					this.link.sendCamera(() -> meleeUI.getCineFilter().setDuration(duration));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("DisplayCineFilter",
				(arguments, globalScope, triggerScope) -> {
					final boolean display = arguments.get(0).visit(BooleanJassValueVisitor.getInstance());
					this.link.sendCamera(() -> meleeUI.getCineFilter().display(display));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsCineFilterDisplayed",
				(arguments, globalScope, triggerScope) -> BooleanJassValue
						.of(this.link.view().cineFilterDisplayed()));
	}
}
