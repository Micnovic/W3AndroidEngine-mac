package net.warsmash.lan.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import net.warsmash.lan.protocol.LanMessages;

/** What a connection sent last reaches the peer when the loop shuts down right behind it. */
class LanLoopDrainTest {
	private static byte[] readUntilEof(final Socket s) throws Exception {
		final InputStream in = s.getInputStream();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final byte[] buf = new byte[1024];
		int n;
		while ((n = in.read(buf)) >= 0) {
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}

	private static LanConnection connect(final LanLoop loop, final int port) throws Exception {
		final LanConnection[] box = new LanConnection[1];
		final CountDownLatch up = new CountDownLatch(1);
		loop.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 3000, new LanLoop.ConnectCallback() {
			@Override
			public void onConnected(final LanConnection connection) {
				box[0] = connection;
				up.countDown();
			}

			@Override
			public void onFailed(final java.io.IOException error) {
				up.countDown();
			}
		});
		assertTrue(up.await(3, TimeUnit.SECONDS));
		return box[0];
	}

	@Test
	void aMessageSentRightBeforeShutdownArrivesWhileThePeerHasUnreadInput() throws Exception {
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			final LanLoop loop = new LanLoop();
			loop.start();
			final LanConnection c = connect(loop, server.getLocalPort());
			try (Socket peer = server.accept()) {
				peer.setSoTimeout(5000);
				final byte[] pong = LanConnection.encode(new LanMessages.Pong(0));
				for (int i = 0; i < 200; i++) {
					peer.getOutputStream().write(pong);
				}
				peer.getOutputStream().flush();
				final byte[] frame = LanConnection.encode(new LanMessages.Leave(1));
				c.sendFrame(frame);
				loop.shutdown(LanLoop.RESPONSIVE_WAIT_MS);
				final byte[] got = readUntilEof(peer);
				assertTrue(got.length >= frame.length);
				final byte[] last = new byte[frame.length];
				System.arraycopy(got, got.length - frame.length, last, 0, frame.length);
				assertEquals(java.util.Arrays.toString(frame), java.util.Arrays.toString(last));
			}
		}
	}

	@Test
	void aPeerThatNeverClosesDelaysTheEndOnlyBriefly() throws Exception {
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			final LanLoop loop = new LanLoop();
			loop.start();
			connect(loop, server.getLocalPort());
			try (Socket peer = server.accept()) {
				final long start = System.nanoTime();
				loop.shutdown(5000);
				final long ms = (System.nanoTime() - start) / 1_000_000;
				assertTrue(ms < 2500, "took " + ms);
			}
		}
	}
}
