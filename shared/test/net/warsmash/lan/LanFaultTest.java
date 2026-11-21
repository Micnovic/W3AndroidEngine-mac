package net.warsmash.lan;

import static net.warsmash.lan.LanSessionTest.sender;
import static net.warsmash.lan.LanSessionTest.settle;
import static net.warsmash.lan.LanTestSupport.assertConsistent;
import static net.warsmash.lan.LanTestSupport.command;
import static net.warsmash.lan.LanTestSupport.waitFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.warsmash.lan.LanTestSupport.Node;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.transport.LanFaultInjector;

class LanFaultTest {
	private final LanTestSupport env = new LanTestSupport();

	@AfterEach
	void tearDown() throws Exception {
		this.env.close();
		waitFor("network threads to end", 3000, () -> LanTestSupport.liveLanThreads().isEmpty());
	}

	@Test
	void delayAndJitterKeepTurnsIdenticalAndCommandsExactlyOnce() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		final Node c = this.env.joinAndWait("C");
		this.env.startGame(a, b, c);
		final LanFaultInjector hostFaults = new LanFaultInjector(1);
		hostFaults.setDelay(15, 20);
		this.env.hostLoop.setFaultInjector(hostFaults);
		final Node[] nodes = { a, b, c };
		for (int i = 0; i < nodes.length; i++) {
			final LanFaultInjector f = new LanFaultInjector(10 + i);
			f.setDelay(10 * i, 25);
			nodes[i].loop.setFaultInjector(f);
		}
		final Thread[] senders = new Thread[3];
		for (int i = 0; i < 3; i++) {
			senders[i] = sender(nodes[i], i, 120, 20 + i);
		}
		for (final Thread t : senders) {
			t.join();
		}
		settle(120, a, b, c);
		assertConsistent(120, a, b, c);
		assertTrue(a.rec.turns.size() > 20);
	}

	@Test
	void freezeStallsEveryoneThenTheGameContinuesWithNothingLost() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		final LanFaultInjector f = new LanFaultInjector(3);
		b.loop.setFaultInjector(f);
		final Thread sa = sender(a, 0, 100, 1);
		final Thread sb = sender(b, 1, 100, 2);
		waitFor("traffic", () -> a.client.lastTurn() > 10);
		f.freeze(700);
		final int at = a.client.lastTurn();
		waitFor("lag notice", () -> a.rec.lagEvents > 0);
		// the other side runs out of sync limit and stops, it does not run ahead
		assertTrue(a.client.lastTurn() <= at + 16, "turns ran past the sync limit");
		sa.join();
		sb.join();
		settle(100, a, b);
		waitFor("lag end", () -> a.rec.lagEnds > 0);
		assertConsistent(100, a, b);
	}

	@Test
	void aLongFreezeBreaksTheLinkAndTheClientComesBackThroughRejoin() throws Exception {
		this.env.timings.silenceMs = 600;
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		final LanFaultInjector f = new LanFaultInjector(4);
		b.loop.setFaultInjector(f);
		final Thread sa = sender(a, 0, 80, 1);
		final Thread sb = sender(b, 1, 80, 2);
		waitFor("traffic", () -> b.client.lastTurn() > 10);
		f.freeze(5000);
		waitFor("link broken", () -> !b.rec.disconnects.isEmpty() && b.client.pendingCommands() >= 0);
		f.thaw();
		sa.join();
		sb.join();
		b.client.reconnect();
		waitFor("rejoined", () -> b.rec.caughtUp > 0);
		settle(80, a, b);
		assertConsistent(80, a, b);
	}

	@Test
	void aCutWithCommandsInFlightLosesAndDuplicatesNothing() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		final Node c = this.env.joinAndWait("C");
		this.env.startGame(a, b, c);
		final LanFaultInjector f = new LanFaultInjector(5);
		f.setDelay(5, 10);
		b.loop.setFaultInjector(f);
		final Thread[] senders = { sender(a, 0, 150, 1), sender(b, 1, 150, 2), sender(c, 2, 150, 3) };
		for (int r = 0; r < 3; r++) {
			final int round = r;
			final int mark = b.client.lastTurn();
			waitFor("progress before cut " + round, () -> b.client.lastTurn() > mark + 8);
			f.cut();
			waitFor("cut seen " + round, () -> b.rec.disconnects.size() > round);
			// the others are held by the sync limit while b is gone, commands keep queueing on b
			Thread.sleep(60);
			b.client.reconnect();
			final int expected = round + 1;
			waitFor("rejoin " + round, () -> b.rec.caughtUp >= expected);
		}
		for (final Thread t : senders) {
			t.join();
		}
		settle(150, a, b, c);
		assertConsistent(150, a, b, c);
		assertEquals(3, b.rec.rejoins);
		assertEquals(LanProtocol.PRESENCE_PLAYING, this.env.host.presenceOf(b.client.participantId()));
	}

	@Test
	void clientThatNeverReturnsBecomesAbsentAndTheOthersContinue() throws Exception {
		this.env.timings.absentAfterMs = 300;
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		waitFor("traffic", () -> a.client.lastTurn() > 5);
		b.client.close();
		waitFor("absent", () -> this.env.host.presenceOf(b.client.participantId()) == LanProtocol.PRESENCE_ABSENT);
		final int at = a.client.lastTurn();
		waitFor("others continue", () -> a.client.lastTurn() > at + 30);
		// b was away for a while; it still gets the whole game when it returns
		final InetSocketAddress addr = this.env.address();
		b.client.reconnect(addr);
		waitFor("caught up", () -> b.rec.caughtUp > 0);
		waitFor("playing again", () -> this.env.host.presenceOf(b.client.participantId()) == LanProtocol.PRESENCE_PLAYING);
		b.client.sendCommand(command(1, 1));
		waitFor("command executed", () -> b.client.pendingCommands() == 0);
		final int target = a.client.lastTurn() + 10;
		waitFor("in step", () -> b.client.lastTurn() >= target && a.client.lastTurn() >= target);
		boolean absentSeen = false;
		boolean backSeen = false;
		for (final net.warsmash.lan.protocol.LanData.TurnData t : a.rec.turns) {
			for (final net.warsmash.lan.protocol.LanData.PresenceEntry p : t.presence) {
				absentSeen |= p.state == LanProtocol.PRESENCE_ABSENT;
				backSeen |= p.state == LanProtocol.PRESENCE_PLAYING;
			}
		}
		assertTrue(absentSeen && backSeen, "presence changes travel inside the turn stream");
		final int common = Math.min(a.rec.turns.size(), b.rec.turns.size());
		for (int i = 0; i < common; i++) {
			assertTrue(java.util.Arrays.equals(a.rec.turns.get(i).toBytes(), b.rec.turns.get(i).toBytes()),
					"turn " + i + " differs after rejoin");
		}
	}

	@Test
	void aCommandTooLongToTravelIsRefusedAndTheNextOnesStillArrive() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		waitFor("traffic", () -> a.client.lastTurn() > 2);
		assertEquals(-1, a.client.sendCommand(new byte[LanProtocol.MAX_COMMAND_BYTES + 1]));
		assertEquals(1, a.client.sendCommand(command(1, 1)));
		assertEquals(2, a.client.sendCommand(command(1, 2)));
		waitFor("commands executed", () -> a.client.pendingCommands() == 0);
		assertTrue(a.rec.disconnects.isEmpty(), "the link stays up: " + a.rec.disconnects);
		assertEquals(LanProtocol.PRESENCE_PLAYING, this.env.host.presenceOf(a.client.participantId()));
	}

	@Test
	void aGameWhoseOnlyActivePlayerLeavesStaysOpenForTheAbsentOne() throws Exception {
		this.env.timings.absentAfterMs = 300;
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		waitFor("traffic", () -> a.client.lastTurn() > 5);
		b.client.close();
		waitFor("absent", () -> this.env.host.presenceOf(b.client.participantId()) == LanProtocol.PRESENCE_ABSENT);
		a.client.leave(LanProtocol.LEAVE_QUIT);
		waitFor("a gone", () -> this.env.host.presenceOf(a.client.participantId()) == LanProtocol.PRESENCE_LEFT);
		Thread.sleep(600);
		assertEquals(LanProtocol.STATE_IN_GAME, this.env.host.state());
		b.client.reconnect(this.env.address());
		waitFor("caught up", () -> b.rec.caughtUp > 0);
		waitFor("playing again", () -> this.env.host.presenceOf(b.client.participantId()) == LanProtocol.PRESENCE_PLAYING);
		assertEquals(LanProtocol.STATE_IN_GAME, this.env.host.state());
	}

	@Test
	void continueWithoutThemReleasesAStalledGame() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		final LanFaultInjector f = new LanFaultInjector(6);
		b.loop.setFaultInjector(f);
		f.freeze(60000);
		waitFor("lag", () -> a.rec.lagEvents > 0);
		a.client.vote(LanProtocol.VOTE_CONTINUE_WITHOUT);
		waitFor("lag ended", () -> a.rec.lagEnds > 0);
		final int at = a.client.lastTurn();
		waitFor("running again", () -> a.client.lastTurn() > at + 20);
		assertEquals(LanProtocol.PRESENCE_ABSENT, this.env.host.presenceOf(b.client.participantId()));
	}

	@Test
	void checksumMismatchIsReportedToEveryone() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		a.client.checksum(20, 111);
		b.client.checksum(20, 111);
		a.client.checksum(40, 5);
		b.client.checksum(40, 6);
		waitFor("desync", () -> !a.rec.desyncs.isEmpty() && !b.rec.desyncs.isEmpty());
		assertEquals(40, a.rec.desyncs.get(0).tick);
		assertEquals(2, a.rec.desyncs.get(0).groups.size());
		assertEquals(1, a.rec.desyncs.size());
	}
}
