package com.etheller.warsmash.util;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.List;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

public enum RenderMathUtils {
	;
	public static final float[] EMPTY_FLOAT_ARRAY = new float[0];
	public static final Vector3 VEC3_UNIT_X = new Vector3(1, 0, 0);
	public static final Vector3 VEC3_UNIT_Y = new Vector3(0, 1, 0);
	public static final Vector3 VEC3_UNIT_Z = new Vector3(0, 0, 1);
	public static final float[] FLOAT_VEC3_ZERO = new float[] { 0, 0, 0 };
	public static final float[] FLOAT_QUAT_DEFAULT = new float[] { 0, 0, 0, 1 };
	public static final float[] FLOAT_VEC3_ONE = new float[] { 1, 1, 1 };

	// copied from ghostwolf and
	// https://www.blend4web.com/api_doc/libs_gl-matrix2.js.html
	public static void fromRotationTranslationScaleOrigin(final Quaternion q, final Vector3 v, final Vector3 s,
			final Matrix4 out, final Vector3 pivot) {
		final float x = q.x;
		final float y = q.y;
		final float z = q.z;
		final float w = q.w;
		final float x2 = x + x;
		final float y2 = y + y;
		final float z2 = z + z;
		final float xx = x * x2;
		final float xy = x * y2;
		final float xz = x * z2;
		final float yy = y * y2;
		final float yz = y * z2;
		final float zz = z * z2;
		final float wx = w * x2;
		final float wy = w * y2;
		final float wz = w * z2;
		final float sx = s.x;
		final float sy = s.y;
		final float sz = s.z;
		out.val[Matrix4.M00] = (1 - (yy + zz)) * sx;
		out.val[Matrix4.M10] = (xy + wz) * sx;
		out.val[Matrix4.M20] = (xz - wy) * sx;
		out.val[Matrix4.M30] = 0;
		out.val[Matrix4.M01] = (xy - wz) * sy;
		out.val[Matrix4.M11] = (1 - (xx + zz)) * sy;
		out.val[Matrix4.M21] = (yz + wx) * sy;
		out.val[Matrix4.M31] = 0;
		out.val[Matrix4.M02] = (xz + wy) * sz;
		out.val[Matrix4.M12] = (yz - wx) * sz;
		out.val[Matrix4.M22] = (1 - (xx + yy)) * sz;
		out.val[Matrix4.M32] = 0;
		out.val[Matrix4.M03] = (v.x + pivot.x) - ((out.val[Matrix4.M00] * pivot.x) + (out.val[Matrix4.M01] * pivot.y)
				+ (out.val[Matrix4.M02] * pivot.z));
		out.val[Matrix4.M13] = (v.y + pivot.y) - ((out.val[Matrix4.M10] * pivot.x) + (out.val[Matrix4.M11] * pivot.y)
				+ (out.val[Matrix4.M12] * pivot.z));
		out.val[Matrix4.M23] = (v.z + pivot.z) - ((out.val[Matrix4.M20] * pivot.x) + (out.val[Matrix4.M21] * pivot.y)
				+ (out.val[Matrix4.M22] * pivot.z));
		out.val[Matrix4.M33] = 1;
	}

	// copied from
	// https://www.blend4web.com/api_doc/libs_gl-matrix2.js.html
	public static void fromRotationTranslationScale(final Quaternion q, final Vector3 v, final Vector3 s,
			final Matrix4 out) {
		final float x = q.x;
		final float y = q.y;
		final float z = q.z;
		final float w = q.w;
		final float x2 = x + x;
		final float y2 = y + y;
		final float z2 = z + z;
		final float xx = x * x2;
		final float xy = x * y2;
		final float xz = x * z2;
		final float yy = y * y2;
		final float yz = y * z2;
		final float zz = z * z2;
		final float wx = w * x2;
		final float wy = w * y2;
		final float wz = w * z2;
		final float sx = s.x;
		final float sy = s.y;
		final float sz = s.z;
		out.val[Matrix4.M00] = (1 - (yy + zz)) * sx;
		out.val[Matrix4.M10] = (xy + wz) * sx;
		out.val[Matrix4.M20] = (xz - wy) * sx;
		out.val[Matrix4.M30] = 0;
		out.val[Matrix4.M01] = (xy - wz) * sy;
		out.val[Matrix4.M11] = (1 - (xx + zz)) * sy;
		out.val[Matrix4.M21] = (yz + wx) * sy;
		out.val[Matrix4.M31] = 0;
		out.val[Matrix4.M02] = (xz + wy) * sz;
		out.val[Matrix4.M12] = (yz - wx) * sz;
		out.val[Matrix4.M22] = (1 - (xx + yy)) * sz;
		out.val[Matrix4.M32] = 0;
		out.val[Matrix4.M03] = v.x;
		out.val[Matrix4.M13] = v.y;
		out.val[Matrix4.M23] = v.z;
		out.val[Matrix4.M33] = 1;
	}

	public static void mul(final Matrix4 dest, final Matrix4 left, final Matrix4 right) {
		dest.set(left); // TODO better performance here, remove the extra
						// copying
		dest.mul(right);
	}

	public static void mul(final Quaternion dest, final Quaternion left, final Quaternion right) {
		dest.set(left); // TODO better performance here, remove the extra
						// copying
		dest.mul(right);
	}

	public static Matrix4 perspective(final Matrix4 out, final float fovy, final float aspect, final float near,
			final float far) {
		final float f = 1.0f / (float) Math.tan(fovy / 2), nf;
		out.val[Matrix4.M00] = f / aspect;
		out.val[Matrix4.M10] = 0;
		out.val[Matrix4.M20] = 0;
		out.val[Matrix4.M30] = 0;
		out.val[Matrix4.M01] = 0;
		out.val[Matrix4.M11] = f;
		out.val[Matrix4.M21] = 0;
		out.val[Matrix4.M31] = 0;
		out.val[Matrix4.M02] = 0;
		out.val[Matrix4.M12] = 0;
		out.val[Matrix4.M32] = -1;
		out.val[Matrix4.M03] = 0;
		out.val[Matrix4.M13] = 0;
		out.val[Matrix4.M33] = 0;
		if (!Double.isNaN(far) && !Double.isInfinite(far)) {
			nf = 1 / (near - far);
			out.val[Matrix4.M22] = (far + near) * nf;
			out.val[Matrix4.M23] = (2 * far * near) * nf;
		}
		else {
			out.val[Matrix4.M22] = -1;
			out.val[Matrix4.M23] = -2 * near;
		}
		return out;
	}

	public static Matrix4 ortho(final Matrix4 out, final float left, final float right, final float bottom,
			final float top, final float near, final float far) {
		final float lr = 1 / (left - right);
		final float bt = 1 / (bottom - top);
		final float nf = 1 / (near - far);
		out.val[Matrix4.M00] = -2 * lr;
		out.val[Matrix4.M10] = 0;
		out.val[Matrix4.M20] = 0;
		out.val[Matrix4.M30] = 0;

		out.val[Matrix4.M01] = 0;
		out.val[Matrix4.M11] = -2 * bt;
		out.val[Matrix4.M21] = 0;
		out.val[Matrix4.M31] = 0;

		out.val[Matrix4.M02] = 0;
		out.val[Matrix4.M12] = 0;
		out.val[Matrix4.M22] = 2 * nf;
		out.val[Matrix4.M32] = 0;

		out.val[Matrix4.M03] = (left + right) * lr;
		out.val[Matrix4.M13] = (top + bottom) * bt;
		out.val[Matrix4.M23] = (far + near) * nf;
		out.val[Matrix4.M33] = 1;
		return out;
	}

	public static void unpackPlanes(final Vector4[] planes, final Matrix4 m) {
		final float a00 = m.val[Matrix4.M00], a01 = m.val[Matrix4.M01], a02 = m.val[Matrix4.M02],
				a03 = m.val[Matrix4.M03], a10 = m.val[Matrix4.M10], a11 = m.val[Matrix4.M11], a12 = m.val[Matrix4.M12],
				a13 = m.val[Matrix4.M13], a20 = m.val[Matrix4.M20], a21 = m.val[Matrix4.M21], a22 = m.val[Matrix4.M22],
				a23 = m.val[Matrix4.M23], a30 = m.val[Matrix4.M30], a31 = m.val[Matrix4.M31], a32 = m.val[Matrix4.M32],
				a33 = m.val[Matrix4.M33];

		// Left clipping plane
		Vector4 plane = planes[0];
		plane.x = a30 + a00;
		plane.y = a31 + a01;
		plane.z = a32 + a02;
		plane.w = a33 + a03;

		// Right clipping plane
		plane = planes[1];
		plane.x = a30 - a00;
		plane.y = a31 - a01;
		plane.z = a32 - a02;
		plane.w = a33 - a03;

		// Top clipping plane
		plane = planes[2];
		plane.x = a30 - a10;
		plane.y = a31 - a11;
		plane.z = a32 - a12;
		plane.w = a33 - a13;

		// Bottom clipping plane
		plane = planes[3];
		plane.x = a30 + a10;
		plane.y = a31 + a11;
		plane.z = a32 + a12;
		plane.w = a33 + a13;

		// Near clipping plane
		plane = planes[4];
		plane.x = a30 + a20;
		plane.y = a31 + a21;
		plane.z = a32 + a22;
		plane.w = a33 + a23;

		// Far clipping plane
		plane = planes[5];
		plane.x = a30 - a20;
		plane.y = a31 - a21;
		plane.z = a32 - a22;
		plane.w = a33 - a23;

		normalizePlane(planes[0], planes[0]);
		normalizePlane(planes[1], planes[1]);
		normalizePlane(planes[2], planes[2]);
		normalizePlane(planes[3], planes[3]);
		normalizePlane(planes[4], planes[4]);
		normalizePlane(planes[5], planes[5]);
	}

	public static void normalizePlane(final Vector4 out, final Vector4 plane) {
		final float len = Vector3.len(plane.x, plane.y, plane.z);

		out.x = plane.x / len;
		out.y = plane.y / len;
		out.z = plane.z / len;
		out.w = plane.w / len;
	}

	public static float distanceToPlane(final Vector4 plane, final Vector3 point) {
		return (plane.x * point.x) + (plane.y * point.y) + (plane.z * point.z) + plane.w;
	}

	private static final Vector4 heap = new Vector4();

	public static Vector3 unproject(final Vector3 out, final Vector3 v, final Matrix4 inverseMatrix,
			final Rectangle viewport) {
		final float x = ((2 * (v.x - viewport.x)) / viewport.width) - 1;
		final float y = ((2 * (v.y - viewport.y)) / viewport.height) - 1;
		final float z = (2 * v.z) - 1;

		heap.set(x, y, z, 1);
		Vector4.transformMat4(heap, heap, inverseMatrix);
		out.set(heap.x / heap.w, heap.y / heap.w, heap.z / heap.w);

		return out;
	}

	public static int testCell(final List<Vector4> planes, final int left, final int right, final int bottom,
			final int top, int first) {
		if (first == -1) {
			first = 0;
		}

		for (int i = 0; i < 6; i++) {
			final int index = (first + i) % 6;
			final Vector4 plane = planes.get(index);

			if ((distance2Plane2(plane, left, bottom) < 0) && (distance2Plane2(plane, left, top) < 0)
					&& (distance2Plane2(plane, right, top) < 0) && (distance2Plane2(plane, right, bottom) < 0)) {
				return index;
			}
		}

		return -1;
	}

	public static int testCell(final Vector4[] planes, final float left, final float right, final float bottom,
			final float top, int first) {
		if (first == -1) {
			first = 0;
		}

		for (int i = 0; i < 6; i++) {
			final int index = (first + i) % 6;
			final Vector4 plane = planes[index];

			if ((distance2Plane2(plane, left, bottom) < 0) && (distance2Plane2(plane, left, top) < 0)
					&& (distance2Plane2(plane, right, top) < 0) && (distance2Plane2(plane, right, bottom) < 0)) {
				return index;
			}
		}

		return -1;
	}

	/**
	 * Like testCell, but the cell is a box from bottomZ to topZ instead of a flat square at height 0.
	 */
	public static int testCell(final Vector4[] planes, final float left, final float right, final float bottom,
			final float top, final float bottomZ, final float topZ, int first) {
		if (first == -1) {
			first = 0;
		}

		for (int i = 0; i < 6; i++) {
			final int index = (first + i) % 6;
			final Vector4 plane = planes[index];

			if ((distanceToPlane3(plane, left, bottom, bottomZ) < 0) && (distanceToPlane3(plane, left, top, bottomZ) < 0)
					&& (distanceToPlane3(plane, right, top, bottomZ) < 0)
					&& (distanceToPlane3(plane, right, bottom, bottomZ) < 0)
					&& (distanceToPlane3(plane, left, bottom, topZ) < 0) && (distanceToPlane3(plane, left, top, topZ) < 0)
					&& (distanceToPlane3(plane, right, top, topZ) < 0)
					&& (distanceToPlane3(plane, right, bottom, topZ) < 0)) {
				return index;
			}
		}

		return -1;
	}

	public static float distance2Plane2(final Vector4 plane, final float px, final float py) {
		return (plane.x * px) + (plane.y * py) + plane.w;
	}

	public static int testSphere(final Vector4[] planes, final float x, final float y, final float z, final float r,
			int first) {
		if (first == -1) {
			first = 0;
		}

		for (int i = 0; i < 6; i++) {
			final int index = (first + i) % 6;

			if (distanceToPlane3(planes[index], x, y, z) < -r) {
				return index;
			}
		}

		return -1;
	}

	public static float distanceToPlane3(final Vector4 plane, final float px, final float py, final float pz) {
		return (plane.x * px) + (plane.y * py) + (plane.z * pz) + plane.w;
	}

	public static float randomInRange(final float a, final float b) {
		return (float) (a + (Math.random() * (b - a)));
	}

	public static float clamp(final float x, final float minVal, final float maxVal) {
		return Math.min(Math.max(x, minVal), maxVal);
	}

	public static float lerp(final float a, final float b, final float t) {
		return a + (t * (b - a));
	}

	public static float hermite(final float a, final float b, final float c, final float d, final float t) {
		final float factorTimes2 = t * t;
		final float factor1 = (factorTimes2 * ((2 * t) - 3)) + 1;
		final float factor2 = (factorTimes2 * (t - 2)) + t;
		final float factor3 = factorTimes2 * (t - 1);
		final float factor4 = factorTimes2 * (3 - (2 * t));
		return (a * factor1) + (b * factor2) + (c * factor3) + (d * factor4);
	}

	public static float bezier(final float a, final float b, final float c, final float d, final float t) {
		final float invt = 1 - t;
		final float factorTimes2 = t * t;
		final float inverseFactorTimesTwo = invt * invt;
		final float factor1 = inverseFactorTimesTwo * invt;
		final float factor2 = 3 * t * inverseFactorTimesTwo;
		final float factor3 = 3 * factorTimes2 * invt;
		final float factor4 = factorTimes2 * t;

		return (a * factor1) + (b * factor2) + (c * factor3) + (d * factor4);
	}

	public static final float EPSILON = 0.000001f;

	public static float[] slerp(final float[] out, final float[] a, final float[] b, final float t) {
		return slerp(out, a, 0, b, 0, t);
	}

	/** {@link #slerp(float[], float[], float[], float)} of the quaternions at the given offsets of a and b. */
	public static float[] slerp(final float[] out, final float[] a, final int aOffset, final float[] b,
			final int bOffset, final float t) {
		final float ax = a[aOffset], ay = a[aOffset + 1], az = a[aOffset + 2], aw = a[aOffset + 3];
		final float bx = b[bOffset], by = b[bOffset + 1], bz = b[bOffset + 2], bw = b[bOffset + 3];
		final long weights = slerpWeights(ax, ay, az, aw, bx, by, bz, bw, t);
		final float scale0 = Float.intBitsToFloat((int) (weights >>> 32));
		final float scale1 = Float.intBitsToFloat((int) weights);
		// calculate final values
		out[0] = (scale0 * ax) + (scale1 * bx);
		out[1] = (scale0 * ay) + (scale1 * by);
		out[2] = (scale0 * az) + (scale1 * bz);
		out[3] = (scale0 * aw) + (scale1 * bw);

		return out;
	}

	/**
	 * The two weights of a slerp from a to b, packed in a long (the first in the high half). When b has to be taken
	 * negated to go the shorter way, the second weight is negated instead, which gives the same products.
	 */
	private static long slerpWeights(final float ax, final float ay, final float az, final float aw, final float bx,
			final float by, final float bz, final float bw, final float t) {
		float omega, cosom, sinom, scale0, scale1;

		// calc cosine
		cosom = (ax * bx) + (ay * by) + (az * bz) + (aw * bw);
		// adjust signs (if necessary)
		final boolean negated = cosom < 0.0;
		if (negated) {
			cosom = -cosom;
		}
		// calculate coefficients
		if ((1.0 - cosom) > EPSILON) {
			// standard case (slerp)
			omega = (float) Math.acos(cosom);
			sinom = (float) Math.sin(omega);
			scale0 = (float) (Math.sin((1.0 - t) * omega) / sinom);
			scale1 = (float) (Math.sin(t * omega) / sinom);
		}
		else {
			// "from" and "to" quaternions are very close
			// ... so we can do a linear interpolation
			scale0 = 1.0f - t;
			scale1 = t;
		}
		if (negated) {
			scale1 = -scale1;
		}
		return ((long) Float.floatToRawIntBits(scale0) << 32) | (Float.floatToRawIntBits(scale1) & 0xFFFFFFFFL);
	}

	/** Size of what {@link #slerpSetup} keeps for a pair of quaternions. */
	public static final int SLERP_SETUP_SIZE = 3;
	private static final float SLERP = 1;
	private static final float SLERP_FLIPPED = 2;
	private static final float LERP = 3;
	private static final float LERP_FLIPPED = 4;

	public static float[] sqlerp(final float[] out, final float[] a, final float[] b, final float[] c, final float[] d,
			final float t) {
		return sqlerp(out, a, 0, b, 0, c, 0, d, 0, t);
	}

	/**
	 * {@link #sqlerp(float[], float[], float[], float[], float[], float)} of the quaternions at the given offsets;
	 * the two intermediate quaternions are kept in locals, so any thread can run it.
	 */
	public static float[] sqlerp(final float[] out, final float[] a, final int aOffset, final float[] b,
			final int bOffset, final float[] c, final int cOffset, final float[] d, final int dOffset, final float t) {
		final float ax = a[aOffset], ay = a[aOffset + 1], az = a[aOffset + 2], aw = a[aOffset + 3];
		final float dx = d[dOffset], dy = d[dOffset + 1], dz = d[dOffset + 2], dw = d[dOffset + 3];
		final long weights1 = slerpWeights(ax, ay, az, aw, dx, dy, dz, dw, t);
		final float scale10 = Float.intBitsToFloat((int) (weights1 >>> 32));
		final float scale11 = Float.intBitsToFloat((int) weights1);
		final float x1 = (scale10 * ax) + (scale11 * dx);
		final float y1 = (scale10 * ay) + (scale11 * dy);
		final float z1 = (scale10 * az) + (scale11 * dz);
		final float w1 = (scale10 * aw) + (scale11 * dw);

		final float bx = b[bOffset], by = b[bOffset + 1], bz = b[bOffset + 2], bw = b[bOffset + 3];
		final float cx = c[cOffset], cy = c[cOffset + 1], cz = c[cOffset + 2], cw = c[cOffset + 3];
		final long weights2 = slerpWeights(bx, by, bz, bw, cx, cy, cz, cw, t);
		final float scale20 = Float.intBitsToFloat((int) (weights2 >>> 32));
		final float scale21 = Float.intBitsToFloat((int) weights2);
		final float x2 = (scale20 * bx) + (scale21 * cx);
		final float y2 = (scale20 * by) + (scale21 * cy);
		final float z2 = (scale20 * bz) + (scale21 * cz);
		final float w2 = (scale20 * bw) + (scale21 * cw);

		final long weights = slerpWeights(x1, y1, z1, w1, x2, y2, z2, w2, 2 * t * (1 - t));
		final float scale0 = Float.intBitsToFloat((int) (weights >>> 32));
		final float scale1 = Float.intBitsToFloat((int) weights);
		out[0] = (scale0 * x1) + (scale1 * x2);
		out[1] = (scale0 * y1) + (scale1 * y2);
		out[2] = (scale0 * z1) + (scale1 * z2);
		out[3] = (scale0 * w1) + (scale1 * w2);
		return out;
	}

	/**
	 * The part of {@link #slerp} that depends only on the two quaternions (the angle between them and its sine, with
	 * the way they are combined), kept at offset in setup; the keys of an animation are fixed, so it is worked out
	 * once for each pair of them.
	 */
	public static void slerpSetup(final float[] a, final int aOffset, final float[] b, final int bOffset,
			final float[] setup, final int offset) {
		float cosom = (a[aOffset] * b[bOffset]) + (a[aOffset + 1] * b[bOffset + 1]) + (a[aOffset + 2] * b[bOffset + 2])
				+ (a[aOffset + 3] * b[bOffset + 3]);
		final boolean flipped = cosom < 0.0;
		if (flipped) {
			cosom = -cosom;
		}
		if ((1.0 - cosom) > EPSILON) {
			final float omega = (float) Math.acos(cosom);
			setup[offset] = omega;
			setup[offset + 1] = (float) Math.sin(omega);
			setup[offset + 2] = flipped ? SLERP_FLIPPED : SLERP;
		}
		else {
			setup[offset + 2] = flipped ? LERP_FLIPPED : LERP;
		}
	}

	/** The weights of a slerp whose angle terms come from {@link #slerpSetup}, packed as {@link #slerpWeights} does. */
	private static long slerpWeights(final float t, final float[] setup, final int offset) {
		final float kind = setup[offset + 2];
		final float scale0, scale1;
		if ((kind == SLERP) || (kind == SLERP_FLIPPED)) {
			final float omega = setup[offset];
			final float sinom = setup[offset + 1];
			scale0 = (float) (Math.sin((1.0 - t) * omega) / sinom);
			scale1 = (float) (Math.sin(t * omega) / sinom);
		}
		else {
			scale0 = 1.0f - t;
			scale1 = t;
		}
		final float second = ((kind == SLERP_FLIPPED) || (kind == LERP_FLIPPED)) ? -scale1 : scale1;
		return ((long) Float.floatToRawIntBits(scale0) << 32) | (Float.floatToRawIntBits(second) & 0xFFFFFFFFL);
	}

	/**
	 * The same as {@link #slerp(float[], float[], int, float[], int, float)}, with the part of it that depends only on
	 * a and b taken from {@link #slerpSetup}.
	 */
	public static float[] slerp(final float[] out, final float[] a, final int aOffset, final float[] b,
			final int bOffset, final float t, final float[] setup, final int offset) {
		final long weights = slerpWeights(t, setup, offset);
		final float scale0 = Float.intBitsToFloat((int) (weights >>> 32));
		final float scale1 = Float.intBitsToFloat((int) weights);
		out[0] = (scale0 * a[aOffset]) + (scale1 * b[bOffset]);
		out[1] = (scale0 * a[aOffset + 1]) + (scale1 * b[bOffset + 1]);
		out[2] = (scale0 * a[aOffset + 2]) + (scale1 * b[bOffset + 2]);
		out[3] = (scale0 * a[aOffset + 3]) + (scale1 * b[bOffset + 3]);
		return out;
	}

	/**
	 * The same as {@link #sqlerp(float[], float[], int, float[], int, float[], int, float[], int, float)}, with the
	 * setups of the pairs (a, d) at offset and (b, c) after it in setup, both from {@link #slerpSetup}.
	 */
	public static float[] sqlerp(final float[] out, final float[] a, final int aOffset, final float[] b,
			final int bOffset, final float[] c, final int cOffset, final float[] d, final int dOffset, final float t,
			final float[] setup, final int offset) {
		final float ax = a[aOffset], ay = a[aOffset + 1], az = a[aOffset + 2], aw = a[aOffset + 3];
		final float dx = d[dOffset], dy = d[dOffset + 1], dz = d[dOffset + 2], dw = d[dOffset + 3];
		final long weights1 = slerpWeights(t, setup, offset);
		final float scale10 = Float.intBitsToFloat((int) (weights1 >>> 32));
		final float scale11 = Float.intBitsToFloat((int) weights1);
		final float x1 = (scale10 * ax) + (scale11 * dx);
		final float y1 = (scale10 * ay) + (scale11 * dy);
		final float z1 = (scale10 * az) + (scale11 * dz);
		final float w1 = (scale10 * aw) + (scale11 * dw);

		final float bx = b[bOffset], by = b[bOffset + 1], bz = b[bOffset + 2], bw = b[bOffset + 3];
		final float cx = c[cOffset], cy = c[cOffset + 1], cz = c[cOffset + 2], cw = c[cOffset + 3];
		final long weights2 = slerpWeights(t, setup, offset + SLERP_SETUP_SIZE);
		final float scale20 = Float.intBitsToFloat((int) (weights2 >>> 32));
		final float scale21 = Float.intBitsToFloat((int) weights2);
		final float x2 = (scale20 * bx) + (scale21 * cx);
		final float y2 = (scale20 * by) + (scale21 * cy);
		final float z2 = (scale20 * bz) + (scale21 * cz);
		final float w2 = (scale20 * bw) + (scale21 * cw);

		final long weights = slerpWeights(x1, y1, z1, w1, x2, y2, z2, w2, 2 * t * (1 - t));
		final float scale0 = Float.intBitsToFloat((int) (weights >>> 32));
		final float scale1 = Float.intBitsToFloat((int) weights);
		out[0] = (scale0 * x1) + (scale1 * x2);
		out[1] = (scale0 * y1) + (scale1 * y2);
		out[2] = (scale0 * z1) + (scale1 * z2);
		out[3] = (scale0 * w1) + (scale1 * w2);
		return out;
	}

	static final Vector3 best = new Vector3();
	static final Vector3 tmp = new Vector3();
	static final Vector3 tmp1 = new Vector3();
	static final Vector3 tmp2 = new Vector3();
	static final Vector3 tmp3 = new Vector3();

	/**
	 * Intersects the given ray with list of triangles. Returns the nearest
	 * intersection point in intersection
	 *
	 * @param ray          The ray
	 * @param vertices     the vertices
	 * @param indices      the indices, each successive 3 shorts index the 3
	 *                     vertices of a triangle
	 * @param vertexSize   the size of a vertex in floats
	 * @param intersection The nearest intersection point (optional)
	 * @return Whether the ray and the triangles intersect.
	 */
	public static boolean intersectRayTriangles(final Ray ray, final float[] vertices, final int[] indices,
			final int vertexSize, final Vector3 intersection) {
		return intersectRayTriangles(ray, vertices, indices, null, indices.length, vertexSize, intersection);
	}

	/** The same with the unsigned 16 bit indices of a model, kept as chars to halve their size. */
	public static boolean intersectRayTriangles(final Ray ray, final float[] vertices, final char[] indices,
			final int vertexSize, final Vector3 intersection) {
		return intersectRayTriangles(ray, vertices, null, indices, indices.length, vertexSize, intersection);
	}

	/** The same with vertices and faces in native buffers, which a model keeps for picking. */
	public static boolean intersectRayTriangles(final Ray ray, final FloatBuffer vertices, final CharBuffer indices,
			final int vertexSize, final Vector3 intersection) {
		float min_dist = Float.MAX_VALUE;
		boolean hit = false;
		final int indexCount = indices.limit();

		if ((indexCount % 3) != 0) {
			throw new RuntimeException("triangle list size is not a multiple of 3");
		}

		for (int i = 0; i < indexCount; i += 3) {
			final int i1 = indices.get(i) * vertexSize;
			final int i2 = indices.get(i + 1) * vertexSize;
			final int i3 = indices.get(i + 2) * vertexSize;

			final boolean result = FixedIntersector.intersectRayTriangle(ray,
					tmp1.set(vertices.get(i1), vertices.get(i1 + 1), vertices.get(i1 + 2)),
					tmp2.set(vertices.get(i2), vertices.get(i2 + 1), vertices.get(i2 + 2)),
					tmp3.set(vertices.get(i3), vertices.get(i3 + 1), vertices.get(i3 + 2)), tmp);

			if (result == true) {
				final float dist = ray.origin.dst2(tmp);
				if (dist < min_dist) {
					min_dist = dist;
					best.set(tmp);
					hit = true;
				}
			}
		}

		if (hit == false) {
			return false;
		}
		else {
			if (intersection != null) {
				intersection.set(best);
			}
			return true;
		}
	}

	/** The same for the triangles whose first corners are listed in faces, from start to end (exclusive). */
	public static boolean intersectRayTriangles(final Ray ray, final FloatBuffer vertices, final CharBuffer indices,
			final int vertexSize, final int[] faces, final int start, final int end, final Vector3 intersection) {
		float min_dist = Float.MAX_VALUE;
		boolean hit = false;
		for (int f = start; f < end; f++) {
			final int i = faces[f];
			final int i1 = indices.get(i) * vertexSize;
			final int i2 = indices.get(i + 1) * vertexSize;
			final int i3 = indices.get(i + 2) * vertexSize;

			final boolean result = FixedIntersector.intersectRayTriangle(ray,
					tmp1.set(vertices.get(i1), vertices.get(i1 + 1), vertices.get(i1 + 2)),
					tmp2.set(vertices.get(i2), vertices.get(i2 + 1), vertices.get(i2 + 2)),
					tmp3.set(vertices.get(i3), vertices.get(i3 + 1), vertices.get(i3 + 2)), tmp);

			if (result) {
				final float dist = ray.origin.dst2(tmp);
				if (dist < min_dist) {
					min_dist = dist;
					best.set(tmp);
					hit = true;
				}
			}
		}
		if (!hit) {
			return false;
		}
		if (intersection != null) {
			intersection.set(best);
		}
		return true;
	}

	private static boolean intersectRayTriangles(final Ray ray, final float[] vertices, final int[] intIndices,
			final char[] charIndices, final int indexCount, final int vertexSize, final Vector3 intersection) {
		float min_dist = Float.MAX_VALUE;
		boolean hit = false;

		if ((indexCount % 3) != 0) {
			throw new RuntimeException("triangle list size is not a multiple of 3");
		}

		for (int i = 0; i < indexCount; i += 3) {
			final int i1 = (intIndices != null ? intIndices[i] : charIndices[i]) * vertexSize;
			final int i2 = (intIndices != null ? intIndices[i + 1] : charIndices[i + 1]) * vertexSize;
			final int i3 = (intIndices != null ? intIndices[i + 2] : charIndices[i + 2]) * vertexSize;

			final boolean result = FixedIntersector.intersectRayTriangle(ray,
					tmp1.set(vertices[i1], vertices[i1 + 1], vertices[i1 + 2]),
					tmp2.set(vertices[i2], vertices[i2 + 1], vertices[i2 + 2]),
					tmp3.set(vertices[i3], vertices[i3 + 1], vertices[i3 + 2]), tmp);

			if (result == true) {
				final float dist = ray.origin.dst2(tmp);
				if (dist < min_dist) {
					min_dist = dist;
					best.set(tmp);
					hit = true;
				}
			}
		}

		if (hit == false) {
			return false;
		}
		else {
			if (intersection != null) {
				intersection.set(best);
			}
			return true;
		}
	}

	/**
	 * Intersects the given ray with list of triangles. Returns the nearest
	 * intersection point in intersection
	 *
	 * @param ray          The ray
	 * @param vertices     the vertices
	 * @param indices      the indices, each successive 3 shorts index the 3
	 *                     vertices of a triangle
	 * @param vertexSize   the size of a vertex in floats
	 * @param intersection The nearest intersection point (optional)
	 * @return Whether the ray and the triangles intersect.
	 */
	public static boolean intersectRayTriangles(final Ray ray, final float[] vertices, final int[] indices,
			final int vertexSize, final Vector3 worldLocation, final float facingRadians, final Vector3 intersection) {
		float min_dist = Float.MAX_VALUE;
		boolean hit = false;

		if ((indices.length % 3) != 0) {
			throw new RuntimeException("triangle list size is not a multiple of 3");
		}

		final float facingX_X = (float) Math.cos(facingRadians);
		final float facingX_Y = (float) Math.sin(facingRadians);
		final double halfPi = Math.PI / 2;
		final float facingY_X = (float) Math.cos(facingRadians + halfPi);
		final float facingY_Y = (float) Math.sin(facingRadians + halfPi);
		for (int i = 0; i < indices.length; i += 3) {
			final int i1 = indices[i] * vertexSize;
			final int i2 = indices[i + 1] * vertexSize;
			final int i3 = indices[i + 2] * vertexSize;

			final boolean result = Intersector.intersectRayTriangle(ray,
					tmp1.set((vertices[i1] * facingX_X) + (vertices[i1 + 1] * facingY_X) + worldLocation.x,
							(vertices[i1] * facingX_Y) + (vertices[i1 + 1] * facingY_Y) + worldLocation.y,
							vertices[i1 + 2] + worldLocation.z),
					tmp2.set((vertices[i2] * facingX_X) + (vertices[i2 + 1] * facingY_X) + worldLocation.x,
							(vertices[i2] * facingX_Y) + (vertices[i2 + 1] * facingY_Y) + worldLocation.y,
							vertices[i2 + 2] + worldLocation.z),
					tmp3.set((vertices[i3] * facingX_X) + (vertices[i3 + 1] * facingY_X) + worldLocation.x,
							(vertices[i3] * facingX_Y) + (vertices[i3 + 1] * facingY_Y) + worldLocation.y,
							vertices[i3 + 2] + worldLocation.z),
					tmp);

			if (result == true) {
				final float dist = ray.origin.dst2(tmp);
				if (dist < min_dist) {
					min_dist = dist;
					best.set(tmp);
					hit = true;
				}
			}
		}

		if (hit == false) {
			return false;
		}
		else {
			if (intersection != null) {
				intersection.set(best);
			}
			return true;
		}
	}

	// ==== All of the following "wrap" calls are horribly inefficient. Eventually
	// they should be removed entirely with better design.
	// Until that happens, be sure to only call them during setup and not while the
	// simulation is live. Otherwise you'll probably get some
	// bad lag (and memory leaks?).
	public static ShortBuffer wrapFaces(final short[] faces) {
		final ShortBuffer wrapper = ByteBuffer.allocateDirect(faces.length * 2).order(ByteOrder.nativeOrder())
				.asShortBuffer();
		wrapper.put(faces);
		wrapper.clear();
		return wrapper;
	}

	public static ByteBuffer wrap(final byte[] skin) {
		final ByteBuffer wrapper = ByteBuffer.allocateDirect(skin.length).order(ByteOrder.nativeOrder());
		wrapper.put(skin);
		wrapper.clear();
		return wrapper;
	}

	public static FloatBuffer wrap(final float[] positions) {
		final FloatBuffer wrapper = ByteBuffer.allocateDirect(positions.length * 4).order(ByteOrder.nativeOrder())
				.asFloatBuffer();
		wrapper.put(positions);
		wrapper.clear();
		return wrapper;
	}

	public static IntBuffer wrap(final int[] positions) {
		final IntBuffer wrapper = ByteBuffer.allocateDirect(positions.length * 4).order(ByteOrder.nativeOrder())
				.asIntBuffer();
		wrapper.put(positions);
		wrapper.clear();
		return wrapper;
	}

	public static ByteBuffer wrapAsBytes(final int[] positions) {
		final ByteBuffer wrapper = ByteBuffer.allocateDirect(positions.length).order(ByteOrder.nativeOrder());
		for (final int face : positions) {
			wrapper.put((byte) face);
		}
		wrapper.clear();
		return wrapper;
	}

	public static Buffer wrap(final short[] cornerTextures) {
		final ByteBuffer wrapper = ByteBuffer.allocateDirect(cornerTextures.length).order(ByteOrder.nativeOrder());
		for (final short face : cornerTextures) {
			wrapper.put((byte) face);
		}
		wrapper.clear();
		return wrapper;
	}

	public static Buffer wrapShort(final short[] cornerTextures) {
		final ByteBuffer wrapper = ByteBuffer.allocateDirect(cornerTextures.length * 2).order(ByteOrder.nativeOrder());
		for (final short face : cornerTextures) {
			wrapper.putShort(face);
		}
		wrapper.clear();
		return wrapper;
	}

	public static Buffer wrapPairs(final float[][] quadVertices) {
		final FloatBuffer wrapper = ByteBuffer.allocateDirect(quadVertices.length * 8).order(ByteOrder.nativeOrder())
				.asFloatBuffer();
		for (int i = 0; i < quadVertices.length; i++) {
			for (int j = 0; j < 2; j++) {
				wrapper.put(quadVertices[i][j]);
			}
		}
		wrapper.clear();
		return wrapper;
	}

	public static Buffer wrap(final int[][] quadIndices) {
		final IntBuffer wrapper = ByteBuffer.allocateDirect(quadIndices.length * 3 * 4).order(ByteOrder.nativeOrder())
				.asIntBuffer();
		for (int i = 0; i < quadIndices.length; i++) {
			for (int j = 0; j < 3; j++) {
				wrapper.put(quadIndices[i][j]);
			}
		}
		wrapper.clear();
		return wrapper;
	}

	public static Buffer wrap(final List<float[]> vertices) {
		if (vertices.isEmpty()) {
			return null;
		}
		final int expectedNumberOfFloats = vertices.get(0).length;
		final FloatBuffer wrapper = ByteBuffer.allocateDirect(vertices.size() * expectedNumberOfFloats * 4)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		for (final float[] subArray : vertices) {
			for (final float f : subArray) {
				wrapper.put(f);
			}
		}
		wrapper.clear();
		return wrapper;
	}

	public static Buffer wrapFaces(final List<int[]> indices) {
		if (indices.isEmpty()) {
			return null;
		}
		final int expectedNumberOfValues = indices.get(0).length;
		final ShortBuffer wrapper = ByteBuffer.allocateDirect(indices.size() * expectedNumberOfValues * 2)
				.order(ByteOrder.nativeOrder()).asShortBuffer();
		for (final int[] subArray : indices) {
			for (final int value : subArray) {
				wrapper.put((short) value);
			}
		}
		wrapper.clear();
		return wrapper;
	}
}
