package com.etheller.interpreter.ast.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.scope.trigger.TriggerBooleanExpression;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.triggers.BoolExprAnd;
import com.etheller.warsmash.parsers.jass.triggers.BoolExprNot;
import com.etheller.warsmash.parsers.jass.triggers.BoolExprOr;

import net.warsmash.parsers.jass.SmashJassParser;

/**
 * A failing script thread ends alone, integers stored into reals become reals, and boolexprs combine as in the
 * original game.
 */
class JassFailureTest {
	private static final String SCRIPT = String.join("\n", //
			"native Fail takes nothing returns nothing", //
			"native RunNow takes string name returns nothing", //
			"native OutOfRange takes integer i returns integer", //
			"globals", //
			"    real g = 5", //
			"    real array ra", //
			"    real r1 = 0", //
			"    real r2 = 0", //
			"    integer stage = 0", //
			"    integer read = -1", //
			"endglobals", //
			"function Reals takes nothing returns nothing", //
			"    set ra[0] = 5", //
			"    set r1 = g / 2", //
			"    set r2 = ra[0] / 2", //
			"endfunction", //
			"function Failing takes nothing returns nothing", //
			"    call Fail()", //
			"    set stage = 99", //
			"endfunction", //
			"function Caller takes nothing returns nothing", //
			"    set stage = 1", //
			"    call RunNow(\"Failing\")", //
			"    set stage = stage + 1", //
			"endfunction", //
			"function ReadOutOfRange takes nothing returns nothing", //
			"    set read = OutOfRange(5)", //
			"endfunction", //
			"");

	private static GlobalScope run(final String function) throws Exception {
		final JassProgram program = new JassProgram();
		program.getJassNativeManager().createNative("Fail", (arguments, globalScope, triggerScope) -> {
			throw new IllegalStateException("failing native");
		});
		program.getJassNativeManager().createNative("RunNow", (arguments, globalScope, triggerScope) -> {
			final String name = arguments.get(0).visit(StringJassValueVisitor.getInstance());
			globalScope.runThreadUntilCompletion(
					globalScope.createThread(name, Collections.emptyList(), triggerScope));
			return null;
		});
		program.getJassNativeManager().createNative("OutOfRange", (arguments, globalScope, triggerScope) -> {
			final int index = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
			return IntegerJassValue.of(Arrays.asList(1, 2).get(index));
		});
		new SmashJassParser(new StringReader(SCRIPT)).scanAndParse("test.j", program);
		program.initialize();
		final GlobalScope scope = program.getGlobalScope();
		scope.runThreadUntilCompletion(
				scope.createThread(function, Collections.emptyList(), TriggerExecutionScope.EMPTY));
		return scope;
	}

	private static int integer(final GlobalScope scope, final String name) {
		return scope.getGlobal(name).visit(IntegerJassValueVisitor.getInstance());
	}

	@Test
	void anIntegerStoredIntoARealDividesAsAReal() throws Exception {
		final GlobalScope scope = run("Reals");
		assertEquals(2.5, scope.getGlobal("r1").visit(RealJassValueVisitor.getInstance()));
		assertEquals(2.5, scope.getGlobal("r2").visit(RealJassValueVisitor.getInstance()));
	}

	@Test
	void aThreadThatFailsStopsAloneAndItsCallerGoesOn() throws Exception {
		assertEquals(2, integer(run("Caller"), "stage"));
	}

	@Test
	void aNativeReadingOutOfRangeGivesItsDefault() throws Exception {
		assertEquals(0, integer(run("ReadOutOfRange"), "read"));
	}

	@Test
	void boolexprsCombineAndANullIsFalse() {
		final TriggerBooleanExpression yes = (globalScope, triggerScope) -> true;
		final TriggerBooleanExpression no = (globalScope, triggerScope) -> false;
		assertFalse(new BoolExprNot(yes).evaluate(null, null));
		assertTrue(new BoolExprNot(no).evaluate(null, null));
		assertFalse(new BoolExprAnd(null, yes).evaluate(null, null));
		assertFalse(new BoolExprAnd(yes, no).evaluate(null, null));
		assertFalse(new BoolExprOr(no, null).evaluate(null, null));
		assertTrue(new BoolExprOr(null, yes).evaluate(null, null));
		assertFalse(new BoolExprNot(null).evaluate(null, null));
		assertTrue(new BoolExprNot(new BoolExprNot(null)).evaluate(null, null));
		assertFalse(new BoolExprOr(no, no).evaluate(null, null));
	}
}
