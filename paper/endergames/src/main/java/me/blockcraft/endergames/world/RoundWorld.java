package me.blockcraft.endergames.world;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.endergames.config.EnderGamesConfig.WorldSettings;
import me.blockcraft.endergames.config.MapSeed;
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

/**
 * The round's world: a vanilla overworld generated at every server start from one of the configured seeds, picked
 * at random. While the lobby waits it puts the border around that seed's centre and loads every chunk inside, so
 * the round starts without generating terrain; only then is it {@link #ready()}.
 */
public final class RoundWorld {

    private static final NamespacedKey KEY = new NamespacedKey("endergames", "runde");
    private static final int LOADS_IN_FLIGHT = 16;
    private static final long DAY = 1000L;
    private static final int SPECTATOR_HEIGHT = 20;
    private static final int SPOT_SEARCH_RADIUS = 12;
    private static final int SPOT_SEARCH_STEP = 3;
    private static final int RING_STEP_INWARD = 8;
    private static final Set<Material> UNSAFE = Set.of(Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH,
            Material.POINTED_DRIPSTONE, Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE);

    private final Plugin plugin;
    private final World world;
    private final WorldSettings settings;
    private final Random random = new Random();
    private final int centerX;
    private final int centerZ;
    private boolean ready;

    private RoundWorld(Plugin plugin, World world, WorldSettings settings, MapSeed seed) {
        this.plugin = plugin;
        this.world = world;
        this.settings = settings;
        this.centerX = seed.x();
        this.centerZ = seed.z();
    }

    /** Throws the last start's world away and generates the next from a random seed of the pool; the dimension must not be loaded yet. */
    public static RoundWorld create(Plugin plugin, WorldSettings settings) {
        var seed = settings.seeds().get(new Random().nextInt(settings.seeds().size()));
        plugin.getLogger().info("EnderGames world from seed " + seed.seed() + ", centred on " + seed.x() + ", " + seed.z());
        var stale = Bukkit.getWorld(KEY);
        if (stale != null) {
            Bukkit.unloadWorld(stale, false);
        }
        deleteRecursively(folder());
        var world = Objects.requireNonNull(WorldCreator.ofKey(KEY)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .seed(seed.seed())
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
        return new RoundWorld(plugin, world, settings, seed);
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

    /** Sets the border round the seed's centre and loads its chunks; {@code onReady} runs on the main thread. */
    public void prepare(Runnable onReady) {
        var border = world.getWorldBorder();
        border.setCenter(centerX + 0.5, centerZ + 0.5);
        border.setSize(settings.radius() * 2.0);
        preload(chunksInside(), () -> {
            world.setSpawnLocation(spectatorSpawn());
            ready = true;
            plugin.getLogger().info("EnderGames world is loaded");
            onReady.run();
        });
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
