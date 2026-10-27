package me.blockcraft.mapimport;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Builds the template from the finished world: the chunks the mode keeps, their entities without loose items and
 * without anything the finishing server spawned, their points of interest, and a generator for everything else. Only
 * these, and for a new template its level.dat, Paper's metadata of the dimension and the pictures of map items, leave
 * the scratch directory; the server's other data files (game rules, raids, weather) stay behind.
 */
final class Write {

    private static final String GENERATOR = "data/minecraft/world_gen_settings.dat";
    /** Without it Paper treats the dimension as a vanilla world and migrates it at every start. */
    private static final String PAPER_DATA = "data/paper";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String LEVEL_OVERWORLD = "dimensions/minecraft/overworld";
    private static final List<String> CHUNK_FOLDERS = List.of("region", "entities", "poi");
    private static final int REPORTED_AREAS = 10;
    private static final int AREA_GAP = 2;

    enum Layout {
        /** A server group's map: level.dat and the overworld under dimensions/minecraft/overworld. */
        LEVEL,
        /** One dimension folder, like a BedWars pool map's {@code maps/<key>/world}. */
        DIMENSION
    }

    record Settings(Optional<String> mode, List<Box> bounds, int margin, Layout layout, Optional<Path> target, boolean dryRun) {

        static Settings of(Map<String, String> options) {
            var target = MapImport.option(options, "target").map(path -> Path.of(path).toAbsolutePath().normalize());
            boolean dryRun = options.containsKey("dry-run");
            if (target.isEmpty() && !dryRun) {
                throw new IllegalArgumentException("Name the template with -Ptarget=<directory>, or look first with -PdryRun");
            }
            String layoutName = MapImport.option(options, "layout").orElse("level");
            var layout = Arrays.stream(Layout.values()).filter(value -> value.name().equalsIgnoreCase(layoutName)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown layout " + layoutName + "; use level or dimension"));
            var bounds = MapImport.option(options, "bounds").map(Box::parseAll).orElse(List.of());
            int margin = Integer.parseInt(MapImport.option(options, "margin").orElse("0"));
            return new Settings(MapImport.option(options, "mode"), bounds, margin, layout, target, dryRun);
        }

        boolean inBounds(ChunkPos chunk) {
            return bounds.isEmpty() || bounds.stream().anyMatch(box -> box.reaches(chunk, margin));
        }
    }

    private final ImportFiles files;
    private final Settings settings;
    private final Set<ChunkPos> original;
    private final Set<UUID> originalEntities = new HashSet<>();
    private final Path template;
    private final Path dimension;
    private final Counts counts = new Counts();
    private Mode mode;

    private Write(ImportFiles files, Settings settings) throws IOException {
        this.files = files;
        this.settings = settings;
        original = new HashSet<>(files.readChunks());
        try (Stream<String> lines = Files.lines(files.entities())) {
            lines.filter(line -> !line.isBlank()).map(UUID::fromString).forEach(originalEntities::add);
        }
        template = files.template();
        dimension = settings.layout() == Layout.LEVEL ? template.resolve(LEVEL_OVERWORLD) : template;
    }

    static void run(ImportFiles files, Settings settings) throws IOException {
        if (!Files.isRegularFile(files.finished())) {
            throw new IllegalStateException("The world in " + files.world() + " is not finished; run importMap, not this step alone");
        }
        new Write(files, settings).run();
    }

    private void run() throws IOException {
        var generatorSettings = Prepare.read(files.overworld().resolve(GENERATOR));
        var dimensions = generatorSettings.getCompound("data").getCompound("dimensions");
        var generator = dimensions.getCompound(OVERWORLD).getCompound("generator");
        mode = mode(generator);

        Prepare.delete(template);
        for (Path regionFile : Prepare.regionFiles(files.overworld().resolve("region"))) {
            keep(regionFile);
        }
        if (!counts.unfinished.isEmpty()) {
            throw new IllegalStateException(counts.unfinished.size() + " chunks are not finished, for example " + counts.unfinished.getFirst()
                    + "; delete " + files.finished() + " and run importMap again");
        }
        int missing = original.size() - counts.chunks;
        if (missing > 0) {
            throw new IllegalStateException(missing + " chunks of the old world are missing from the finished one");
        }

        var overworld = dimensions.getCompound(OVERWORLD).put("generator", mode instanceof Mode.Void ? voidGenerator() : generator);
        var data = generatorSettings.getCompound("data").put("dimensions", dimensions.put(OVERWORLD, overworld)).putBoolean("generate_structures", false);
        Files.createDirectories(dimension.resolve(GENERATOR).getParent());
        Prepare.write(dimension.resolve(GENERATOR), generatorSettings.put("data", data));
        copyFiles(files.overworld().resolve(PAPER_DATA), dimension.resolve(PAPER_DATA));
        int maps = Prepare.files(files.world().resolve(Prepare.MAPS)).size();
        if (settings.layout() == Layout.LEVEL) {
            Files.copy(files.world().resolve("level.dat"), template.resolve("level.dat"));
            copyFiles(files.world().resolve(Prepare.MAPS), template.resolve(Prepare.MAPS));
        }

        counts.report(mode);
        if (maps > 0) {
            System.out.println(settings.layout() == Layout.LEVEL ? "Kept the pictures of the old world's map items (" + maps + " files)"
                    : "A dimension cannot hold the pictures of map items (" + maps + " files), so maps in item frames show blank");
        }
        System.out.println("Template: " + template);
        if (settings.target().isEmpty() || settings.dryRun()) {
            System.out.println("Dry run: nothing written" + settings.target().map(target -> " to " + target).orElse(""));
            return;
        }
        publish(settings.target().get());
    }

    private void keep(Path regionFile) throws IOException {
        var region = RegionFile.position(regionFile);
        var name = regionFile.getFileName().toString();
        var entityChunks = readIfPresent(files.overworld().resolve("entities").resolve(name));
        var poiChunks = readIfPresent(files.overworld().resolve("poi").resolve(name));
        Map<Integer, RegionFile.Entry> keptChunks = new TreeMap<>();
        Map<Integer, RegionFile.Entry> keptEntities = new TreeMap<>();
        Map<Integer, RegionFile.Entry> keptPoi = new TreeMap<>();
        for (var stored : RegionFile.read(regionFile).entrySet()) {
            int index = stored.getKey();
            var position = ChunkPos.inRegion(region, index);
            if (!original.contains(position)) {
                continue;
            }
            counts.chunks++;
            var chunk = new Chunk(stored.getValue().read());
            if (!chunk.status().equals("minecraft:full")) {
                counts.unfinished.add(position);
                continue;
            }
            var entityChunk = entityChunks.get(index);
            var entityTag = entityChunk == null ? CompoundBinaryTag.empty() : entityChunk.read();
            List<CompoundBinaryTag> entities = new ArrayList<>();
            for (var entity : entityTag.getList("Entities", BinaryTagTypes.COMPOUND)) {
                var fate = Entities.fate((CompoundBinaryTag) entity, originalEntities);
                if (fate == Entities.Fate.KEPT) {
                    entities.add((CompoundBinaryTag) entity);
                }
                counts.entities.merge(fate, 1, Integer::sum);
            }
            if (!settings.inBounds(position) || !mode.keeps(chunk, !entities.isEmpty())) {
                counts.entitiesInDroppedChunks += entities.size();
                continue;
            }
            counts.kept.add(position);
            counts.blockEntities += chunk.tag().getList("block_entities").size();
            counts.blockTicks += chunk.tag().getList("block_ticks").size();
            chunk.tag().getList("fluid_ticks", BinaryTagTypes.COMPOUND).forEach(tick -> counts.fluidTicks.add(position(tick)));
            keptChunks.put(index, stored.getValue());
            if (!entities.isEmpty()) {
                boolean unchanged = entities.size() == entityTag.getList("Entities").size();
                keptEntities.put(index, unchanged ? entityChunk
                        : RegionFile.Entry.of(entityTag.put("Entities", ListBinaryTag.from(entities)), entityChunk.timestamp()));
            }
            if (poiChunks.containsKey(index)) {
                keptPoi.put(index, poiChunks.get(index));
            }
        }
        writeIfAny(dimension.resolve("region").resolve(name), keptChunks);
        writeIfAny(dimension.resolve("entities").resolve(name), keptEntities);
        writeIfAny(dimension.resolve("poi").resolve(name), keptPoi);
    }

    private Mode mode(CompoundBinaryTag generator) {
        String type = generator.getString("type");
        boolean flat = type.equals(Mode.FLAT_GENERATOR);
        String name = settings.mode().orElse(flat ? "flat" : "");
        return switch (name) {
            case "flat" -> {
                if (!flat) {
                    throw new IllegalArgumentException("The old world is not superflat (" + type + "); use -Pmode=void or all");
                }
                yield Mode.Flat.of(generator.getCompound("settings"));
            }
            case "void" -> new Mode.Void();
            case "all" -> new Mode.All();
            case "" -> throw new IllegalArgumentException("The old world is not superflat (" + type
                    + "); choose -Pmode=void (keep every chunk with a block) or all (keep every chunk, best with -Pbounds)");
            default -> throw new IllegalArgumentException("Unknown mode " + name + "; use flat, void or all");
        };
    }

    private static CompoundBinaryTag voidGenerator() {
        var settings = CompoundBinaryTag.builder()
                .put("layers", ListBinaryTag.empty())
                .putString("biome", "minecraft:the_void")
                .putBoolean("features", false)
                .putBoolean("lakes", false)
                .put("structure_overrides", ListBinaryTag.empty())
                .build();
        return CompoundBinaryTag.builder().putString("type", Mode.FLAT_GENERATOR).put("settings", settings).build();
    }

    /**
     * Replaces the target's chunks and generator with the template's. A target that already is a map keeps its
     * level.dat, Paper's metadata (both hold the spawn), its map pictures and everything else in it; a new one gets
     * the finished world's.
     */
    private void publish(Path target) throws IOException {
        Path to = settings.layout() == Layout.LEVEL ? target.resolve(LEVEL_OVERWORLD) : target;
        for (String folder : CHUNK_FOLDERS) {
            Prepare.delete(to.resolve(folder));
            copyFiles(dimension.resolve(folder), to.resolve(folder));
        }
        Files.createDirectories(to.resolve(GENERATOR).getParent());
        Files.copy(dimension.resolve(GENERATOR), to.resolve(GENERATOR), StandardCopyOption.REPLACE_EXISTING);
        copyFiles(dimension.resolve(PAPER_DATA), to.resolve(PAPER_DATA));
        if (settings.layout() == Layout.LEVEL) {
            if (Files.notExists(target.resolve("level.dat"))) {
                Files.copy(template.resolve("level.dat"), target.resolve("level.dat"));
            }
            copyFiles(template.resolve(Prepare.MAPS), target.resolve(Prepare.MAPS));
        }
        System.out.println("Wrote the template to " + target);
    }

    /** Copies the files of a folder, leaving those the target already has. */
    private static void copyFiles(Path from, Path to) throws IOException {
        for (Path file : Prepare.files(from)) {
            Files.createDirectories(to);
            if (Files.notExists(to.resolve(file.getFileName()))) {
                Files.copy(file, to.resolve(file.getFileName()));
            }
        }
    }

    private static String position(BinaryTag tick) {
        var compound = (CompoundBinaryTag) tick;
        return compound.getInt("x") + " " + compound.getInt("y") + " " + compound.getInt("z");
    }

    private static Map<Integer, RegionFile.Entry> readIfPresent(Path file) throws IOException {
        return Files.isRegularFile(file) ? RegionFile.read(file) : Map.of();
    }

    private static void writeIfAny(Path file, Map<Integer, RegionFile.Entry> chunks) throws IOException {
        if (!chunks.isEmpty()) {
            RegionFile.write(file, chunks);
        }
    }

    private static final class Counts {
        int chunks;
        final List<ChunkPos> unfinished = new ArrayList<>();
        final List<ChunkPos> kept = new ArrayList<>();
        final Map<Entities.Fate, Integer> entities = new EnumMap<>(Entities.Fate.class);
        int entitiesInDroppedChunks;
        int blockEntities;
        int blockTicks;
        final List<String> fluidTicks = new ArrayList<>();

        void report(Mode mode) {
            System.out.println("Mode: " + mode.getClass().getSimpleName().toLowerCase()
                    + (mode instanceof Mode.Flat flat ? ", ground " + flat.describe() : ""));
            System.out.println("Kept " + kept.size() + " of " + chunks + " chunks, with " + blockEntities + " block entities");
            var areas = areas(kept);
            if (!areas.isEmpty()) {
                System.out.println("Kept chunks lie in " + areas.size() + (areas.size() == 1 ? " area" : " areas") + ", largest first (as -Pbounds boxes):");
                areas.stream().limit(REPORTED_AREAS).forEach(area -> System.out.println("  " + Box.around(area) + "  " + area.size() + " chunks"));
                if (areas.size() > REPORTED_AREAS) {
                    System.out.println("  and " + (areas.size() - REPORTED_AREAS) + " smaller areas");
                }
            }
            System.out.println("Entities: kept " + (entities.getOrDefault(Entities.Fate.KEPT, 0) - entitiesInDroppedChunks)
                    + ", dropped " + entities.getOrDefault(Entities.Fate.LOOSE_ITEM, 0) + " loose items, "
                    + entitiesInDroppedChunks + " in dropped chunks and " + entities.getOrDefault(Entities.Fate.ADDED, 0) + " the finishing server spawned");
            if (blockTicks + fluidTicks.size() > 0) {
                System.out.println("Kept chunks hold " + blockTicks + " scheduled block and " + fluidTicks.size() + " fluid updates;"
                        + " they run at every start once a player comes near");
            }
            if (!fluidTicks.isEmpty()) {
                System.out.println("Water or lava may flow there: " + String.join(", ", fluidTicks.stream().limit(REPORTED_AREAS).toList())
                        + (fluidTicks.size() > REPORTED_AREAS ? " and more" : ""));
            }
            System.out.println("New chunks generate as " + mode.outside());
        }

        /** Kept chunks at most {@link #AREA_GAP} chunks apart belong to one area. */
        private static List<List<ChunkPos>> areas(List<ChunkPos> chunks) {
            Set<ChunkPos> left = new HashSet<>(chunks);
            List<List<ChunkPos>> areas = new ArrayList<>();
            while (!left.isEmpty()) {
                var start = left.iterator().next();
                left.remove(start);
                List<ChunkPos> area = new ArrayList<>(List.of(start));
                for (int i = 0; i < area.size(); i++) {
                    var chunk = area.get(i);
                    for (int dx = -AREA_GAP; dx <= AREA_GAP; dx++) {
                        for (int dz = -AREA_GAP; dz <= AREA_GAP; dz++) {
                            var near = new ChunkPos(chunk.x() + dx, chunk.z() + dz);
                            if (left.remove(near)) {
                                area.add(near);
                            }
                        }
                    }
                }
                areas.add(area);
            }
            areas.sort(Comparator.comparingInt((List<ChunkPos> area) -> area.size()).reversed());
            return areas;
        }
    }
}
