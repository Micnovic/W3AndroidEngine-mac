package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.RemovablePathingMapInstance;

final class CUnitPlacementState {
	float flyHeight;
	float turnRate;
	float propWindow;
	Rectangle collisionRectangle;
	/** The collision trees the unit is in (CWorldCollision); 0 in a save from before it was kept. */
	int collisionTrees;
	RemovablePathingMapInstance pathingInstance;
}
