package com.etheller.interpreter.ast.execution.instruction;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;

/**
 * An integer returned from a function that returns a real is not converted: the original game reads its 32 bits as a
 * float.
 */
public final class IntegerBitsAsRealInstruction implements JassInstruction {
	public static final IntegerBitsAsRealInstruction INSTANCE = new IntegerBitsAsRealInstruction();

	@Override
	public void run(final JassThread thread) {
		final JassValue value = thread.stackFrame.peek();
		if (value instanceof IntegerJassValue) {
			thread.stackFrame.pop();
			thread.stackFrame.push(RealJassValue.of(Float.intBitsToFloat(((IntegerJassValue) value).getValue())));
		}
	}

	@Override
	public int opCost() {
		return 0;
	}
}
