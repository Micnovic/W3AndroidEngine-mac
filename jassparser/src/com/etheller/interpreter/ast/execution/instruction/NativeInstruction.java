package com.etheller.interpreter.ast.execution.instruction;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.function.NativeJassFunction;
import com.etheller.interpreter.ast.util.JassProfiler;
import com.etheller.interpreter.ast.value.JassValue;

public class NativeInstruction implements JassInstruction {
	private final int nativeId;
	private final int argumentCount;

	public NativeInstruction(final int nativeId, final int argumentCount) {
		this.nativeId = nativeId;
		this.argumentCount = argumentCount;
	}

	@Override
	public void run(final JassThread thread) {
		final NativeJassFunction nativeFromId = thread.globalScope.getNativeById(this.nativeId);
		final List<JassValue> arguments;
		if (this.argumentCount == 0) {
			arguments = Collections.emptyList();
		}
		else {
			final JassValue[] values = new JassValue[this.argumentCount];
			for (int i = this.argumentCount - 1; i >= 0; i--) {
				values[i] = thread.stackFrame.pop();
			}
			arguments = Arrays.asList(values);
		}
		if (JassProfiler.ENABLED) {
			JassProfiler.enterNative(thread, nativeFromId.getName(), arguments);
		}
		final JassValue nativeReturnValue = nativeFromId.call(arguments, thread.globalScope, thread.triggerScope);
		if (JassProfiler.ENABLED) {
			JassProfiler.exitNative(thread);
		}
		thread.stackFrame.push(nativeReturnValue);
		ReturnInstruction.INSTANCE.run(thread);

	}
}
