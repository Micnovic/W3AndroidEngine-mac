package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.Arrays;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.parsers.w3x.w3e.Corner;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.blight.CAbilityBlight;

/**
 * Blight on the terrain: the ground it covers, its sources and the corners it reaches. The game owns it: the corners'
 * blight, the pathing grid and the trees change here, and only the corners that changed go on to the drawn terrain.
 */
public final class CBlight {
	private CBlight() {
	}

	public static void set(final CSimulation simulation, final float whichLocationX, final float whichLocationY,
			final float radius, final boolean square, final boolean blighted) {
		// Measured on the original game: blight is set on the terrain corners (every 128) around the corner nearest
		// to the point (halves rounded away from zero), with the radius rounded to whole corners and a corner counted
		// when it is within that many corners plus a little; a 128 tile is blighted, for building and blight checks
		// too, when any of its four corners is. A change reaches all four tiles round each changed corner, so clearing
		// a corner clears them even where a neighbouring corner stays blighted.
		final CTerrainData terrainData = simulation.getTerrainData();
		final int cornerRadius = getBlightCornerRadius(radius);
		final float cornerReach = cornerRadius + 0.35f;
		final float centerX = roundHalfAwayFromZero(whichLocationX / 128f) * 128f;
		final float centerY = roundHalfAwayFromZero(whichLocationY / 128f) * 128f;
		int[] changedCorners = new int[32];
		float[] changedPositions = new float[32];
		int changedCount = 0;
		int minI = Integer.MAX_VALUE;
		int maxI = Integer.MIN_VALUE;
		int minJ = Integer.MAX_VALUE;
		int maxJ = Integer.MIN_VALUE;
		float[] sources = null;
		for (int i = -cornerRadius; i <= cornerRadius; i++) {
			for (int j = -cornerRadius; j <= cornerRadius; j++) {
				if (!square && (((i * i) + (j * j)) > (cornerReach * cornerReach))) {
					continue;
				}
				final float x = centerX + (i * 128f);
				final float y = centerY + (j * 128f);
				final Corner corner = terrainData.getCorner(x, y);
				if ((corner == null) || ((corner.getBlight() != 0) == blighted)) {
					continue;
				}
				if (!blighted) {
					if (sources == null) {
						sources = findStandingBlightSources(simulation, centerX, centerY, cornerRadius);
					}
					if (isInStandingBlightSource(sources, x, y)) {
						continue;
					}
				}
				corner.setBlight(blighted);
				if (changedCorners.length < ((changedCount + 1) * 2)) {
					changedCorners = Arrays.copyOf(changedCorners, changedCorners.length * 2);
					changedPositions = Arrays.copyOf(changedPositions, changedPositions.length * 2);
				}
				changedCorners[changedCount * 2] = i;
				changedCorners[(changedCount * 2) + 1] = j;
				changedPositions[changedCount * 2] = x;
				changedPositions[(changedCount * 2) + 1] = y;
				changedCount++;
				minI = Math.min(minI, i);
				maxI = Math.max(maxI, i);
				minJ = Math.min(minJ, j);
				maxJ = Math.max(maxJ, j);
			}
		}
		if (changedCount == 0) {
			return;
		}
		final PathingGrid pathingGrid = simulation.getPathingGrid();
		for (int k = 0; k < changedCount; k++) {
			final float cornerX = centerX + (changedCorners[k * 2] * 128f);
			final float cornerY = centerY + (changedCorners[(k * 2) + 1] * 128f);
			for (int a = -1; a <= 0; a++) {
				for (int b = -1; b <= 0; b++) {
					final float tileX = cornerX + (a * 128f);
					final float tileY = cornerY + (b * 128f);
					for (float x = tileX + 16f; x < (tileX + 128f); x += 32f) {
						for (float y = tileY + 16f; y < (tileY + 128f); y += 32f) {
							if (pathingGrid.contains(x, y)) {
								pathingGrid.setBlighted(x, y, blighted);
							}
						}
					}
				}
			}
		}
		simulation.getSimulationRenderController().blightChanged(Arrays.copyOf(changedPositions, changedCount * 2),
				blighted);
		if (blighted) {
			final float minX = centerX + ((minI - 1) * 128f);
			final float minY = centerY + ((minJ - 1) * 128f);
			final Rectangle blightRectangle = new Rectangle(minX, minY, ((maxI - minI) + 2) * 128f,
					((maxJ - minJ) + 2) * 128f);
			simulation.getWorldCollision().enumDestructablesInRect(blightRectangle, new TreeBlighting(simulation));
		}
	}

	public static int getBlightCornerRadius(final float radius) {
		return (int) ((radius / 128f) + 0.5f);
	}

	/**
	 * Measured: the blight of a standing blight source (Necropolis) cannot be removed, it stays as it was. Returns x,
	 * y and the squared corner reach of each source near the change.
	 */
	private static float[] findStandingBlightSources(final CSimulation simulation, final float centerX,
			final float centerY, final int cornerRadius) {
		float[] sources = new float[48];
		int sourceCount = 0;
		final float reach = (cornerRadius + 2) * 128f;
		for (final CUnit unit : simulation.getUnitsIncludingNew()) {
			final CAbilityBlight blight = unit.getFirstAbilityOfType(CAbilityBlight.class);
			if ((blight == null) || !blight.isCreatesBlight() || blight.isDisabled() || unit.isDead()) {
				continue;
			}
			final int sourceRadius = getBlightCornerRadius(blight.getCurrentRadius());
			final float sourceX = roundHalfAwayFromZero(unit.getX() / 128f) * 128f;
			final float sourceY = roundHalfAwayFromZero(unit.getY() / 128f) * 128f;
			final float sourceReach = reach + (sourceRadius * 128f);
			final float dx = sourceX - centerX;
			final float dy = sourceY - centerY;
			if (((dx * dx) + (dy * dy)) > (sourceReach * sourceReach)) {
				continue;
			}
			if (sources.length < ((sourceCount + 1) * 3)) {
				sources = Arrays.copyOf(sources, sources.length * 2);
			}
			final float sourceCornerReach = sourceRadius + 0.35f;
			sources[sourceCount * 3] = sourceX;
			sources[(sourceCount * 3) + 1] = sourceY;
			sources[(sourceCount * 3) + 2] = sourceCornerReach * sourceCornerReach;
			sourceCount++;
		}
		return Arrays.copyOf(sources, sourceCount * 3);
	}

	private static boolean isInStandingBlightSource(final float[] sources, final float x, final float y) {
		for (int k = 0; k < (sources.length / 3); k++) {
			final int i = Math.round((x - sources[k * 3]) / 128f);
			final int j = Math.round((y - sources[(k * 3) + 1]) / 128f);
			if (((i * i) + (j * j)) <= sources[(k * 3) + 2]) {
				return true;
			}
		}
		return false;
	}

	private static float roundHalfAwayFromZero(final float value) {
		return (int) (value + (value < 0 ? -0.5f : 0.5f));
	}

	/** Trees on blighted ground turn to their blighted look; there is no way to unblight one. */
	private static final class TreeBlighting implements CDestructableEnumFunction {
		private final CSimulation simulation;

		private TreeBlighting(final CSimulation simulation) {
			this.simulation = simulation;
		}

		@Override
		public boolean call(final CDestructable destructable) {
			if (destructable.checkIsOnBlight(this.simulation)) {
				destructable.setBlighted(true);
			}
			return false;
		}
	}
}
