package net.warsmash.lan;

import static net.warsmash.lan.LanTestSupport.waitFor;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.warsmash.lan.LanTestSupport.Node;
import net.warsmash.lan.LanTestSupport.Recorder;
import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.client.MapDownload;
import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.maps.LanMapIndex;
import net.warsmash.lan.maps.MapHash;
import net.warsmash.lan.maps.MapTestFiles;
import net.warsmash.lan.protocol.LanCodec;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanData.TurnData;
import net.warsmash.lan.protocol.LanMessage;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.transport.LanConnection;
import net.warsmash.lan.transport.LanFaultInjector;
import net.warsmash.lan.transport.LanLoop;

/** Map transfer between a host and clients over loopback. */
class MapTransferTest {
	@TempDir
	Path tmp;

	private final LanTestSupport env = new LanTestSupport();
	private final List<Socket> sockets = new CopyOnWriteArrayList<Socket>();

	@AfterEach
	void tearDown() throws Exception {
		for (final Socket s : this.sockets) {
			s.close();
		}
		this.env.close();
		waitFor("network threads to end", 3000, () -> LanTestSupport.liveLanThreads().isEmpty());
	}

	/** Records map events with the time they arrived. */
	static final class MapRecorder extends Recorder {
		final List<Integer> states = new CopyOnWriteArrayList<Integer>();
		final List<Long> progress = new CopyOnWriteArrayList<Long>();
		final List<Long> turnTimes = new CopyOnWriteArrayList<Long>();
		volatile int state = -1;
		volatile int failure;
		volatile File file;
		volatile int total;

		@Override
		public void onMapState(final int s, final LanData.MapInfo map, final File f, final int fail) {
			this.states.add(s);
			this.state = s;
			this.failure = fail;
			this.file = f;
		}

		@Override
		public void onMapProgress(final long done, final int t) {
			this.progress.add(done);
			this.total = t;
		}

		@Override
		public void onTurn(final TurnData t) {
			super.onTurn(t);
			this.turnTimes.add(System.nanoTime());
		}
	}

	/** Records what the host reports about transfers. */
	static final class HostEvents implements LanHost.MapListener {
		final AtomicInteger sendingEvents = new AtomicInteger();
		final List<String> statuses = new CopyOnWriteArrayList<String>();
		volatile long maxSent;

		@Override
		public void onMapSending(final int participantId, final long sent, final int total) {
			this.sendingEvents.incrementAndGet();
			this.maxSent = Math.max(this.maxSent, sent);
		}

		@Override
		public void onMapStatus(final int participantId, final int status, final int percent) {
			this.statuses.add(participantId + ":" + status);
		}
	}

	private File dir(final String name) {
		final File d = this.tmp.resolve(name).toFile();
		d.mkdirs();
		return d;
	}

	private LanHost.Config hostConfig(final File map) throws IOException {
		final LanHost.Config c = LanTestSupport.hostConfig();
		c.map = new LanMapIndex(null, null).describe(map);
		c.mapFile = map;
		return c;
	}

	private LanClient.Config clientConfig(final String name, final File download, final File... roots) {
		final LanClient.Config c = LanTestSupport.clientConfig(name);
		final LanMapIndex index = new LanMapIndex(null, download);
		for (final File r : roots) {
			index.addRoot(r);
		}
		c.mapIndex = index;
		return c;
	}

	private Node join(final String name, final File download, final MapRecorder rec, final File... roots)
			throws IOException {
		return this.env.join(clientConfig(name, download, roots), rec);
	}

	private static File[] filesIn(final File dir) {
		final File[] f = dir.listFiles();
		return f == null ? new File[0] : f;
	}

	private static void assertSameContent(final File expected, final File actual) throws IOException {
		assertEquals(expected.length(), actual.length());
		assertArrayEquals(MapHash.compute(expected).sha256, MapHash.compute(actual).sha256);
	}

	private static void assertOnlyThe(final File dir, final String name) {
		final File[] f = filesIn(dir);
		assertEquals(1, f.length, "download folder holds " + Arrays.toString(f));
		assertEquals(name, f[0].getName());
	}

	// ------------------------------------------------------------------ happy path

	@Test
	void threeMegabytesReachTwoClientsWhoThenStartTheGame() throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Three.w3x"), 3 * 1024 * 1024, 21);
		final HostEvents hostEvents = new HostEvents();
		this.env.startHost(hostConfig(map));
		this.env.host.setMapListener(hostEvents);
		final File dlA = dir("dlA");
		final File dlB = dir("dlB");
		final MapRecorder ra = new MapRecorder();
		final MapRecorder rb = new MapRecorder();
		final Node a = join("Thrall", dlA, ra);
		final Node b = join("Jaina", dlB, rb);
		waitFor("both have the map", 20000, () -> ra.state == LanClient.MAP_READY && rb.state == LanClient.MAP_READY);

		assertSameContent(map, ra.file);
		assertSameContent(map, rb.file);
		assertOnlyThe(dlA, "Three.w3x");
		assertOnlyThe(dlB, "Three.w3x");
		assertEquals(Arrays.asList(LanClient.MAP_CHECKING, LanClient.MAP_DOWNLOADING, LanClient.MAP_VERIFYING,
				LanClient.MAP_READY), ra.states);

		assertEquals(0L, (long) ra.progress.get(0), "a fresh download starts at zero");
		long last = -1;
		for (final long p : ra.progress) {
			assertTrue(p >= last, "progress never goes back");
			last = p;
		}
		assertEquals(3 * 1024 * 1024, last);
		assertEquals(3 * 1024 * 1024, ra.total);
		assertTrue(hostEvents.sendingEvents.get() >= 2);
		assertEquals(3 * 1024 * 1024, hostEvents.maxSent);
		final int idA = a.rec.welcome.participantId;
		final int idB = b.rec.welcome.participantId;
		waitFor("host heard HAVE from both", () -> this.env.host.mapStatusOf(idA) == LanProtocol.MAP_STATUS_HAVE
				&& this.env.host.mapStatusOf(idB) == LanProtocol.MAP_STATUS_HAVE);
		assertTrue(hostEvents.statuses.contains(idA + ":" + LanProtocol.MAP_STATUS_HAVE));

		this.env.startGame(a, b);
		assertEquals(LanProtocol.STATE_IN_GAME, this.env.host.state());
	}

	@Test
	void aMapTheClientAlreadyHasUnderAnotherNameIsNotSent() throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Original.w3x"), 300000, 22);
		final File mine = dir("mine");
		Files.copy(map.toPath(), new File(mine, "My Copy.w3x").toPath());
		final HostEvents hostEvents = new HostEvents();
		this.env.startHost(hostConfig(map));
		this.env.host.setMapListener(hostEvents);
		final File dl = dir("dl");
		final MapRecorder rec = new MapRecorder();
		join("Thrall", dl, rec, mine);
		waitFor("map found", () -> rec.state == LanClient.MAP_READY);
		assertEquals("My Copy.w3x", rec.file.getName());
		assertEquals(Arrays.asList(LanClient.MAP_CHECKING, LanClient.MAP_READY), rec.states);
		assertEquals(0, filesIn(dl).length);
		assertTrue(rec.progress.isEmpty());
		waitFor("HAVE", () -> this.env.host.mapStatusOf(1) == LanProtocol.MAP_STATUS_HAVE);
		assertEquals(0, hostEvents.sendingEvents.get());
	}

	@Test
	void aClientWithoutAMapIndexIsTreatedAsHavingTheMap() throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Legacy.w3x"), 100000, 23);
		this.env.startHost(hostConfig(map));
		final Node a = this.env.joinAndWait("Thrall");
		waitFor("HAVE", () -> this.env.host.mapStatusOf(1) == LanProtocol.MAP_STATUS_HAVE);
		this.env.startGame(a);
	}

	@Test
	void aSecondDownloadIsFoundWithoutAnotherTransfer() throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Again.w3x"), 500000, 24);
		final HostEvents hostEvents = new HostEvents();
		this.env.startHost(hostConfig(map));
		this.env.host.setMapListener(hostEvents);
		final File dl = dir("dl");
		final File cache = this.tmp.resolve("hashes.txt").toFile();
		final LanClient.Config cfg = LanTestSupport.clientConfig("Thrall");
		cfg.mapIndex = new LanMapIndex(cache, dl);
		final MapRecorder first = new MapRecorder();
		final Node a = this.env.join(cfg, first);
		waitFor("first download", 10000, () -> first.state == LanClient.MAP_READY);
		a.client.leave(LanProtocol.LEAVE_NORMAL);
		waitFor("left", () -> this.env.host.participantCount() == 0);
		final int sent = hostEvents.sendingEvents.get();

		final LanClient.Config cfg2 = LanTestSupport.clientConfig("Thrall");
		cfg2.mapIndex = new LanMapIndex(cache, dl);
		final MapRecorder second = new MapRecorder();
		this.env.join(cfg2, second);
		waitFor("second join", () -> second.state == LanClient.MAP_READY);
		assertEquals(Arrays.asList(LanClient.MAP_CHECKING, LanClient.MAP_READY), second.states);
		assertEquals(sent, hostEvents.sendingEvents.get());
		assertEquals(0, cfg2.mapIndex.hashesComputed(), "hashes came from the cache file");
	}

	// ---------------------------------------------------------------- the ready rule

	private LanConnection rawConnect(final List<LanMessage> inbox, final List<String> closed) throws Exception {
		final LanLoop loop = this.env.newLoop("raw");
		final LanConnection[] holder = new LanConnection[1];
		final boolean[] failed = new boolean[1];
		loop.connect(this.env.address(), 2000, new LanLoop.ConnectCallback() {
			@Override
			public void onConnected(final LanConnection c) {
				c.setListener(new LanConnection.Listener() {
					@Override
					public void onMessage(final LanConnection connection, final LanMessage message) {
						inbox.add(message);
					}

					@Override
					public void onClosed(final LanConnection connection, final String reason) {
						closed.add(reason);
					}
				});
				final LanMessages.Hello h = new LanMessages.Hello();
				h.engineVersion = LanTestSupport.VERSION;
				h.buildCommit = "abc";
				h.dataChecksum = LanTestSupport.DATA;
				h.playerName = "Raw";
				c.send(h);
				holder[0] = c;
			}

			@Override
			public void onFailed(final IOException error) {
				failed[0] = true;
			}
		});
		waitFor("raw connection", () -> holder[0] != null || failed[0]);
		assertNotNull(holder[0]);
		waitFor("raw welcome", () -> {
			for (final LanMessage m : inbox) {
				if (m instanceof LanMessages.Welcome) {
					return true;
				}
			}
			return false;
		});
		return holder[0];
	}

	private static LanMessages.LobbyState lastLobby(final List<LanMessage> inbox) {
		LanMessages.LobbyState last = null;
		for (final LanMessage m : inbox) {
			if (m instanceof LanMessages.LobbyState) {
				last = (LanMessages.LobbyState) m;
			}
		}
		return last;
	}

	@Test
	void hostDoesNotAcceptReadyUntilTheMapIsReported() throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Ready.w3x"), 100000, 25);
		this.env.startHost(hostConfig(map));
		final List<LanMessage> inbox = new CopyOnWriteArrayList<LanMessage>();
		final List<String> closed = new CopyOnWriteArrayList<String>();
		final LanConnection c = rawConnect(inbox, closed);

		inbox.clear();
		c.send(new LanMessages.Ready(true));
		waitFor("lobby back", () -> lastLobby(inbox) != null);
		assertFalse(lastLobby(inbox).slots.get(0).ready, "ready without the map is refused");

		c.send(new LanMessages.MapStatus(LanProtocol.MAP_STATUS_DOWNLOADING, 40));
		c.send(new LanMessages.Ready(true));
		Thread.sleep(150);
		assertFalse(lastLobby(inbox).slots.get(0).ready);

		c.send(new LanMessages.MapStatus(LanProtocol.MAP_STATUS_HAVE, 100));
		c.send(new LanMessages.Ready(true));
		waitFor("ready accepted", () -> lastLobby(inbox).slots.get(0).ready);

		c.send(new LanMessages.MapStatus(LanProtocol.MAP_STATUS_DOWNLOADING, 0));
		waitFor("ready dropped when the map is not there any more", () -> !lastLobby(inbox).slots.get(0).ready);
		assertTrue(closed.isEmpty());
	}

	@Test
	void changingTheMapMakesEveryoneFetchItAgain() throws Exception {
		final File one = MapTestFiles.writeMap(new File(dir("host"), "One.w3x"), 400000, 26);
		final File two = MapTestFiles.writeMap(new File(dir("host"), "Two.w3x"), 500000, 27);
		this.env.startHost(hostConfig(one));
		final File dl = dir("dl");
		final MapRecorder rec = new MapRecorder();
		final Node a = join("Thrall", dl, rec);
		waitFor("first map", 10000, () -> rec.state == LanClient.MAP_READY);
		a.client.setReady(true);
		waitFor("ready", () -> a.rec.lobby.slots.get(0).ready);

		rec.states.clear();
		this.env.host.setMap(new LanMapIndex(null, null).describe(two), two);
		waitFor("not ready any more", () -> !a.rec.lobby.slots.get(0).ready);
		waitFor("second map", 10000, () -> rec.states.contains(LanClient.MAP_READY));
		assertEquals("Two.w3x", rec.file.getName());
		assertSameContent(two, rec.file);
		a.client.setReady(true);
		waitFor("ready again", () -> a.rec.lobby.slots.get(0).ready);
	}

	@Test
	void requestOutsideTheFileClosesTheConnection() throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Range.w3x"), 100000, 28);
		this.env.startHost(hostConfig(map));
		final List<LanMessage> inbox = new CopyOnWriteArrayList<LanMessage>();
		final List<String> closed = new CopyOnWriteArrayList<String>();
		final LanConnection c = rawConnect(inbox, closed);
		c.send(new LanMessages.MapRequest(100001));
		waitFor("closed", () -> !closed.isEmpty());
		waitFor("participant gone", () -> this.env.host.participantCount() == 0);
	}

	@Test
	void requestAtTheEndIsRefusedWithoutAChunk() throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "End.w3x"), 100000, 29);
		this.env.startHost(hostConfig(map));
		final List<LanMessage> inbox = new CopyOnWriteArrayList<LanMessage>();
		final List<String> closed = new CopyOnWriteArrayList<String>();
		final LanConnection c = rawConnect(inbox, closed);
		c.send(new LanMessages.MapRequest(100000));
		waitFor("closed", () -> !closed.isEmpty());
		for (final LanMessage m : inbox) {
			assertFalse(m instanceof LanMessages.MapChunk);
		}
	}

	@Test
	void hostWithoutTheFileRefusesInsteadOfHanging() throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Gone.w3x"), 100000, 30);
		final LanHost.Config cfg = hostConfig(map);
		cfg.mapFile = null;
		this.env.startHost(cfg);
		final File dl = dir("dl");
		final MapRecorder rec = new MapRecorder();
		join("Thrall", dl, rec);
		waitFor("disconnect", () -> !rec.disconnects.isEmpty());
		assertEquals(0, filesIn(dl).length - countParts(dl), "no real file");
	}

	private static int countParts(final File dir) {
		int n = 0;
		for (final File f : filesIn(dir)) {
			if (f.getName().endsWith(".part")) {
				n++;
			}
		}
		return n;
	}

	// ----------------------------------------------------------------- bad transfers

	@Test
	void corruptedBytesDeleteThePartAndEndTheJoin() throws Exception {
		final File served = MapTestFiles.writeMap(new File(dir("host"), "Real.w3x"), 600000, 31);
		final File bad = MapTestFiles.writeCorruptedCopy(served, new File(dir("evil"), "Real.w3x"));
		final LanHost.Config cfg = hostConfig(served);
		cfg.mapFile = bad;
		this.env.startHost(cfg);
		final File dl = dir("dl");
		final MapRecorder rec = new MapRecorder();
		join("Thrall", dl, rec);
		waitFor("failed", 10000, () -> rec.state == LanClient.MAP_FAILED);
		assertEquals(MapDownload.FAIL_HASH, rec.failure);
		waitFor("disconnected", () -> !rec.disconnects.isEmpty());
		assertEquals(LanClient.MAP_FAILED_TEXT, rec.disconnects.get(0));
		assertEquals(0, filesIn(dl).length, "neither a part nor a map is left");
		waitFor("removed from the lobby", () -> this.env.host.participantCount() == 0);
	}

	@Test
	void aFileThatIsNotAnArchiveIsRefusedEvenWithTheRightHash() throws Exception {
		final File fake = new File(dir("host"), "Fake.w3x");
		final byte[] junk = new byte[200000];
		Arrays.fill(junk, (byte) 7);
		Files.write(fake.toPath(), junk);
		final LanHost.Config cfg = LanTestSupport.hostConfig();
		final MapHash.Result h = MapHash.compute(fake);
		cfg.map = new LanData.MapInfo();
		cfg.map.name = "Fake.w3x";
		cfg.map.size = (int) h.size;
		cfg.map.sha256 = h.sha256;
		cfg.map.crc32 = h.crc32;
		cfg.mapFile = fake;
		this.env.startHost(cfg);
		final File dl = dir("dl");
		final MapRecorder rec = new MapRecorder();
		join("Thrall", dl, rec);
		waitFor("failed", 10000, () -> rec.state == LanClient.MAP_FAILED);
		assertEquals(MapDownload.FAIL_BAD_HEADER, rec.failure);
		assertEquals(0, filesIn(dl).length);
	}

	@Test
	void unsafeAnnouncedNamesNeverTouchTheDisk() throws Exception {
		final String[] names = { "..\\Evil.w3x", "../Evil.w3x", "sub/Evil.w3x", "C:\\Evil.w3x", "Evil.exe",
				"Evil.w3x.bat", "con.w3x", "...w3x" };
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Fine.w3x"), 100000, 32);
		final MapHash.Result h = MapHash.compute(map);
		for (final String name : names) {
			try (LanTestSupport own = new LanTestSupport()) {
				final LanHost.Config cfg = LanTestSupport.hostConfig();
				cfg.map = new LanData.MapInfo();
				cfg.map.name = name;
				cfg.map.size = (int) h.size;
				cfg.map.sha256 = h.sha256;
				cfg.map.crc32 = h.crc32;
				cfg.mapFile = map;
				own.startHost(cfg);
				final File dl = dir("dl-" + Math.abs(name.hashCode()));
				final MapRecorder rec = new MapRecorder();
				own.join(clientConfig("Thrall", dl), rec);
				waitFor("refused " + name, 5000, () -> rec.state == LanClient.MAP_FAILED);
				assertEquals(MapDownload.FAIL_BAD_INFO, rec.failure, name);
				assertEquals(0, filesIn(dl).length, name);
				assertFalse(new File(this.tmp.toFile(), "Evil.w3x").exists());
				assertFalse(new File(dl.getParentFile(), "Evil.w3x").exists());
			}
		}
		waitFor("network threads to end", 3000, () -> LanTestSupport.liveLanThreads().isEmpty());
	}

	@Test
	void announcedSizeDifferentFromTheFileEndsTheRequest() throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Size.w3x"), 100000, 33);
		final File longer = MapTestFiles.writeMap(new File(dir("evil"), "Size.w3x"), 100010, 33);
		final LanHost.Config cfg = hostConfig(map);
		cfg.mapFile = longer;
		this.env.startHost(cfg);
		final File dl = dir("dl");
		final MapRecorder rec = new MapRecorder();
		join("Thrall", dl, rec);
		waitFor("disconnect", () -> !rec.disconnects.isEmpty());
		assertEquals(0, filesIn(dl).length - countParts(dl));
		assertNotEquals(LanClient.MAP_READY, rec.state);
	}

	private static void assertNotEquals(final int unexpected, final int actual) {
		assertFalse(unexpected == actual, "state must not be " + unexpected);
	}

	// ----------------------------------------------------------- interruption and faults

	@Test
	void interruptedTransferResumesFromThePartAndFinishes() throws Exception {
		final long size = 4 * 1024 * 1024;
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Resume.w3x"), size, 34);
		this.env.startHost(hostConfig(map));
		// frames wait in the delay line, which the sender counts as queued, so the transfer runs at a few MB/s
		final LanFaultInjector injector = new LanFaultInjector(1);
		injector.setDelay(100, 0);
		this.env.hostLoop.setFaultInjector(injector);
		final File dl = dir("dl");
		final MapRecorder first = new MapRecorder();
		join("Thrall", dl, first);
		waitFor("under way", 10000, () -> first.progress.size() > 1);
		injector.cut();
		waitFor("disconnected", () -> !first.disconnects.isEmpty());
		injector.setDelay(0, 0);
		assertEquals(1, countParts(dl));
		final long left = filesIn(dl)[0].length();
		assertTrue(left > 0 && left < size, "part holds " + left);

		final MapRecorder second = new MapRecorder();
		join("Thrall", dl, second);
		waitFor("second ready", 20000, () -> second.state == LanClient.MAP_READY);
		assertEquals(left, (long) second.progress.get(0), "the second attempt starts where the first stopped");
		assertSameContent(map, second.file);
		assertOnlyThe(dl, "Resume.w3x");
	}

	@Test
	void silentHostFailsTheDownloadButKeepsThePart() throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Quiet.w3x"), 8 * 1024 * 1024, 35);
		this.env.startHost(hostConfig(map));
		final LanFaultInjector injector = new LanFaultInjector(2);
		injector.setDelay(100, 0);
		this.env.hostLoop.setFaultInjector(injector);
		final File dl = dir("dl");
		final LanClient.Config cfg = clientConfig("Thrall", dl);
		cfg.mapStallMs = 400;
		final MapRecorder rec = new MapRecorder();
		this.env.join(cfg, rec);
		waitFor("under way", 10000, () -> rec.progress.size() > 1);
		injector.freeze(1200);
		waitFor("stalled", 5000, () -> rec.state == LanClient.MAP_FAILED);
		assertEquals(MapDownload.FAIL_TIMEOUT, rec.failure);
		assertEquals(1, countParts(dl));
		assertTrue(filesIn(dl)[0].length() > 0);
		injector.thaw();
	}

	@Test
	void transferSurvivesDelayAndJitter() throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Slow.w3x"), 2 * 1024 * 1024, 36);
		this.env.startHost(hostConfig(map));
		final LanFaultInjector injector = new LanFaultInjector(3);
		injector.setDelay(25, 20);
		this.env.hostLoop.setFaultInjector(injector);
		final File dl = dir("dl");
		final MapRecorder rec = new MapRecorder();
		join("Thrall", dl, rec);
		waitFor("download through the delay", 30000, () -> rec.state == LanClient.MAP_READY);
		assertSameContent(map, rec.file);
		assertOnlyThe(dl, "Slow.w3x");
	}

	@Test
	void slowReceiverDoesNotMakeTheHostQueueUpTheWholeFile() throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Pressure.w3x"), 48 * 1024 * 1024, 37);
		final HostEvents hostEvents = new HostEvents();
		this.env.startHost(hostConfig(map));
		this.env.host.setMapListener(hostEvents);
		final Socket s = new Socket("127.0.0.1", this.env.port);
		this.sockets.add(s);
		s.setReceiveBufferSize(64 * 1024);
		final DataOutputStream out = new DataOutputStream(s.getOutputStream());
		final LanMessages.Hello h = new LanMessages.Hello();
		h.engineVersion = LanTestSupport.VERSION;
		h.buildCommit = "abc";
		h.dataChecksum = LanTestSupport.DATA;
		h.playerName = "Slow";
		out.write(LanCodec.encodeFrame(h));
		out.write(LanCodec.encodeFrame(new LanMessages.MapRequest(0)));
		out.flush();
		// the socket is not read for a while; the host must stop at its high-water mark, not buffer 48 MB
		long stalled = -1;
		for (int i = 0; i < 20; i++) {
			Thread.sleep(100);
			out.write(LanCodec.encodeFrame(new LanMessages.MapStatus(LanProtocol.MAP_STATUS_DOWNLOADING, 1)));
			out.flush();
			final long now = hostEvents.maxSent;
			if (now == stalled && now > 0) {
				break;
			}
			stalled = now;
		}
		Thread.sleep(300);
		out.write(LanCodec.encodeFrame(new LanMessages.MapStatus(LanProtocol.MAP_STATUS_DOWNLOADING, 1)));
		out.flush();
		assertTrue(hostEvents.maxSent < 24L * 1024 * 1024, "host handed over " + hostEvents.maxSent + " bytes unread");
		assertEquals(1, this.env.host.participantCount(), "the host did not drop it for queue overflow");

		// now read everything: it completes in order
		final InputStream in = s.getInputStream();
		final LanCodec.FrameDecoder decoder = new LanCodec.FrameDecoder();
		final long[] next = new long[1];
		final byte[] buf = new byte[64 * 1024];
		final long deadline = System.nanoTime() + LanTestSupport.scaled(60_000L) * 1000000L;
		while (next[0] < 48L * 1024 * 1024 && System.nanoTime() < deadline) {
			s.setSoTimeout((int) LanTestSupport.scaled(2000));
			final int n = in.read(buf);
			if (n < 0) {
				break;
			}
			// keep the link alive for the host's silence check
			out.write(LanCodec.encodeFrame(new LanMessages.MapStatus(LanProtocol.MAP_STATUS_DOWNLOADING, 1)));
			decoder.feed(buf, 0, n, m -> {
				if (m instanceof LanMessages.MapChunk) {
					final LanMessages.MapChunk c = (LanMessages.MapChunk) m;
					assertEquals(next[0], c.offset);
					next[0] += c.data.length;
				}
			});
		}
		assertEquals(48L * 1024 * 1024, next[0]);
	}

	// ------------------------------------------------------- turn traffic and big files

	/** Longest time between two consecutive turns seen by a client, in ms, from the turn at {@code from} on. */
	private static long longestTurnGapMs(final MapRecorder rec, final int from) {
		long worst = 0;
		for (int i = Math.max(1, from); i < rec.turnTimes.size(); i++) {
			worst = Math.max(worst, (rec.turnTimes.get(i) - rec.turnTimes.get(i - 1)) / 1000000L);
		}
		return worst;
	}

	/**
	 * One player starts a game while two observers are still receiving the map. A delay on the host's link (frames
	 * waiting in it count as queued) holds the transfer to a few tens of MB/s so it surely overlaps the game.
	 */
	private void runWithObserversDownloading(final long mapSize, final long seed, final int delayMs, final long maxGapMs)
			throws Exception {
		final File map = MapTestFiles.writeMap(new File(dir("host"), "Big.w3x"), mapSize, seed);
		final HostEvents hostEvents = new HostEvents();
		this.env.startHost(hostConfig(map));
		this.env.host.setMapListener(hostEvents);
		final LanFaultInjector injector = new LanFaultInjector(seed);
		injector.setDelay(delayMs, 0);
		this.env.hostLoop.setFaultInjector(injector);
		final File host = map.getParentFile();

		final MapRecorder rp = new MapRecorder();
		final Node player = join("Thrall", dir("dlP"), rp, host);
		waitFor("player has the map", 20000, () -> rp.state == LanClient.MAP_READY);

		final LanClient.Config c1 = clientConfig("Obs1", dir("dl1"));
		c1.wantedRole = LanProtocol.ROLE_OBSERVER;
		final LanClient.Config c2 = clientConfig("Obs2", dir("dl2"));
		c2.wantedRole = LanProtocol.ROLE_OBSERVER;
		final MapRecorder r1 = new MapRecorder();
		final MapRecorder r2 = new MapRecorder();
		this.env.join(c1, r1);
		this.env.join(c2, r2);
		waitFor("observers under way", 20000, () -> r1.progress.size() > 1 && r2.progress.size() > 1);

		// the game starts and runs while both observers are still receiving the map
		player.client.setReady(true);
		waitFor("ready", () -> player.rec.lobby != null && player.rec.lobby.slots.get(0).ready);
		player.client.requestStart();
		waitFor("go", 20000, () -> player.rec.go);
		assertTrue(r1.state != LanClient.MAP_READY && r2.state != LanClient.MAP_READY,
				"the observers must still be receiving when the game starts");
		final int firstTurn = rp.turnTimes.size();
		final long startedAt = System.nanoTime();

		waitFor("both observers have the map", 120000,
				() -> r1.state == LanClient.MAP_READY && r2.state == LanClient.MAP_READY);
		final long elapsedMs = (System.nanoTime() - startedAt) / 1000000L;
		assertSameContent(map, r1.file);
		assertSameContent(map, r2.file);
		assertEquals(0, player.rec.lagEvents, "no lag was reported during the transfer");
		final long gap = longestTurnGapMs(rp, firstTurn);
		System.out.println("map transfer " + (mapSize >> 20) + " MB to 2 observers: game ran " + elapsedMs
				+ " ms with " + (rp.turnTimes.size() - firstTurn) + " turns, longest turn gap " + gap + " ms");
		assertTrue(gap <= LanTestSupport.scaled(maxGapMs) / 2, "turns stalled for " + gap + " ms during the transfer");
		assertTrue(rp.turnTimes.size() - firstTurn >= 3, "turns kept coming");
		assertOnlyThe(dir("dl1"), "Big.w3x");
		assertOnlyThe(dir("dl2"), "Big.w3x");
	}

	@Test
	void turnsKeepFlowingWhileObserversReceiveAMap() throws Exception {
		runWithObserversDownloading(24L * 1024 * 1024, 38, 20, 250);
	}

	@Tag("slow")
	@Test
	void hundredMegabytesToTwoClientsWhileTheGameRuns() throws Exception {
		runWithObserversDownloading(100L * 1024 * 1024, 39, 10, 250);
	}
}
