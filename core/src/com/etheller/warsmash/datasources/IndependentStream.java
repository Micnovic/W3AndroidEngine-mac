package com.etheller.warsmash.datasources;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.etheller.warsmash.util.StreamBytes;

/**
 * Marks a stream that shares nothing with other streams of its data source that a read changes, so a helper thread
 * can read it while the game thread reads others. A stream of an archive read through one shared channel is not.
 */
public interface IndependentStream {
	/** A stream of the game's storage, which reads its file by position and decodes on its own. */
	final class Buffered extends BufferedInputStream implements IndependentStream {
		public Buffered(final InputStream in, final int size) {
			super(in, size);
		}
	}

	final class FromFile extends FileInputStream implements IndependentStream {
		public FromFile(final File file) throws FileNotFoundException {
			super(file);
		}
	}

	/** Opens a file that was looked up before, on whichever thread calls it, see {@link DataSource#opener}. */
	interface Opener {
		InputStream open() throws IOException;
	}

	/**
	 * A file that is opened when it is first read, so that a helper thread that reads it also does the opening: for a
	 * file of the game's storage that is not streamed, opening it reads and decodes all of it.
	 */
	final class Opening extends InputStream implements IndependentStream, StreamBytes.Passing {
		private final Opener opener;
		private InputStream stream;
		private boolean closed;

		public Opening(final Opener opener) {
			this.opener = opener;
		}

		private InputStream stream() throws IOException {
			if (this.closed) {
				throw new IOException("Stream closed");
			}
			if (this.stream == null) {
				this.stream = this.opener.open();
				if (this.stream == null) {
					throw new FileNotFoundException("The file is gone from its data source");
				}
			}
			return this.stream;
		}

		@Override
		public InputStream inner() throws IOException {
			return stream();
		}

		@Override
		public int read() throws IOException {
			return stream().read();
		}

		@Override
		public int read(final byte[] b, final int off, final int len) throws IOException {
			return stream().read(b, off, len);
		}

		@Override
		public long skip(final long n) throws IOException {
			return stream().skip(n);
		}

		@Override
		public int available() throws IOException {
			return stream().available();
		}

		@Override
		public void close() throws IOException {
			this.closed = true;
			final InputStream opened = this.stream;
			this.stream = null;
			if (opened != null) {
				opened.close();
			}
		}
	}
}
