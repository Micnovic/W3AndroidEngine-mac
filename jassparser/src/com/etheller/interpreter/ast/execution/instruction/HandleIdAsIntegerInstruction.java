package com.etheller.interpreter.ast.execution.instruction;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;

/**
 * A function that returns an integer may return a handle: the original game hands back the handle's id, which maps
 * use to keep handles in integer tables. A real returned from an integer function is not converted either: its float
 * bits are the integer (1.29).
 */
public final class HandleIdAsIntegerInstruction implements JassInstruction {
	public static final HandleIdAsIntegerInstruction INSTANCE = new HandleIdAsIntegerInstruction();

	@Override
	public void run(final JassThread thread) {
		final JassValue value = thread.stackFrame.peek();
		if (value instanceof HandleJassValue) {
			thread.stackFrame.pop();
			final Object handle = ((HandleJassValue) value).getJavaValue();
			thread.stackFrame.push(IntegerJassValue.of(
					(handle == null) || (thread.globalScope.getHandleIdSource() == null) ? 0
							: thread.globalScope.getHandleIdSource().handleIdOf(handle)));
		}
		else if (value instanceof RealJassValue) {
			thread.stackFrame.pop();
			thread.stackFrame.push(
					IntegerJassValue.of(Float.floatToRawIntBits((float) ((RealJassValue) value).getValue())));
		}
	}

	@Override
	public int opCost() {
		return 0;
	}
}
