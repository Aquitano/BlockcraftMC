package me.blockcraft.mapimport;

import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * An Anvil region file ({@code r.<x>.<z>.mca}): a header of 1024 sector offsets and 1024 timestamps, then each chunk as
 * a length, a compression byte and the compressed NBT. A chunk too large for 255 sectors lives in {@code c.<x>.<z>.mcc}
 * next to it, flagged by the compression byte's high bit.
 */
public final class RegionFile {

    private static final int SECTOR = 4096;
    private static final int MAX_SECTORS = 255;
    private static final int EXTERNAL = 0x80;
    private static final byte GZIP = 1;
    private static final byte ZLIB = 2;
    private static final byte NONE = 3;

    /** A chunk as stored: kept verbatim unless it is changed, so a copied chunk stays byte for byte the same. */
    public record Entry(int timestamp, byte compression, byte[] payload) {

        public CompoundBinaryTag read() throws IOException {
            InputStream raw = new ByteArrayInputStream(payload);
            InputStream in = switch (compression) {
                case GZIP -> new GZIPInputStream(raw);
                case ZLIB -> new InflaterInputStream(raw);
                case NONE -> raw;
                default -> throw new IOException("Unsupported chunk compression " + compression
                        + "; set region-file-compression=deflate and save the world again");
            };
            try (in) {
                return BinaryTagIO.unlimitedReader().read(in, BinaryTagIO.Compression.NONE);
            }
        }

        public static Entry of(CompoundBinaryTag tag, int timestamp) throws IOException {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DeflaterOutputStream(bytes)) {
                BinaryTagIO.writer().write(tag, out, BinaryTagIO.Compression.NONE);
            }
            return new Entry(timestamp, ZLIB, bytes.toByteArray());
        }
    }

    private RegionFile() {
    }

    public static ChunkPos position(Path file) {
        var parts = file.getFileName().toString().split("\\.");
        if (parts.length != 4 || !parts[0].equals("r") || !parts[3].equals("mca")) {
            throw new IllegalArgumentException("Not a region file: " + file);
        }
        return new ChunkPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    /**
     * The chunks of the file by their header index; an empty or truncated file has none. A chunk whose entry points
     * past the end of the file is left out, as Minecraft does.
     */
    public static SortedMap<Integer, Entry> read(Path file) throws IOException {
        SortedMap<Integer, Entry> chunks = new TreeMap<>();
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length < 2 * SECTOR) {
            return chunks;
        }
        var buffer = ByteBuffer.wrap(bytes);
        var region = position(file);
        for (int index = 0; index < 1024; index++) {
            int sector = buffer.getInt(index * 4) >>> 8;
            if (sector == 0) {
                continue;
            }
            int start = sector * SECTOR;
            int length = start + 5 <= bytes.length ? buffer.getInt(start) : 0;
            if (length < 1 || start + 4 + length > bytes.length) {
                System.err.println("Leaving out chunk " + ChunkPos.inRegion(region, index) + " of " + file + ", whose entry is broken");
                continue;
            }
            byte compression = bytes[start + 4];
            byte[] payload;
            if ((compression & EXTERNAL) != 0) {
                payload = Files.readAllBytes(file.resolveSibling(external(ChunkPos.inRegion(region, index))));
                compression = (byte) (compression & ~EXTERNAL);
            } else {
                payload = new byte[length - 1];
                System.arraycopy(bytes, start + 5, payload, 0, length - 1);
            }
            chunks.put(index, new Entry(buffer.getInt(SECTOR + index * 4), compression, payload));
        }
        return chunks;
    }

    public static void write(Path file, Map<Integer, Entry> chunks) throws IOException {
        var header = ByteBuffer.allocate(2 * SECTOR);
        var body = new ByteArrayOutputStream();
        var region = position(file);
        int sector = 2;
        for (var chunk : new TreeMap<>(chunks).entrySet()) {
            var entry = chunk.getValue();
            int sectors = (entry.payload().length + 5 + SECTOR - 1) / SECTOR;
            ByteBuffer stored;
            if (sectors > MAX_SECTORS) {
                Files.write(file.resolveSibling(external(ChunkPos.inRegion(region, chunk.getKey()))), entry.payload());
                sectors = 1;
                stored = ByteBuffer.allocate(SECTOR).putInt(1).put((byte) (entry.compression() | EXTERNAL));
            } else {
                stored = ByteBuffer.allocate(sectors * SECTOR).putInt(entry.payload().length + 1)
                        .put(entry.compression()).put(entry.payload());
            }
            body.write(stored.array());
            header.putInt(chunk.getKey() * 4, sector << 8 | sectors);
            header.putInt(SECTOR + chunk.getKey() * 4, entry.timestamp());
            sector += sectors;
        }
        Files.createDirectories(file.getParent());
        try (var out = Files.newOutputStream(file)) {
            out.write(header.array());
            body.writeTo(out);
        }
    }

    private static String external(ChunkPos chunk) {
        return "c." + chunk.x() + "." + chunk.z() + ".mcc";
    }
}
