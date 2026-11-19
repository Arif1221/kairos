package com.skarif.kairos.lyrics;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Random-access bytes. Tag parsing jumps around (and skips multi-megabyte cover art and audio),
 * so it reads by position instead of streaming the whole file.
 */
public interface ByteSource {
    long length() throws IOException;

    /** Reads exactly {@code len} bytes at {@code pos} or throws EOFException. */
    void readFully(long pos, byte[] dst, int off, int len) throws IOException;

    /** Works with a FileInputStream from ContentResolver.openFileDescriptor(...). */
    final class OfChannel implements ByteSource {
        private final FileChannel channel;

        public OfChannel(FileChannel channel) {
            this.channel = channel;
        }

        @Override
        public long length() throws IOException {
            return channel.size();
        }

        @Override
        public void readFully(long pos, byte[] dst, int off, int len) throws IOException {
            ByteBuffer buf = ByteBuffer.wrap(dst, off, len);
            long p = pos;
            while (buf.hasRemaining()) {
                int n = channel.read(buf, p);
                if (n < 0) throw new EOFException();
                p += n;
            }
        }
    }

    final class OfBytes implements ByteSource {
        private final byte[] data;

        public OfBytes(byte[] data) {
            this.data = data;
        }

        @Override
        public long length() {
            return data.length;
        }

        @Override
        public void readFully(long pos, byte[] dst, int off, int len) throws IOException {
            if (pos < 0 || pos + len > data.length) throw new EOFException();
            System.arraycopy(data, (int) pos, dst, off, len);
        }
    }
}
