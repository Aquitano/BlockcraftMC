package me.blockcraft.mapimport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * The scratch directory one import works in: the finishing server with its copy of the old world, what the old world
 * held before Paper touched it, and the finished template. It is shared with {@link FinishPlugin}, so it holds no NBT.
 */
public record ImportFiles(Path root) {

    public static final String SCRATCH_PROPERTY = "blockcraft.mapimport.scratch";

    public static ImportFiles at(Path root) {
        return new ImportFiles(root.toAbsolutePath().normalize());
    }

    public Path server() {
        return root.resolve("server");
    }

    public Path world() {
        return server().resolve("world");
    }

    public Path overworld() {
        return world().resolve("dimensions/minecraft/overworld");
    }

    /** Every chunk the old world stored; the finishing server loads exactly these. */
    public Path chunks() {
        return root.resolve("chunks.txt");
    }

    /** The UUID of every entity the old world stored, so nothing the finishing server spawned gets into the map. */
    public Path entities() {
        return root.resolve("entities.txt");
    }

    /** Which source the finished world was made from; a matching one is reused. */
    public Path source() {
        return root.resolve("source.txt");
    }

    /** Written by the finishing server once every chunk loaded. */
    public Path finished() {
        return root.resolve("finished");
    }

    public Path template() {
        return root.resolve("template");
    }

    public List<ChunkPos> readChunks() {
        try (var lines = Files.lines(chunks())) {
            return lines.filter(line -> !line.isBlank()).map(ChunkPos::parse).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeChunks(Collection<ChunkPos> chunks) throws IOException {
        Files.write(chunks(), chunks.stream().map(ChunkPos::toString).toList());
    }
}
