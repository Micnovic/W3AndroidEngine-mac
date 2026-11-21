package net.warsmash.lan.transport;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/** Plain-Java implementation (desktop and tests); the locks are no-ops. */
public class DefaultLanNetwork implements LanNetwork {
	/** Whether the interface may carry LAN play; platforms leave out the ones that cannot (mobile data, virtual adapters). */
	protected boolean acceptInterface(final NetworkInterface ni) {
		return true;
	}

	/**
	 * Listing the adapters costs a few hundred milliseconds on a PC with many virtual ones, and every lobby change asks
	 * for the broadcast addresses on the network thread, so the answer is kept: fresh for {@link #FRESH_MS}, then the
	 * broadcast addresses are handed out stale while another thread renews them (what a player asks about their own
	 * addresses is always current).
	 */
	private static final long FRESH_MS = 2000;
	private static final long STALE_OK_MS = 30000;

	private static final class Snapshot {
		final List<InetAddress> local = new ArrayList<InetAddress>();
		final List<InetAddress> broadcast = new ArrayList<InetAddress>();
		final long taken = System.nanoTime();
	}

	private Snapshot snapshot;
	private boolean renewing;

	private Snapshot snapshot(final boolean staleOk) {
		final Snapshot current;
		synchronized (this) {
			current = this.snapshot;
		}
		final long ageMs = current == null ? Long.MAX_VALUE : (System.nanoTime() - current.taken) / 1000000;
		if (ageMs < FRESH_MS) {
			return current;
		}
		if (staleOk && ageMs < STALE_OK_MS) {
			renewInBackground();
			return current;
		}
		return renew();
	}

	private Snapshot renew() {
		final Snapshot s = take();
		synchronized (this) {
			this.snapshot = s;
		}
		return s;
	}

	private void renewInBackground() {
		synchronized (this) {
			if (this.renewing) {
				return;
			}
			this.renewing = true;
		}
		final Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					renew();
				}
				finally {
					synchronized (DefaultLanNetwork.this) {
						DefaultLanNetwork.this.renewing = false;
					}
				}
			}
		}, "lan-interfaces");
		t.setDaemon(true);
		t.start();
	}

	private Snapshot take() {
		final Snapshot result = new Snapshot();
		try {
			result.broadcast.add(InetAddress.getByAddress(new byte[] { -1, -1, -1, -1 }));
			final Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
			if (ifaces != null) {
				for (final NetworkInterface ni : Collections.list(ifaces)) {
					if (!ni.isUp() || ni.isLoopback() || !acceptInterface(ni)) {
						continue;
					}
					for (final InetAddress a : Collections.list(ni.getInetAddresses())) {
						if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
							result.local.add(a);
						}
					}
					for (final InterfaceAddress ia : ni.getInterfaceAddresses()) {
						// Android reports no broadcast address for some interfaces; the prefix gives it.
						final InetAddress b = ia.getBroadcast() != null ? ia.getBroadcast()
								: broadcastOf(ia.getAddress(), ia.getNetworkPrefixLength());
						if (b != null && !result.broadcast.contains(b)) {
							result.broadcast.add(b);
						}
					}
				}
			}
		}
		catch (final java.io.IOException e) {
			LanLog.warn("cannot enumerate interfaces", e);
		}
		return result;
	}

	@Override
	public boolean isNetworkAvailable() {
		return !localAddresses().isEmpty();
	}

	@Override
	public List<InetAddress> localAddresses() {
		return new ArrayList<InetAddress>(snapshot(false).local);
	}

	@Override
	public List<InetAddress> broadcastAddresses() {
		return new ArrayList<InetAddress>(snapshot(true).broadcast);
	}

	/** The broadcast address of the subnet the address and prefix length make, or null for a non-IPv4 address. */
	public static InetAddress broadcastOf(final InetAddress address, final int prefixLength) {
		if (!(address instanceof Inet4Address) || prefixLength < 1 || prefixLength > 31) {
			return null;
		}
		final byte[] bytes = address.getAddress();
		final int mask = -1 << (32 - prefixLength);
		final int value = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8)
				| (bytes[3] & 0xFF);
		final int broadcast = value | ~mask;
		try {
			return InetAddress.getByAddress(new byte[] { (byte) (broadcast >>> 24), (byte) (broadcast >>> 16),
					(byte) (broadcast >>> 8), (byte) broadcast });
		}
		catch (final java.net.UnknownHostException e) {
			return null;
		}
	}

	@Override
	public void acquireDiscoveryLock() {
	}

	@Override
	public void releaseDiscoveryLock() {
	}

	@Override
	public void acquireSessionLock() {
	}

	@Override
	public void releaseSessionLock() {
	}
}
