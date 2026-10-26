package me.blockcraft.endergames.world;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.endergames.config.EnderGamesConfig.WorldSettings;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The round's world: a vanilla overworld generated from a new seed at every server start. While the lobby waits it
 * looks for a centre on dry land, puts the border around it and loads every chunk inside, so the round starts
 * without generating terrain; only then is it {@link #ready()}.
 */
public final class RoundWorld {

    private static final NamespacedKey KEY = new NamespacedKey("endergames", "runde");
    private static final int SAMPLES_PER_SIDE = 5;
    private static final double LAND_SHARE = 0.8;
    private static final int LOADS_IN_FLIGHT = 16;
    private static final long DAY = 1000L;
    private static final int SPECTATOR_HEIGHT = 20;
    private static final int SPOT_SEARCH_RADIUS = 12;
    private static final int SPOT_SEARCH_STEP = 3;
    private static final int RING_STEP_INWARD = 8;
    private static final Set<Material> UNSAFE = Set.of(Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH,
            Material.POINTED_DRIPSTONE, Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE);

    private record Candidate(int x, int z, double land) {
    }

    private final Plugin plugin;
    private final World world;
    private final WorldSettings settings;
    private final Random random = new Random();
    private int centerX;
    private int centerZ;
    private boolean ready;

    private RoundWorld(Plugin plugin, World world, WorldSettings settings) {
        this.plugin = plugin;
        this.world = world;
        this.settings = settings;
    }

    /** Throws the last start's world away and generates a new one; the dimension must not be loaded yet. */
    public static RoundWorld create(Plugin plugin, WorldSettings settings) {
        var stale = Bukkit.getWorld(KEY);
        if (stale != null) {
            Bukkit.unloadWorld(stale, false);
        }
        deleteRecursively(folder());
        var world = Objects.requireNonNull(WorldCreator.ofKey(KEY)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .seed(new Random().nextLong())
                .generateStructures(true)
                .createWorld(), "Could not create the EnderGames world");
        world.setAutoSave(false);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.SPAWN_PATROLS, false);
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        world.setGameRule(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(DAY);
        return new RoundWorld(plugin, world, settings);
    }

    public World world() {
        return world;
    }

    public boolean ready() {
        return ready;
    }

    public boolean contains(World other) {
        return world.equals(other);
    }

    /** Finds the centre, sets the border and loads its chunks; {@code onReady} runs on the main thread. */
    public void prepare(Runnable onReady) {
        search(0, new Candidate(0, 0, -1), centre -> {
            centerX = centre.x();
            centerZ = centre.z();
            plugin.getLogger().info("EnderGames world centred on " + centerX + ", " + centerZ
                    + " with " + Math.round(centre.land() * 100) + "% land");
            var border = world.getWorldBorder();
            border.setCenter(centerX + 0.5, centerZ + 0.5);
            border.setSize(settings.radius() * 2.0);
            world.setSpawnLocation(spectatorSpawn());
            preload(chunksInside(), () -> {
                ready = true;
                plugin.getLogger().info("EnderGames world is loaded");
                onReady.run();
            });
        });
    }

    /** Tries candidates on a spiral of {@code search-step} until one is mostly land, else keeps the best. */
    private void search(int attempt, Candidate best, Consumer<Candidate> found) {
        if (attempt >= settings.searchAttempts()) {
            found.accept(best);
            return;
        }
        int x = spiral(attempt, true) * settings.searchStep();
        int z = spiral(attempt, false) * settings.searchStep();
        var points = samples(x, z);
        CompletableFuture.allOf(points.stream()
                        .map(point -> world.getChunkAtAsync(point[0] >> 4, point[1] >> 4))
                        .toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> Tasks.sync(plugin, () -> {
                    if (failure != null) {
                        plugin.getLogger().log(Level.WARNING, "Could not load a candidate centre", failure);
                    }
                    long land = points.stream().filter(point -> isLand(point[0], point[1])).count();
                    var candidate = new Candidate(x, z, (double) land / points.size());
                    var better = candidate.land() > best.land() ? candidate : best;
                    if (candidate.land() >= LAND_SHARE) {
                        found.accept(candidate);
                    } else {
                        search(attempt + 1, better, found);
                    }
                }));
    }

    /** The cells of a square spiral out from 0: the centre, then ring after ring of the search grid. */
    static int spiral(int attempt, boolean x) {
        if (attempt == 0) {
            return 0;
        }
        int ring = (int) Math.ceil((Math.sqrt(attempt + 1) - 1) / 2);
        int side = 2 * ring;
        int index = attempt - (2 * ring - 1) * (2 * ring - 1);
        int edge = index / side;
        int offset = index % side;
        int cx = switch (edge) {
            case 0 -> ring;
            case 1 -> ring - 1 - offset;
            case 2 -> -ring;
            default -> -ring + 1 + offset;
        };
        int cz = switch (edge) {
            case 0 -> -ring + 1 + offset;
            case 1 -> ring;
            case 2 -> ring - 1 - offset;
            default -> -ring;
        };
        return x ? cx : cz;
    }

    private List<int[]> samples(int x, int z) {
        var points = new ArrayList<int[]>();
        int radius = settings.radius();
        for (int i = 0; i < SAMPLES_PER_SIDE; i++) {
            for (int j = 0; j < SAMPLES_PER_SIDE; j++) {
                points.add(new int[]{
                        x - radius + 2 * radius * i / (SAMPLES_PER_SIDE - 1),
                        z - radius + 2 * radius * j / (SAMPLES_PER_SIDE - 1)});
            }
        }
        return points;
    }

    private boolean isLand(int x, int z) {
        return !world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES).isLiquid();
    }

    private List<int[]> chunksInside() {
        int min = (centerX - settings.radius()) >> 4;
        int max = (centerX + settings.radius()) >> 4;
        int minZ = (centerZ - settings.radius()) >> 4;
        int maxZ = (centerZ + settings.radius()) >> 4;
        var chunks = new ArrayList<int[]>();
        for (int cx = min; cx <= max; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        int originX = centerX >> 4;
        int originZ = centerZ >> 4;
        chunks.sort(Comparator.comparingInt(chunk -> Math.abs(chunk[0] - originX) + Math.abs(chunk[1] - originZ)));
        return chunks;
    }

    /** A few loads at a time, so the generation does not starve the lobby's tick; each chunk stays loaded. */
    private void preload(List<int[]> nearestFirst, Runnable done) {
        new Preload(new ArrayList<>(nearestFirst.reversed()), done).pump();
    }

    private final class Preload {

        private final List<int[]> pending;
        private final Runnable done;
        private int running;

        private Preload(List<int[]> pending, Runnable done) {
            this.pending = pending;
            this.done = done;
        }

        private void pump() {
            while (running < LOADS_IN_FLIGHT && !pending.isEmpty()) {
                var chunk = pending.removeLast();
                running++;
                world.getChunkAtAsync(chunk[0], chunk[1]).whenComplete((loaded, failure) -> Tasks.sync(plugin, () -> {
                    running--;
                    if (loaded != null) {
                        loaded.addPluginChunkTicket(plugin);
                    }
                    if (pending.isEmpty() && running == 0) {
                        done.run();
                    } else {
                        pump();
                    }
                }));
            }
        }
    }

    public Location center() {
        return new Location(world, centerX + 0.5, world.getHighestBlockYAt(centerX, centerZ) + 1, centerZ + 0.5);
    }

    public Location spectatorSpawn() {
        return center().add(0, SPECTATOR_HEIGHT, 0);
    }

    public double borderRadius() {
        return world.getWorldBorder().getSize() / 2;
    }

    /**
     * Where each of {@code count} players starts: evenly on the spawn ring, each moved inward until it stands on
     * dry ground; facing the centre.
     */
    public List<Location> ringSpawns(int count) {
        double radius = settings.radius() * settings.spawnRing();
        var spawns = new ArrayList<Location>();
        for (var point : Ring.points(centerX + 0.5, centerZ + 0.5, radius, count, random.nextDouble() * 2 * Math.PI)) {
            spawns.add(towardsCentre(point).orElseGet(this::center));
        }
        return spawns;
    }

    private Optional<Location> towardsCentre(Ring.Point point) {
        double dx = point.x() - centerX;
        double dz = point.z() - centerZ;
        double length = Math.max(1, Math.hypot(dx, dz));
        for (double back = 0; back < length; back += RING_STEP_INWARD) {
            double x = point.x() - dx / length * back;
            double z = point.z() - dz / length * back;
            var spot = near((int) Math.floor(x), (int) Math.floor(z));
            if (spot.isPresent()) {
                var location = spot.get();
                location.setDirection(center().toVector().subtract(location.toVector()));
                location.setPitch(0);
                return spot;
            }
        }
        return Optional.empty();
    }

    /** A random safe place inside the border, for an ender chest. */
    public Optional<Location> randomSpot(double insideRadius) {
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = Math.sqrt(random.nextDouble()) * insideRadius;
            var spot = surface((int) Math.floor(centerX + Math.cos(angle) * distance),
                    (int) Math.floor(centerZ + Math.sin(angle) * distance));
            if (spot.isPresent()) {
                return spot;
            }
        }
        return Optional.empty();
    }

    private Optional<Location> near(int x, int z) {
        for (int radius = 0; radius <= SPOT_SEARCH_RADIUS; radius += SPOT_SEARCH_STEP) {
            for (int dx = -radius; dx <= radius; dx += SPOT_SEARCH_STEP) {
                for (int dz = -radius; dz <= radius; dz += SPOT_SEARCH_STEP) {
                    var spot = surface(x + dx, z + dz);
                    if (spot.isPresent()) {
                        return spot;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** The block above the ground at x/z if a player can stand there: solid, harmless ground and two free blocks. */
    public Optional<Location> surface(int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return Optional.empty();
        }
        var ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (!safeGround(ground)) {
            return Optional.empty();
        }
        var feet = ground.getRelative(BlockFace.UP);
        var head = feet.getRelative(BlockFace.UP);
        if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()) {
            return Optional.empty();
        }
        return Optional.of(feet.getLocation().add(0.5, 0, 0.5));
    }

    private static boolean safeGround(Block ground) {
        var type = ground.getType();
        return type.isSolid() && !ground.isLiquid() && !UNSAFE.contains(type);
    }

    /** Leaves nothing on disk; the next start generates anew anyway. */
    public void discard() {
        world.getPlayers().forEach(player -> player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation()));
        world.removePluginChunkTickets(plugin);
        Bukkit.unloadWorld(world, false);
    }

    private static Path folder() {
        var level = Bukkit.getWorlds().getFirst().getName();
        return Bukkit.getWorldContainer().toPath().resolve(level).resolve("dimensions")
                .resolve(KEY.getNamespace()).resolve(KEY.getKey());
    }

    private static void deleteRecursively(Path path) {
        if (Files.notExists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            for (var each : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(each);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete the last EnderGames world " + path, e);
        }
    }
}
