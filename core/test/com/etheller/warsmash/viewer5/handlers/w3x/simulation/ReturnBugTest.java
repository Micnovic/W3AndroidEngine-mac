package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.StringReader;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;

import net.warsmash.parsers.jass.SmashJassParser;

/**
 * Maps keep handles in integer tables by returning a handle from a function that returns an integer, and the other way
 * round.
 */
class ReturnBugTest {
	private static final String SCRIPT = String.join("\n", //
			"type agent extends handle", //
			"type group extends agent", //
			"type texttag extends handle", //
			"globals", //
			"    group g = null", //
			"    group back = null", //
			"    texttag tag = null", //
			"    agent agentBack = null", //
			"    integer id = -1", //
			"endglobals", //
			"function H2I takes handle h returns integer", //
			"    return h", //
			"endfunction", //
			"function I2G takes integer i returns group", //
			"    return i", //
			"endfunction", //
			"function I2TT takes integer i returns texttag", //
			"    return i", //
			"endfunction", //
			"function I2A takes integer i returns agent", //
			"    return i", //
			"endfunction", //
			"function ReadAsAgent takes nothing returns nothing", //
			"    set agentBack = I2A(id)", //
			"endfunction", //
			"function Convert takes nothing returns nothing", //
			"    set id = H2I(g)", //
			"    set back = I2G(id)", //
			"endfunction", //
			"function ReadBack takes nothing returns nothing", //
			"    set back = I2G(id)", //
			"endfunction", //
			"function Clear takes nothing returns nothing", //
			"    set g = null", //
			"    set back = null", //
			"endfunction", //
			"function ReadAsTextTag takes nothing returns nothing", //
			"    set tag = I2TT(id)", //
			"endfunction", //
			"");

	private static final class Group extends AllocatedHandle {
	}

	private final HandleIdAllocator allocator = new HandleIdAllocator();
	private final JassProgram program;
	private final GlobalScope scope;

	ReturnBugTest() throws Exception {
		this.program = new JassProgram();
		new SmashJassParser(new StringReader(SCRIPT)).scanAndParse("test.j", this.program);
		this.program.initialize();
		this.scope = this.program.getGlobalScope();
		this.scope.setHandleIdSource(this.allocator);
	}

	private void run(final String function) {
		final JassThread thread = this.scope.createThread(function, Collections.emptyList(),
				TriggerExecutionScope.EMPTY);
		this.scope.runThreadUntilCompletion(thread);
	}

	private void setGroup(final String global, final Object group) {
		this.scope.setGlobal(global, new HandleJassValue((HandleJassType) this.scope.parseType("group"), group));
	}

	private Object handle(final String global) {
		return this.scope.getGlobal(global).visit(ObjectJassValueVisitor.getInstance());
	}

	private int id() {
		return this.scope.getGlobal("id").visit(IntegerJassValueVisitor.getInstance());
	}

	@Test
	void aHandleReturnedAsIntegerIsItsIdAndComesBack() {
		final Group group = this.allocator.assign(new Group());
		setGroup("g", group);
		run("Convert");
		assertEquals(group.getHandleId(), id());
		assertEquals(HandleIdAllocator.FIRST_HANDLE_ID, id());
		assertSame(group, handle("back"));
	}

	@Test
	void nullIsZeroAndZeroIsNull() {
		run("Convert");
		assertEquals(0, id());
		assertNull(handle("back"));
	}

	@Test
	void anIdNoHandleHoldsIsNull() {
		this.scope.setGlobal("id", IntegerJassValue.of(0x100123));
		run("ReadBack");
		assertNull(handle("back"));
	}

	@Test
	void aDestroyedHandleKeepsItsIdUntilTheIdIsFreedThenTheNextHolderIsRead() {
		final Group group = this.allocator.assign(new Group());
		setGroup("g", group);
		run("Convert");
		final int groupId = id();

		this.allocator.release(group);
		run("ReadBack");
		// Until the end of the step the destroyed group still holds its id.
		assertSame(group, handle("back"));

		run("Clear");
		this.allocator.endStep();
		run("ReadBack");
		assertNull(handle("back"));

		final Group next = this.allocator.assign(new Group());
		assertEquals(groupId, next.getHandleId());
		setGroup("g", next);
		run("Convert");
		assertEquals(groupId, id());
		assertSame(next, handle("back"));
	}

	@Test
	void textTagNumbersAreNotHandleTableNumbers() {
		final Group group = this.allocator.assign(new Group());
		setGroup("g", group);
		run("Convert");
		run("ReadAsTextTag");
		assertNull(handle("tag"));
	}

	@Test
	void aHandleReadAsAnotherKindIsStillTheHandle() {
		final Group group = this.allocator.assign(new Group());
		setGroup("g", group);
		run("Convert");
		run("ReadAsAgent");
		assertSame(group, handle("agentBack"));
	}
}
