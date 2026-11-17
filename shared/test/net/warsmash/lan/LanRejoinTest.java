package net.warsmash.lan;

import static net.warsmash.lan.LanSessionTest.sender;
import static net.warsmash.lan.LanSessionTest.settle;
import static net.warsmash.lan.LanTestSupport.assertConsistent;
import static net.warsmash.lan.LanTestSupport.command;
import static net.warsmash.lan.LanTestSupport.waitFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.warsmash.lan.LanTestSupport.Node;
import net.warsmash.lan.LanTestSupport.Recorder;
import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.protocol.LanCodec;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanData.TurnData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanProtocolException;
import net.warsmash.lan.protocol.SampleMessages;
import net.warsmash.lan.transport.LanFaultInjector;

class LanRejoinTest {
	private final LanTestSupport env = new LanTestSupport();

	@TempDir
	File tmp;

	@AfterEach
	void tearDown() throws Exception {
		this.env.close();
		waitFor("network threads to end", 3000, () -> LanTestSupport.liveLanThreads().isEmpty());
	}

	private static boolean sameTurns(final List<TurnData> a, final List<TurnData> b, final int count)
			throws LanProtocolException {
		for (int i = 0; i < count; i++) {
			if (!java.util.Arrays.equals(a.get(i).toBytes(), b.get(i).toBytes())) {
				return false;
			}
		}
		return true;
	}

	@Test
	void aBlockIsMuchSmallerThanItsTurnsAndRefusesWrongSizes() throws Exception {
		final LanMessages.TurnBlock block = SampleMessages.blockOf(0, 400);
		int plain = 0;
		for (final TurnData t : block.turns) {
			plain += t.toBytes().length;
		}
		assertTrue(block.packedLength() * 3 < plain, "packed " + block.packedLength() + " of " + plain);
		final byte[] frame = LanCodec.encodeFrame(block);
		final int body = LanProtocol.FRAME_HEADER;
		final LanMessages.TurnBlock back = (LanMessages.TurnBlock) LanCodec.decodeBody(LanProtocol.TURN_BLOCK, frame,
				body, frame.length - body);
		assertEquals(400, back.turns.size());
		assertEquals(399, back.turns.get(399).n);
		// the size in the header (after the u16 count) says more than the data holds
		final byte[] wrong = frame.clone();
		wrong[body + 5]++;
		assertThrows(LanProtocolException.class,
				() -> LanCodec.decodeBody(LanProtocol.TURN_BLOCK, wrong, body, wrong.length - body));
		final byte[] noData = frame.clone();
		noData[body + 10] ^= 0x55;
		assertThrows(LanProtocolException.class,
				() -> LanCodec.decodeBody(LanProtocol.TURN_BLOCK, noData, body, noData.length - body));
	}

	@Test
	void keysSurviveInAFolderAndGoWhenCleared() {
		final FileRejoinStore store = new FileRejoinStore(new File(this.tmp, "keys"));
		final RejoinKey key = RejoinKey.create(0xABCDEF0123L, new SecureRandom());
		assertNull(store.load(key.gameId));
		store.save(key);
		assertTrue(key.matches(key.gameId, new FileRejoinStore(new File(this.tmp, "keys")).load(key.gameId).token()));
		assertNull(store.load(1));
		store.clear(key.gameId);
		assertNull(store.load(key.gameId));
	}

	@Test
	void aKeyRemembersWhereTheGameWasPlayedAndTheStoreListsItsKeys() {
		final FileRejoinStore store = new FileRejoinStore(new File(this.tmp, "listed"));
		final RejoinKey bare = RejoinKey.create(7, new SecureRandom());
		final RejoinKey placed = RejoinKey.create(8, new SecureRandom()).withHost("192.168.1.40:6113");
		assertNull(bare.host());
		assertEquals("192.168.1.40:6113", RejoinKey.parse(placed.serialize()).host());
		assertNull(RejoinKey.parse(bare.serialize()).host());
		assertTrue(RejoinKey.parse(placed.serialize()).matches(8, placed.token()));
		store.save(bare);
		store.save(placed);
		assertEquals(2, store.all().size());
		assertEquals("192.168.1.40:6113", new FileRejoinStore(new File(this.tmp, "listed")).load(8).host());
		store.clear(7);
		assertEquals(1, store.all().size());
	}

	@Test
	void aPlayerThatLoadsAfterTheOthersWentOnKeepsTheTurnsItAlreadyHolds() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final LanClient.Config slow = LanTestSupport.clientConfig("B");
		slow.autoLoad = false;
		final Recorder rec = new Recorder();
		final Node b = this.env.join(slow, rec);
		waitFor("welcome B", () -> rec.welcome != null);
		a.client.setReady(true);
		b.client.setReady(true);
		waitFor("both ready", () -> a.rec.lobby != null && a.rec.lobby.slots.stream()
				.filter(s -> s.kind == LanProtocol.SLOT_HUMAN && s.ready).count() == 2);
		a.client.requestStart();
		// the host lets the others go when the load time is over, B still loading
		waitFor("go for B", 30000, () -> rec.go);
		final Thread sender = sender(a, 0, 40, 3);
		sender.join();
		waitFor("turns held by B", () -> b.client.lastTurn() > 20);
		final int held = rec.turns.size();
		b.client.loaded();
		waitFor("caught up", 30000, () -> rec.caughtUp > 0);
		assertEquals(1, rec.rejoins);
		assertTrue(rec.resyncs.isEmpty(), "a game that played none of its turns was told to start over");
		assertTrue(!b.client.isRunningJoin());
		waitFor("playing", () -> this.env.host.presenceOf(b.client.participantId()) == LanProtocol.PRESENCE_PLAYING);
		final int common = Math.min(a.rec.turns.size(), rec.turns.size());
		assertTrue(common >= held);
		assertTrue(sameTurns(a.rec.turns, rec.turns, common), "B got other turns");
		for (int i = 1; i < rec.turns.size(); i++) {
			assertEquals(rec.turns.get(i - 1).n + 1, rec.turns.get(i).n, "a turn was given twice or lost");
		}
	}

	@Test
	void aClientThatStartsOverPlaysTheWholeGameAndNumbersItsCommandsOnFromTheOldOnes() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		final Thread sa = sender(a, 0, 60, 1);
		final Thread sb = sender(b, 1, 60, 2);
		sa.join();
		sb.join();
		settle(60, a, b);
		final RejoinKey key = b.client.rejoinKey();
		assertNotNull(key);
		final int slot = LanTestSupport.slotOf(b);
		b.client.close();
		waitFor("seen as gone", () -> this.env.host.presenceOf(b.client.participantId()) >= 0);
		// the process restarted: it has the key and nothing else
		final LanClient.Config config = LanTestSupport.clientConfig("B");
		config.rejoinKey = key;
		final Recorder rec = new Recorder();
		final Node again = this.env.join(config, rec);
		waitFor("caught up", () -> rec.caughtUp > 0);
		assertEquals(1, rec.rejoins);
		assertTrue(again.client.isRunningJoin());
		long steps = 0;
		for (int i = 0; i < again.client.catchUpTotal(); i++) {
			steps += a.rec.turns.get(i).steps;
		}
		assertEquals(steps, again.client.catchUpSteps(), "the progress target is the steps of the archive");
		assertTrue(steps > 0);
		waitFor("playing again",
				() -> this.env.host.presenceOf(again.client.participantId()) == LanProtocol.PRESENCE_PLAYING);
		final int common = Math.min(a.rec.turns.size(), rec.turns.size());
		assertTrue(common > 20);
		assertTrue(sameTurns(a.rec.turns, rec.turns, common), "the rejoined client got other turns");
		// a command of the restarted client is not taken for one of the old numbers
		again.client.sendCommand(command(1, 61));
		waitFor("command in a turn", () -> again.client.pendingCommands() == 0);
		waitFor("command 61 in the turns", 3000, () -> {
			for (final TurnData t : a.rec.turns) {
				for (final LanData.CmdEntry e : t.cmds) {
					if (e.slot == slot && e.seq == 61) {
						return true;
					}
				}
			}
			return false;
		});
	}

	@Test
	void aLongArchiveComesInBlocksAndTheTurnsAfterItFollowWithoutAGap() throws Exception {
		final net.warsmash.lan.host.LanHost.Config hc = LanTestSupport.hostConfig();
		hc.turnIntervalMs = 1;
		this.env.startHost(hc);
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		waitFor("a long game", 30000, () -> a.client.lastTurn() > 2500);
		final RejoinKey key = b.client.rejoinKey();
		b.client.close();
		final LanClient.Config config = LanTestSupport.clientConfig("B");
		config.rejoinKey = key;
		final Recorder rec = new Recorder();
		final Node again = this.env.join(config, rec);
		waitFor("caught up", 30000, () -> rec.caughtUp > 0);
		waitFor("live turns after the archive", () -> again.client.lastTurn() > a.client.lastTurn() - 200);
		for (int i = 0; i < rec.turns.size(); i++) {
			assertEquals(i, rec.turns.get(i).n, "turns must come in order without gaps");
		}
		assertTrue(sameTurns(a.rec.turns, rec.turns, Math.min(a.rec.turns.size(), rec.turns.size()) - 1));
	}

	@Test
	void aBrokenLinkIsMendedWithoutAbsenceOrPresenceChanges() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final LanClient.Config config = LanTestSupport.clientConfig("B");
		config.reconnectMs = 5000;
		final Recorder rec = new Recorder();
		final Node b = this.env.join(config, rec);
		waitFor("welcome", () -> rec.welcome != null);
		this.env.startGame(a, b);
		final LanFaultInjector f = new LanFaultInjector(9);
		b.loop.setFaultInjector(f);
		final Thread sa = sender(a, 0, 100, 1);
		final Thread sb = sender(b, 1, 100, 2);
		for (int round = 0; round < 3; round++) {
			final int mark = b.client.lastTurn();
			final int expected = round + 1;
			waitFor("progress", () -> b.client.lastTurn() > mark + 8);
			f.cut();
			waitFor("mended " + expected, () -> rec.reconnected >= expected);
		}
		sa.join();
		sb.join();
		settle(100, a, b);
		assertConsistent(100, a, b);
		assertTrue(rec.disconnects.isEmpty(), "the loss was reported: " + rec.disconnects);
		assertEquals(3, rec.reconnecting);
		assertEquals(LanProtocol.PRESENCE_PLAYING, this.env.host.presenceOf(b.client.participantId()));
		for (final TurnData t : a.rec.turns) {
			assertTrue(t.presence.isEmpty(), "a mended link is not a change of presence");
		}
	}

	@Test
	void aLinkThatStaysBrokenIsReportedWhenTheTimeIsUp() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final LanClient.Config config = LanTestSupport.clientConfig("B");
		config.reconnectMs = 600;
		final Recorder rec = new Recorder();
		final Node b = this.env.join(config, rec);
		waitFor("welcome", () -> rec.welcome != null);
		this.env.startGame(a, b);
		waitFor("traffic", () -> b.client.lastTurn() > 5);
		this.env.hostLoop.shutdown();
		waitFor("reported", 10000, () -> !rec.disconnects.isEmpty());
		assertEquals(1, rec.reconnecting);
		assertEquals(0, rec.reconnected);
		assertEquals(1, rec.disconnects.size());
	}

	@Test
	void aRejoiningPlayersSumsAreCheckedAgainstTheGame() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		for (final int tick : new int[] { 20, 40, 60 }) {
			a.client.checksum(tick, tick * 3);
			b.client.checksum(tick, tick * 3);
		}
		settle(0, a, b);
		final RejoinKey key = b.client.rejoinKey();
		b.client.close();
		final LanClient.Config config = LanTestSupport.clientConfig("B");
		config.rejoinKey = key;
		config.autoCaughtUp = false;
		final Recorder rec = new Recorder();
		final Node again = this.env.join(config, rec);
		waitFor("rejoin answer", () -> rec.rejoins > 0);
		waitFor("all turns", () -> again.client.lastTurn() >= again.client.catchUpTotal() - 1);
		again.client.checksum(20, 60);
		again.client.checksum(40, 121);
		again.client.checksum(60, 180);
		waitFor("told of the difference", () -> !rec.desyncs.isEmpty());
		assertEquals(40, rec.desyncs.get(0).tick);
		assertEquals(1, rec.desyncs.size());
		assertTrue(a.rec.desyncs.isEmpty(), "the others are not told of a rejoining player's difference");
		assertEquals(LanProtocol.PRESENCE_REJOINING, this.env.host.presenceOf(again.client.participantId()));
		assertTrue(again.client.isCatchingUp());
		again.client.caughtUp();
		waitFor("counted in",
				() -> this.env.host.presenceOf(again.client.participantId()) == LanProtocol.PRESENCE_PLAYING);
		assertTrue(rec.caughtUp > 0);
		final List<Integer> none = new ArrayList<Integer>();
		assertTrue(none.isEmpty());
	}
}
