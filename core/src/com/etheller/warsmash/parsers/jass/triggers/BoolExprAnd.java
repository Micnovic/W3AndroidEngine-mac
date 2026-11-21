package com.etheller.warsmash.parsers.jass.triggers;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.scope.trigger.TriggerBooleanExpression;
import com.etheller.interpreter.ast.util.AllocatedHandle;

public class BoolExprAnd extends AllocatedHandle implements TriggerBooleanExpression {
	private final TriggerBooleanExpression operandA;
	private final TriggerBooleanExpression operandB;

	public BoolExprAnd(final TriggerBooleanExpression operandA, final TriggerBooleanExpression operandB) {
		this.operandA = operandA;
		this.operandB = operandB;
	}

	@Override
	public boolean evaluate(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
		return evaluate(this.operandA, globalScope, triggerScope) && evaluate(this.operandB, globalScope, triggerScope);
	}

	/** A null boolexpr is false inside a combination and as a trigger condition (1.29). */
	static boolean evaluate(final TriggerBooleanExpression operand, final GlobalScope globalScope,
			final TriggerExecutionScope triggerScope) {
		return (operand != null) && operand.evaluate(globalScope, triggerScope);
	}

}
