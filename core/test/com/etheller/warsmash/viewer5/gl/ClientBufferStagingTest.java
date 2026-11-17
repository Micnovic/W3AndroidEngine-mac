package com.etheller.warsmash.viewer5.gl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.graphics.GL20;
import com.etheller.warsmash.viewer5.handlers.mdx.GeometryEmitterFuncs;

class ClientBufferStagingTest {
	private static GL20 noGl() {
		return (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[] { GL20.class },
				(proxy, method, args) -> {
					final Class<?> type = method.getReturnType();
					if (type == int.class) {
						return 1;
					}
					return null;
				});
	}

	@Test
	void stagedFloatsLandInTheBufferAndTheTailFlagReadsAsOneByte() {
		final ClientBuffer buffer = new ClientBuffer(noGl());
		buffer.reserve(2 * GeometryEmitterFuncs.BYTES_PER_OBJECT);
		assertEquals(2 * GeometryEmitterFuncs.FLOATS_PER_OBJECT, buffer.staging.length);

		final int second = GeometryEmitterFuncs.FLOATS_PER_OBJECT;
		buffer.staging[GeometryEmitterFuncs.FLOAT_OFFSET_P0 + 1] = 2.5f;
		buffer.staging[GeometryEmitterFuncs.FLOAT_OFFSET_HEALTH] = 7f;
		buffer.staging[GeometryEmitterFuncs.FLOAT_OFFSET_TAIL] = 0;
		buffer.staging[second + GeometryEmitterFuncs.FLOAT_OFFSET_P3 + 2] = -3f;
		buffer.staging[second + GeometryEmitterFuncs.FLOAT_OFFSET_TAIL] = GeometryEmitterFuncs.TAIL_FLAG;
		buffer.commitStaging(2 * GeometryEmitterFuncs.FLOATS_PER_OBJECT);

		assertEquals(2.5f, buffer.floatView.get(1));
		assertEquals(7f, buffer.floatView.get(GeometryEmitterFuncs.FLOAT_OFFSET_HEALTH));
		assertEquals(-3f, buffer.floatView.get(second + GeometryEmitterFuncs.FLOAT_OFFSET_P3 + 2));
		assertEquals(0, buffer.byteView.get(GeometryEmitterFuncs.BYTE_OFFSET_TAIL));
		assertEquals(1, buffer.byteView.get(GeometryEmitterFuncs.BYTES_PER_OBJECT + GeometryEmitterFuncs.BYTE_OFFSET_TAIL));
		assertEquals(0, buffer.byteView.get(GeometryEmitterFuncs.BYTES_PER_OBJECT + GeometryEmitterFuncs.BYTE_OFFSET_TEAM_COLOR));
	}

	@Test
	void stagingOnlyGrows() {
		final ClientBuffer buffer = new ClientBuffer(noGl());
		buffer.reserve(600);
		final float[] staging = buffer.staging;
		assertTrue(staging.length >= 150);
		buffer.reserve(300);
		assertSame(staging, buffer.staging);
		buffer.reserve(1200);
		assertTrue(buffer.staging.length >= 300);
	}

	@Test
	void commitAtABaseLeavesTheEarlierObjectsAlone() {
		final ClientBuffer buffer = new ClientBuffer(noGl());
		buffer.reserve(2 * GeometryEmitterFuncs.BYTES_PER_OBJECT);
		final int perObject = GeometryEmitterFuncs.FLOATS_PER_OBJECT;

		buffer.staging[GeometryEmitterFuncs.FLOAT_OFFSET_HEALTH] = 1f;
		buffer.commitStaging(0, perObject);
		buffer.staging[perObject + GeometryEmitterFuncs.FLOAT_OFFSET_HEALTH] = 2f;
		buffer.commitStaging(perObject, perObject);

		assertEquals(1f, buffer.floatView.get(GeometryEmitterFuncs.FLOAT_OFFSET_HEALTH));
		assertEquals(2f, buffer.floatView.get(perObject + GeometryEmitterFuncs.FLOAT_OFFSET_HEALTH));
	}
}
