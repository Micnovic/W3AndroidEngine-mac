package com.etheller.warsmash.viewer5.handlers.mdx;

import java.nio.CharBuffer;
import java.nio.FloatBuffer;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.etheller.warsmash.util.RenderMathUtils;
import com.etheller.warsmash.viewer5.Bounds;
import com.etheller.warsmash.viewer5.GenericNode;
import com.hiveworkshop.rms.parsers.mdlx.MdlxCollisionShape;

public class CollisionShape extends GenericObject {
	private static final Vector3 intersectHeap = new Vector3();
	private static final Vector3 intersectHeap2 = new Vector3();
	private static final Matrix4 intersectMatrixHeap = new Matrix4();
	private static final Ray intersectRayHeap = new Ray();
	/** How far a ray may drift sideways along its length for a grid cell to stand for all of it, in model units. */
	private static final float MAX_VERTICAL_DRIFT = PickGrid.MARGIN / 4;
	private Intersectable intersectable;

	public CollisionShape(final MdxModel model, final MdlxCollisionShape object, final int index) {
		super(model, object, index);
		final float[][] vertices = object.getVertices();

		switch (object.getType()) {
		case BOX:
			this.intersectable = new IntersectableBox(vertices[0], vertices[1]);
			break;
		case CYLINDER:
			this.intersectable = null; // TODO
			break;
		case PLANE:
			this.intersectable = null; // TODO
			break;
		case SPHERE:
			this.intersectable = new IntersectableSphere(vertices[0], object.getBoundsRadius());
			break;
		}
	}

	public boolean checkIntersect(final Ray ray, final MdxNode mdxNode, final Vector3 intersection) {
		if (this.intersectable != null) {
			return this.intersectable.checkIntersect(ray, mdxNode, intersection);
		}
		return false;
	}

	private static interface Intersectable {
		boolean checkIntersect(final Ray ray, final MdxNode mdxNode, final Vector3 intersection);
	}

	private static final class IntersectableBox implements Intersectable {
		private final BoundingBox boundingBox;

		public IntersectableBox(final float[] vertex1, final float[] vertex2) {
			this.boundingBox = new BoundingBox(new Vector3(vertex1), new Vector3(vertex2));
		}

		@Override
		public boolean checkIntersect(final Ray ray, final MdxNode mdxNode, final Vector3 intersection) {
			intersectMatrixHeap.set(mdxNode.worldMatrix);
			Matrix4.inv(intersectMatrixHeap.val);
			intersectHeap.set(ray.origin);
			intersectHeap2.set(ray.direction);
			intersectHeap2.add(ray.origin);
			intersectHeap.prj(intersectMatrixHeap);
			intersectHeap2.prj(intersectMatrixHeap);
			intersectHeap2.sub(intersectHeap);
			intersectRayHeap.set(intersectHeap, intersectHeap2);
			if (Intersector.intersectRayBounds(intersectRayHeap, this.boundingBox, intersection)) {
				intersection.prj(mdxNode.worldMatrix);
				return true;
			}
			return false;
		}
	}

	private static final class IntersectableSphere implements Intersectable {
		private final Vector3 center;
		private final float radius;

		public IntersectableSphere(final float[] center, final float radius) {
			this.center = new Vector3(center);
			this.radius = radius;
		}

		@Override
		public boolean checkIntersect(final Ray ray, final MdxNode mdxNode, final Vector3 intersection) {
			intersectHeap.set(this.center);
			intersectHeap.prj(mdxNode.worldMatrix);
			if (Intersector.intersectRaySphere(ray, intersectHeap, this.radius, intersection)) {
				return true;
			}
			return false;
		}
	}

	public static boolean intersectRayTriangles(final Ray ray, final GenericNode mdxNode, final float[] vertices,
			final char[] indices, final int vertexSize, final Vector3 intersection) {
		intersectMatrixHeap.set(mdxNode.worldMatrix);
		Matrix4.inv(intersectMatrixHeap.val);
		intersectHeap.set(ray.origin);
		intersectHeap2.set(ray.direction);
		intersectHeap2.add(ray.origin);
		intersectHeap.prj(intersectMatrixHeap);
		intersectHeap2.prj(intersectMatrixHeap);
		intersectHeap2.sub(intersectHeap);
		intersectRayHeap.set(intersectHeap, intersectHeap2);
		if (RenderMathUtils.intersectRayTriangles(intersectRayHeap, vertices, indices, vertexSize, intersection)) {
			intersection.prj(mdxNode.worldMatrix);
			return true;
		}
		return false;
	}

	/** The same for triangles kept in native buffers. */
	public static boolean intersectRayTriangles(final Ray ray, final GenericNode mdxNode, final FloatBuffer vertices,
			final CharBuffer indices, final int vertexSize, final Vector3 intersection) {
		intersectMatrixHeap.set(mdxNode.worldMatrix);
		Matrix4.inv(intersectMatrixHeap.val);
		intersectHeap.set(ray.origin);
		intersectHeap2.set(ray.direction);
		intersectHeap2.add(ray.origin);
		intersectHeap.prj(intersectMatrixHeap);
		intersectHeap2.prj(intersectMatrixHeap);
		intersectHeap2.sub(intersectHeap);
		intersectRayHeap.set(intersectHeap, intersectHeap2);
		if (RenderMathUtils.intersectRayTriangles(intersectRayHeap, vertices, indices, vertexSize, intersection)) {
			intersection.prj(mdxNode.worldMatrix);
			return true;
		}
		return false;
	}

	/**
	 * The ray against a geoset's triangles. A ray that is vertical in the model's own space (the height of the
	 * ground under a unit) only tests the triangles of the grid cell it passes through.
	 */
	public static boolean intersectRayTriangles(final Ray ray, final GenericNode mdxNode, final Geoset geoset,
			final Vector3 intersection) {
		intersectMatrixHeap.set(mdxNode.worldMatrix);
		Matrix4.inv(intersectMatrixHeap.val);
		intersectHeap.set(ray.origin);
		intersectHeap2.set(ray.direction);
		intersectHeap2.add(ray.origin);
		intersectHeap.prj(intersectMatrixHeap);
		intersectHeap2.prj(intersectMatrixHeap);
		intersectHeap2.sub(intersectHeap);
		intersectRayHeap.set(intersectHeap, intersectHeap2);
		final boolean hit;
		if ((Math.max(Math.abs(intersectHeap2.x), Math.abs(intersectHeap2.y)) <= MAX_VERTICAL_DRIFT)
				&& (geoset.pickGrid() != null)) {
			final PickGrid grid = geoset.pickGrid();
			final int cell = grid.cellAt(intersectHeap.x, intersectHeap.y);
			hit = (cell >= 0) && RenderMathUtils.intersectRayTriangles(intersectRayHeap, geoset.pickVertices,
					geoset.pickFaces, 3, grid.faces(), grid.start(cell), grid.end(cell), intersection);
		}
		else {
			hit = RenderMathUtils.intersectRayTriangles(intersectRayHeap, geoset.pickVertices, geoset.pickFaces, 3,
					intersection);
		}
		if (hit) {
			intersection.prj(mdxNode.worldMatrix);
			return true;
		}
		return false;
	}

	public static boolean intersectRayBounds(final Bounds bounds, final Matrix4 worldMatrix, final Ray ray,
			final Vector3 intersection) {
		intersectMatrixHeap.set(worldMatrix);
		Matrix4.inv(intersectMatrixHeap.val);
		intersectHeap.set(ray.origin);
		intersectHeap2.set(ray.direction);
		intersectHeap2.add(ray.origin);
		intersectHeap.prj(intersectMatrixHeap);
		intersectHeap2.prj(intersectMatrixHeap);
		intersectHeap2.sub(intersectHeap);
		intersectRayHeap.set(intersectHeap, intersectHeap2);
		if (bounds.intersectRay(intersectRayHeap, intersection)) {
			intersection.prj(worldMatrix);
			return true;
		}
		return false;
	}
}
