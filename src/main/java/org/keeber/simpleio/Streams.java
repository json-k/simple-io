package org.keeber.simpleio;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <p>
 * Utility methods relating to streams.
 * 
 * 
 * @author Jason
 *
 */
public class Streams {

	public static byte[] asByteArray(InputStream is) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		copy(is, bos, true);
		return bos.toByteArray();
	}

	public static String asString(InputStream is) throws IOException {
		return new String(asByteArray(is));
	}

	public static void copy(InputStream is, OutputStream os, boolean close)
			throws IOException {
		byte[] buffer = new byte[1024 * 8];
		int len;
		while ((len = is.read(buffer)) > 0) {
			os.write(buffer, 0, len);
		}
		os.flush();
		if (close) {
			Streams.close(is);
			Streams.close(os);
		}
	}

	/**
	 * <p>
	 * Closes the provided stream in a null safe way without an exception.
	 * 
	 * <p>
	 * <i>Q: Shouldn't this be called closeQuietly (or something)?
	 * <p>
	 * <i>A: There is already a close method, so the implication is that this
	 * method does something in addition to it (which it does). I figured it was
	 * more efficient, except for writing this explanation.
	 * 
	 * @param stream
	 *            to be closed (quietly)
	 */
	public static void close(Closeable stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
				// Ignore (that is the only function of this method.
			}
		}
	}

	public static interface CountingStream {

		public long getCount();

	}

	public static class CountingOutputStream extends FilterOutputStream
			implements CountingStream {
		private long count;

		public CountingOutputStream(OutputStream os) {
			super(os);
		}

		public long getCount() {
			return count;
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			out.write(b, off, len);
			count += len;
		}

		@Override
		public void write(int b) throws IOException {
			out.write(b);
			count++;
		}

	}

	public static class CountingInputStream extends FilterInputStream implements
			CountingStream {
		private long count;
		private long mark = -1;

		public CountingInputStream(InputStream is) {
			super(is);
		}

		public long getCount() {
			return count;
		}

		@Override
		public int read() throws IOException {
			int result = in.read();
			if (result != -1) {
				count++;
			}
			return result;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int result = in.read(b, off, len);
			if (result != -1) {
				count += result;
			}
			return result;
		}

		@Override
		public long skip(long n) throws IOException {
			long result = in.skip(n);
			count += result;
			return result;
		}

		@Override
		public synchronized void mark(int readlimit) {
			in.mark(readlimit);
			mark = count;
		}

		@Override
		public synchronized void reset() throws IOException {
			if (!in.markSupported()) {
				throw new IOException("Mark not supported");
			}
			if (mark == -1) {
				throw new IOException("Mark not set");
			}

			in.reset();
			count = mark;
		}
	}

}
