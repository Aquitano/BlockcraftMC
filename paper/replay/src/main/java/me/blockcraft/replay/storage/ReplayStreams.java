package me.blockcraft.replay.storage;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

final class ReplayStreams {
    private ReplayStreams() { }

    static InputStream limited(InputStream input, long maximum) {
        return new FilterInputStream(input) {
            private long count;

            @Override
            public int read() throws IOException {
                int result = in.read();
                if (result >= 0) check(1);
                return result;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                int result = in.read(bytes, offset, (int) Math.min(length, Math.max(1, maximum - count + 1)));
                if (result > 0) check(result);
                return result;
            }

            private void check(int length) throws IOException {
                count += length;
                if (count > maximum) throw new IOException("Replay exceeds byte budget: " + maximum);
            }
        };
    }

    static OutputStream limited(OutputStream output, long maximum) {
        return new FilterOutputStream(output) {
            private long count;

            @Override
            public void write(int value) throws IOException {
                check(1);
                out.write(value);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                check(length);
                out.write(bytes, offset, length);
            }

            private void check(int length) throws IOException {
                if (length > maximum - count) throw new IOException("Replay exceeds byte budget: " + maximum);
                count += length;
            }
        };
    }

    static final class StrictGzipInput extends InflaterInputStream {
        private final CRC32 crc = new CRC32();
        private long decoded;
        private boolean verified;

        StrictGzipInput(InputStream input) throws IOException {
            super(input, new Inflater(true), 8192);
            byte[] header = input.readNBytes(10);
            if (header.length != 10 || (header[0] & 255) != 31 || (header[1] & 255) != 139
                    || header[2] != 8 || header[3] != 0) {
                inf.end();
                throw new IOException("Invalid replay GZIP header");
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int result = super.read(bytes, offset, length);
            if (result > 0) {
                crc.update(bytes, offset, result);
                decoded += result;
            } else if (result == -1 && !verified) {
                verifyTrailer();
                verified = true;
            }
            return result;
        }

        private void verifyTrailer() throws IOException {
            int remaining = inf.getRemaining();
            if (remaining > 8) throw new IOException("Trailing compressed replay data");
            byte[] trailer = new byte[8];
            System.arraycopy(buf, len - remaining, trailer, 0, remaining);
            int missing = 8 - remaining;
            byte[] rest = in.readNBytes(missing);
            if (rest.length != missing) throw new EOFException("Truncated replay GZIP trailer");
            System.arraycopy(rest, 0, trailer, remaining, missing);
            if (in.read() != -1) throw new IOException("Trailing compressed replay data");
            if (littleEndian(trailer, 0) != crc.getValue() || littleEndian(trailer, 4) != (decoded & 0xffff_ffffL)) {
                throw new IOException("Invalid replay GZIP checksum");
            }
        }

        private static long littleEndian(byte[] bytes, int start) {
            long value = 0;
            for (int i = 0; i < 4; i++) value |= (bytes[start + i] & 255L) << (8 * i);
            return value;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                inf.end();
            }
        }
    }
}
