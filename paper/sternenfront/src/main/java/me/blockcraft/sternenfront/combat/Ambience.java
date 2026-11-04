package me.blockcraft.sternenfront.combat;

import me.blockcraft.sternenfront.arena.FrontMap;
import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The map going on by itself, for whoever is near: thin smoke from the torn cruiser with the odd spark, steam from
 * the refinery's pressure tanks. The refinery's stacks need nothing here, they carry real signal fires.
 */
final class Ambience {

    private static final String WRECK = "wrack";
    private static final String REFINERY = "raffinerie";
    private static final int[][] WRECK_COLUMNS = {{2, -8}, {-2, 8}, {-5, 26}, {3, -18}};
    private static final int[][] STEAM_COLUMNS = {{-11, 8}, {11, 8}};
    private static final double ABOVE_TOP = 1.1;
    private static final double SMOKE_SPEED = 0.05;
    private static final int STEAM_EVERY = 4;
    private static final int SPARK_ODDS = 5;

    private final PresentationConfig.Ambient config;
    private final List<Location> smoke;
    private final List<Location> steam;
    private long pulse;

    Ambience(FrontMap map, PresentationConfig.Ambient config) {
        this.config = config;
        smoke = columns(map, WRECK, WRECK_COLUMNS);
        steam = columns(map, REFINERY, STEAM_COLUMNS);
    }

    private static List<Location> columns(FrontMap map, String post, int[][] offsets) {
        return map.posts().stream().filter(site -> site.key().equals(post)).findFirst().map(site -> {
            var world = site.center().getWorld();
            return Arrays.stream(offsets).map(offset -> {
                int x = site.center().getBlockX() + offset[0];
                int z = site.center().getBlockZ() + offset[1];
                return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + ABOVE_TOP, z + 0.5);
            }).toList();
        }).orElse(List.of());
    }

    void tick(long tick) {
        if (tick % config.intervalTicks() != 0) {
            return;
        }
        pulse++;
        smoke.forEach(this::smoke);
        if (pulse % STEAM_EVERY == 0) {
            steam.forEach(this::steam);
        }
        var random = ThreadLocalRandom.current();
        if (!smoke.isEmpty() && random.nextInt(SPARK_ODDS) == 0) {
            spark(smoke.get(random.nextInt(smoke.size())));
        }
    }

    private void smoke(Location at) {
        spawn(Particle.CAMPFIRE_COSY_SMOKE, at, 0, 0, 1, 0, SMOKE_SPEED, true);
    }

    private void steam(Location at) {
        spawn(Particle.CLOUD, at, 4, 0.15, 0.1, 0.15, 0.02, false);
    }

    private void spark(Location at) {
        spawn(Particle.ELECTRIC_SPARK, at, 6, 0.3, 0.2, 0.3, 0.06, false);
    }

    private void spawn(Particle particle, Location at, int count, double x, double y, double z, double speed, boolean far) {
        at.getWorld().spawnParticle(particle, List.copyOf(at.getNearbyPlayers(config.range())), null,
                at.getX(), at.getY(), at.getZ(), count, x, y, z, speed, null, far);
    }
}
