package com.etheller.warsmash.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * Read-only view of a file that keeps no file contents in the heap. The position is kept per thread and every read is
 * positional, so threads that share the channel (the MPQ readers do a position then a read) cannot disturb each other.
 */
public final class FileReadChannel implements SeekableByteChannel {
	private final RandomAccessFile file;
	private final FileChannel channel;
	private final ThreadLocal<long[]> position = ThreadLocal.withInitial(() -> new long[1]);

	public FileReadChannel(final File path) throws IOException {
		this.file = new RandomAccessFile(path, "r");
		this.channel = this.file.getChannel();
	}

	@Override
	public int read(final ByteBuffer dst) throws IOException {
		final long[] pos = this.position.get();
		final int read = this.channel.read(dst, pos[0]);
		if (read > 0) {
			pos[0] += read;
		}
		return read;
	}

	@Override
	public long position() {
		return this.position.get()[0];
	}

	@Override
	public SeekableByteChannel position(final long newPosition) {
		if (newPosition < 0) {
			throw new IllegalArgumentException("negative position");
		}
		this.position.get()[0] = newPosition;
		return this;
	}

	@Override
	public long size() throws IOException {
		return this.channel.size();
	}

	@Override
	public int write(final ByteBuffer src) {
		throw new NonWritableChannelException();
	}

	@Override
	public SeekableByteChannel truncate(final long size) {
		throw new NonWritableChannelException();
	}

	@Override
	public boolean isOpen() {
		return this.channel.isOpen();
	}

	@Override
	public void close() throws IOException {
		this.file.close();
	}
}
