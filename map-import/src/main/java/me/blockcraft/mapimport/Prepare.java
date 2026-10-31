package me.blockcraft.mapimport;

import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Copies the old world's overworld into the finishing server and notes what it held before Paper converts it: every
 * chunk, which the server then loads, and every entity, so the template keeps exactly those. Structures are switched
 * off in the copy, because the finishing server must use the world's own generator but must not add villages or
 * temples to chunks it completes.
 */
final class Prepare {

    private static final String LEVEL = "level.dat";
    private static final String MODERN_OVERWORLD = "dimensions/minecraft/overworld";
    private static final String GENERATOR = "data/minecraft/world_gen_settings.dat";
    private static final List<String> CHUNK_FOLDERS = List.of("region", "entities", "poi");
    private static final Pattern REGION_FILE = Pattern.compile("r\\.-?\\d+\\.-?\\d+\\.mca");
    /** Region files and the chunks too large for them. */
    private static final Pattern CHUNK_FILE = Pattern.compile("r\\.-?\\d+\\.-?\\d+\\.mca|c\\.-?\\d+\\.-?\\d+\\.mcc");
    /** The pictures of map items, which Paper moves to {@link #MAPS} when it converts the world. */
    private static final Pattern LEGACY_MAP = Pattern.compile("map_\\d+\\.dat|idcounts\\.dat");
    static final String MAPS = "data/minecraft/maps";

    private Prepare() {
    }

    static void run(Path source, ImportFiles files) throws IOException {
        if (!Files.isRegularFile(source.resolve(LEVEL))) {
            throw new IllegalArgumentException(source + " is not a world: it has no level.dat");
        }
        boolean modern = Files.isDirectory(source.resolve(MODERN_OVERWORLD));
        Path overworld = modern ? source.resolve(MODERN_OVERWORLD) : source;
        if (!Files.isDirectory(overworld.resolve("region"))) {
            throw new IllegalArgumentException(source + " stores no overworld chunks in " + overworld.resolve("region"));
        }
        if (modern && !Files.isRegularFile(overworld.resolve(GENERATOR))) {
            throw new IllegalArgumentException(source + " has no " + overworld.resolve(GENERATOR));
        }
        List<Path> copied = new ArrayList<>(List.of(source.resolve(LEVEL)));
        if (modern) {
            copied.add(overworld.resolve(GENERATOR));
            copied.addAll(files(source.resolve(MAPS)));
        } else {
            files(source.resolve("data")).stream().filter(file -> LEGACY_MAP.matcher(file.getFileName().toString()).matches()).forEach(copied::add);
        }
        for (String folder : CHUNK_FOLDERS) {
            copied.addAll(files(overworld.resolve(folder)).stream().filter(file -> CHUNK_FILE.matcher(file.getFileName().toString()).matches()).toList());
        }

        String fingerprint = fingerprint(source, copied);
        if (Files.isRegularFile(files.finished()) && Files.isRegularFile(files.source())
                && Files.readString(files.source()).equals(fingerprint)) {
            System.out.println("Reusing the world finished from " + source + " in " + files.world());
            return;
        }

        Files.deleteIfExists(files.finished());
        delete(files.world());
        delete(files.template());
        for (Path file : copied) {
            Path copy = files.world().resolve(source.relativize(file).toString());
            Files.createDirectories(copy.getParent());
            Files.copy(file, copy);
        }
        switchOffStructures(files.world(), modern);

        Set<ChunkPos> chunks = new TreeSet<>(Comparator.comparingInt(ChunkPos::x).thenComparingInt(ChunkPos::z));
        Set<UUID> entities = new TreeSet<>();
        for (String folder : List.of("region", "entities")) {
            for (Path file : regionFiles(overworld.resolve(folder))) {
                var region = RegionFile.position(file);
                for (var chunk : RegionFile.read(file).entrySet()) {
                    var position = ChunkPos.inRegion(region, chunk.getKey());
                    CompoundBinaryTag tag;
                    try {
                        tag = chunk.getValue().read();
                    } catch (IOException e) {
                        System.err.println("Leaving out chunk " + position + " of " + file + ", which cannot be read: " + e.getMessage());
                        continue;
                    }
                    if (folder.equals("region")) {
                        chunks.add(position);
                    }
                    Entities.stored(tag).flatMap(Entities::uuids).forEach(entities::add);
                }
            }
        }
        files.writeChunks(chunks);
        Files.write(files.entities(), entities.stream().map(UUID::toString).toList());
        writeServerProperties(files.server());
        Files.writeString(files.source(), fingerprint);
        System.out.println("Copied " + chunks.size() + " chunks and " + entities.size() + " entities from " + source
                + (modern ? "" : " (pre-26.1 layout)") + " to " + files.world());
    }

    /** 26.1 moved the generator out of level.dat; before 1.16 a flag in level.dat was all there was. */
    private static void switchOffStructures(Path world, boolean modern) throws IOException {
        if (modern) {
            Path file = world.resolve(MODERN_OVERWORLD).resolve(GENERATOR);
            var root = read(file);
            write(file, root.put("data", root.getCompound("data").putBoolean("generate_structures", false)));
            return;
        }
        Path file = world.resolve(LEVEL);
        var data = read(file).getCompound("Data").putBoolean("MapFeatures", false);
        if (data.contains("WorldGenSettings")) {
            data = data.put("WorldGenSettings", data.getCompound("WorldGenSettings").putBoolean("generate_features", false));
        }
        write(file, CompoundBinaryTag.builder().put("Data", data).build());
    }

    static CompoundBinaryTag read(Path file) throws IOException {
        return BinaryTagIO.unlimitedReader().read(file, BinaryTagIO.Compression.GZIP);
    }

    static void write(Path file, CompoundBinaryTag tag) throws IOException {
        BinaryTagIO.writer().write(tag, file, BinaryTagIO.Compression.GZIP);
    }

    /**
     * The finishing server only listens on loopback, lets nobody in, and keeps ticking while empty, since its chunk
     * loads complete on the main thread.
     */
    private static void writeServerProperties(Path server) throws IOException {
        Files.createDirectories(server);
        Files.writeString(server.resolve("server.properties"), String.join("\n",
                "level-name=world",
                "server-ip=127.0.0.1",
                "server-port=25598",
                "online-mode=false",
                "max-players=0",
                "white-list=true",
                "enable-status=false",
                "pause-when-empty-seconds=-1",
                "spawn-protection=0",
                "region-file-compression=deflate",
                ""));
    }

    private static String fingerprint(Path source, List<Path> files) throws IOException {
        var lines = new ArrayList<>(List.of("source " + source));
        for (Path file : files) {
            lines.add(source.relativize(file) + " " + Files.size(file) + " " + Files.getLastModifiedTime(file).toMillis());
        }
        return String.join("\n", lines) + "\n";
    }

    static List<Path> regionFiles(Path folder) throws IOException {
        return files(folder).stream().filter(file -> REGION_FILE.matcher(file.getFileName().toString()).matches()).toList();
    }

    static List<Path> files(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    static void delete(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path child : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(child);
            }
        }
    }
}
