package net.warsmash.nio.channels;

import java.net.SocketAddress;

public interface WritableSocketOutput extends WritableOutput {
    SocketAddress getLocalAddress();
    SocketAddress getRemoteAddress();
}
