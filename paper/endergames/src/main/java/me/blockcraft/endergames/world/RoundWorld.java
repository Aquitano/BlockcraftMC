package me.blockcraft.endergames.world;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.endergames.config.EnderGamesConfig.WorldSettings;
import me.blockcraft.endergames.config.MapSeed;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A round's world: a vanilla overworld generated for every round from one of the configured seeds, picked at random.
 * Before the round starts it puts the border around that seed's centre and loads every chunk inside, so the round
 * starts without generating terrain; only then is it {@link #ready()}.
 */
public final class RoundWorld {

    private static final int LOADS_IN_FLIGHT = 16;
    private static final long DAY = 1000L;
    private static final Set<Material> UNSAFE = Set.of(Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH,
            Material.POINTED_DRIPSTONE, Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE);

    private final Plugin plugin;
    private final World world;
    private final WorldSettings settings;
    private final Random random = new Random();
    private final int centerX;
    private final int centerZ;
    private final CompletableFuture<Void> loaded = new CompletableFuture<>();
    private Optional<StartIsland> startIsland = Optional.empty();

    private RoundWorld(Plugin plugin, World world, WorldSettings settings, MapSeed seed) {
        this.plugin = plugin;
        this.world = world;
        this.settings = settings;
        this.centerX = seed.x();
        this.centerZ = seed.z();
    }

    /** Throws away what is left of the dimension {@code key} and generates it anew from a random seed of the pool. */
    static RoundWorld create(Plugin plugin, WorldSettings settings, NamespacedKey key) {
        var seed = settings.seeds().get(new Random().nextInt(settings.seeds().size()));
        plugin.getSLF4JLogger().info("EnderGames world {} from seed {}, centred on {}, {}", key, seed.seed(), seed.x(), seed.z());
        Worlds.discard(key);
        var world = Objects.requireNonNull(WorldCreator.ofKey(key)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .seed(seed.seed())
                .generateStructures(true)
                .createWorld(), "Could not create the EnderGames world");
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
        return loaded.isDone();
    }

    /** Runs {@code action} on the main thread once the world is ready, unless {@code holder} was disabled by then. */
    public void whenReady(Plugin holder, Runnable action) {
        loaded.thenRun(() -> {
            if (holder.isEnabled()) {
                action.run();
            }
        });
    }

    public boolean contains(World other) {
        return world.equals(other);
    }

    /**
     * Sets the border round the seed's centre, loads its chunks and builds the start island with {@code startChests}
     * chests over them.
     */
    void prepare(int startChests) {
        var border = world.getWorldBorder();
        border.setCenter(centerX + 0.5, centerZ + 0.5);
        border.setSize(settings.radius() * 2.0);
        preload(chunksInside(), () -> {
            startIsland = Optional.of(StartIsland.build(world, centerX, centerZ, startChests));
            world.setSpawnLocation(spectatorSpawn());
            plugin.getSLF4JLogger().info("EnderGames world {} is loaded", world.getKey());
            loaded.complete(null);
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

    /** Above the start island while it stands, then high over the centre. */
    public Location spectatorSpawn() {
        return startIsland.map(StartIsland::spectatorSpawn).orElseGet(() -> center().add(0, StartIsland.HEIGHT_ABOVE_GROUND, 0));
    }

    public Optional<StartIsland> startIsland() {
        return startIsland;
    }

    /** The island crumbles once the protection time is over; nothing the round places may land on it before. */
    public void crumbleStartIsland() {
        startIsland.ifPresent(StartIsland::crumble);
        startIsland = Optional.empty();
    }

    public double borderRadius() {
        return world.getWorldBorder().getSize() / 2;
    }

    /** A random safe place inside the border, for an ender chest. */
    public Optional<Location> randomSpot(double insideRadius) {
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = Math.sqrt(random.nextDouble()) * insideRadius;
            int x = (int) Math.floor(centerX + Math.cos(angle) * distance);
            int z = (int) Math.floor(centerZ + Math.sin(angle) * distance);
            if (startIsland.filter(island -> island.covers(x, z)).isPresent()) {
                continue;
            }
            var spot = surface(x, z);
            if (spot.isPresent()) {
                return spot;
            }
        }
        return Optional.empty();
    }

    /** The block above the ground at x/z if a player can stand there: solid, harmless ground and two free blocks. */
    private Optional<Location> surface(int x, int z) {
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

    /** Lets the chunks nobody stands near unload, once the round in this world is over. */
    public void release() {
        world.removePluginChunkTickets(plugin);
    }

    /** Unloads the world, which nobody may be in, and leaves nothing of it on disk. */
    void discard() {
        Worlds.discard(world.getKey());
    }
}
