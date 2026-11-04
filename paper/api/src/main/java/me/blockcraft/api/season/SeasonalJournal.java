package me.blockcraft.api.season;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SeasonalJournal {

    private static final int MAGIC = 0x53454131;
    record Chunk(int x, int z) { }
    record Change(SeasonalLayout.Point at, String original, String replacement) {
        boolean restores(String current) {
            return replacement.equals(current);
        }
    }

    private final Path directory;

    SeasonalJournal(Path directory) {
        this.directory = directory;
    }

    void write(Chunk chunk, List<Change> changes) throws IOException {
        Files.createDirectories(directory);
        Path target = file(chunk);
        Path staging = target.resolveSibling(target.getFileName() + ".tmp");
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(staging)))) {
            out.writeInt(MAGIC);
            out.writeInt(changes.size());
            for (var change : changes) {
                out.writeInt(change.at().x());
                out.writeInt(change.at().y());
                out.writeInt(change.at().z());
                out.writeUTF(change.original());
                out.writeUTF(change.replacement());
            }
        }
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unavailable) {
            Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    Map<Chunk, List<Change>> read() throws IOException {
        var result = new LinkedHashMap<Chunk, List<Change>>();
        if (Files.notExists(directory)) {
            return result;
        }
        try (var files = Files.list(directory)) {
            for (var file : files.filter(path -> path.getFileName().toString().matches("-?\\d+\\.-?\\d+\\.dat")).sorted().toList()) {
                var parts = file.getFileName().toString().split("\\.");
                var chunk = new Chunk(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                var changes = new ArrayList<Change>();
                try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                    if (in.readInt() != MAGIC) {
                        throw new IOException("Invalid seasonal journal: " + file);
                    }
                    int count = in.readInt();
                    if (count < 0 || count > 262144) {
                        throw new IOException("Invalid seasonal journal size: " + file);
                    }
                    for (int i = 0; i < count; i++) {
                        var at = new SeasonalLayout.Point(in.readInt(), in.readInt(), in.readInt());
                        if (at.x() >> 4 != chunk.x() || at.z() >> 4 != chunk.z()) {
                            throw new IOException("Seasonal journal position belongs to another chunk: " + file);
                        }
                        changes.add(new Change(at, in.readUTF(), in.readUTF()));
                    }
                }
                result.put(chunk, List.copyOf(changes));
            }
        }
        return result;
    }

    void remove(Chunk chunk) throws IOException {
        Files.deleteIfExists(file(chunk));
    }

    private Path file(Chunk chunk) {
        return directory.resolve(chunk.x() + "." + chunk.z() + ".dat");
    }
}
