package com.etheller.interpreter.ast.execution.instruction;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.util.JassNumbers;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;

/**
 * An integer passed to a real parameter of a script function becomes a real, so that the parameter divides as one.
 */
public final class ParameterAsRealInstruction implements JassInstruction {
	private final int localId;

	public ParameterAsRealInstruction(final int localId) {
		this.localId = localId;
	}

	@Override
	public void run(final JassThread thread) {
		final JassValue value = thread.stackFrame.contents.get(this.localId);
		if (value instanceof IntegerJassValue) {
			thread.stackFrame.contents.set(this.localId,
					RealJassValue.of(JassNumbers.fromInt(((IntegerJassValue) value).getValue())));
		}
	}

	@Override
	public int opCost() {
		return 0;
	}
}
