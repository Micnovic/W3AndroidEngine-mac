package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.etheller.warsmash.lan.LanCommandSender;
import com.etheller.warsmash.lan.LanCommandValidator;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandCodec;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;

import net.warsmash.lan.protocol.LanProtocol;

/** A value synced in a LAN game goes through the sender, the host's check and the codec, and lands in the cache. */
class CGameCacheSyncTest {
	private final List<byte[]> sent = new ArrayList<>();
	private final LanCommandSender sender = new LanCommandSender(this.sent::add);
	private final LanCommandValidator validator = new LanCommandValidator();

	@BeforeEach
	void memoryDisk() {
		CGameCache.setDisk(new CGameCache.Disk() {
			@Override
			public byte[] read() {
				return null;
			}

			@Override
			public boolean write(final byte[] bytes) {
				return true;
			}
		});
		CGameCache.reloadAllFromDisk();
	}

	@AfterEach
	void profileDisk() {
		CGameCache.dropUnsaved();
		CGameCache.setDisk(null);
	}

	private void send(final List<String> chunks) {
		for (final String chunk : chunks) {
			this.sender.syncData(CGameCacheSync.PREFIX, chunk);
		}
	}

	/** What the executor of the sending player does with the commands of a turn, on a machine that lacks the value. */
	private void deliver(final CGameCacheSync.Receiver receiver) {
		for (final byte[] command : this.sent) {
			assertTrue(this.validator.accept(0, command));
			CPlayerCommandCodec.decode(command,
					(CPlayerUnitOrderListener) Proxy.newProxyInstance(getClass().getClassLoader(),
							new Class<?>[] { CPlayerUnitOrderListener.class }, (proxy, method, arguments) -> {
								if (method.getName().equals("syncData")) {
									assertTrue(CGameCacheSync.isCacheSync((String) arguments[0]));
									receiver.accept((String) arguments[1]);
								}
								return null;
							}));
		}
		this.sent.clear();
	}

	@Test
	void everyKindOfValueArrives() {
		final CGameCache cache = new CGameCache("Sync.w3v");
		cache.storeInteger("m", "i", -7);
		cache.storeReal("m", "r", 2.5f);
		cache.storeBoolean("m", "b", true);
		cache.storeString("m", "s", "hello ä");
		send(CGameCacheSync.integer(cache, "m", "i"));
		send(CGameCacheSync.real(cache, "m", "r"));
		send(CGameCacheSync.bool(cache, "m", "b"));
		send(CGameCacheSync.string(cache, "m", "s"));
		cache.flush();
		deliver(new CGameCacheSync.Receiver());
		final CGameCache other = new CGameCache("sync.w3v");
		assertEquals(-7, other.getInteger("m", "i"));
		assertEquals(2.5f, other.getReal("m", "r"));
		assertEquals(Boolean.TRUE, other.getBoolean("m", "b"));
		assertEquals("hello ä", other.getString("m", "s"));
	}

	@Test
	void aLongValueTravelsInSeveralCommandsInOrder() {
		final StringBuilder text = new StringBuilder();
		for (int i = 0; i < 1500; i++) {
			text.append((char) ('a' + (i % 26)));
		}
		final CGameCache cache = new CGameCache("long");
		cache.storeString("m", "k", text.toString());
		send(CGameCacheSync.string(cache, "m", "k"));
		assertTrue(this.sent.size() > 1);
		cache.storeString("m", "k", "changed");
		deliver(new CGameCacheSync.Receiver());
		assertEquals(text.toString(), cache.getString("m", "k"));
	}

	@Test
	void aValueThisMachineLacksSendsNothingAndKeepsTheCache() {
		final CGameCache cache = new CGameCache("none");
		assertTrue(CGameCacheSync.integer(cache, "m", "k").isEmpty());
		assertTrue(CGameCacheSync.unit(cache, "m", "k").isEmpty());
		assertNull(cache.getInteger("m", "k"));
	}

	@Test
	void aBrokenOrOutOfOrderChunkIsDropped() {
		final CGameCache cache = new CGameCache("broken");
		final CGameCacheSync.Receiver receiver = new CGameCacheSync.Receiver();
		receiver.accept("garbage");
		receiver.accept("1/2|AAAA");
		receiver.accept("0/1|!!!!");
		assertNull(cache.getInteger("a", "b"));
		cache.storeInteger("m", "k", 5);
		final List<String> chunks = CGameCacheSync.integer(cache, "m", "k");
		cache.storeInteger("m", "k", 6);
		for (final String chunk : chunks) {
			receiver.accept(chunk);
		}
		assertEquals(5, cache.getInteger("m", "k"));
	}

	@Test
	void theHostAcceptsTheChunksOfAPlayerButNotOfAReferee() {
		final CGameCache cache = new CGameCache("c");
		cache.storeString("m", "k", "x");
		send(CGameCacheSync.string(cache, "m", "k"));
		for (final byte[] command : this.sent) {
			assertTrue(this.validator.accept(0, command));
			assertFalse(this.validator.accept(LanProtocol.WATCHER_SLOT_BASE, command));
		}
	}

}
