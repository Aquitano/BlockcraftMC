package me.blockcraft.lobby.activity;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.config.LobbyLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A little of each game in the air round its figure: portal sparks by EnderGames, embers by BedWars, as
 * {@code ambience.particles} names them. Each player sees only the areas within {@code radius} of them, and nothing
 * while their effects setting is off; the particles are sent to that player alone.
 */
public final class AreaAmbience implements AutoCloseable {

    private static final int COUNT = 3;
    private static final double SPREAD = 1.2;
    private static final double SPEED = 0.01;
    private static final double HEIGHT = 1.2;

    private final Supplier<Map<LobbyLocation, Location>> places;
    private final Map<LobbyLocation, Particle> particles;
    private final double radius;
    private final BukkitTask task;

    private AreaAmbience(Plugin plugin, Supplier<Map<LobbyLocation, Location>> places, Map<LobbyLocation, Particle> particles,
                         double radius, long ticks) {
        this.places = places;
        this.particles = particles;
        this.radius = radius;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
    }

    /** {@code places} are where the game figures stand; nothing runs when {@code ambience.enabled} is off. */
    public static Optional<AreaAmbience> start(Plugin plugin, Supplier<Map<LobbyLocation, Location>> places,
                                               Map<String, Object> config) {
        if (Boolean.FALSE.equals(config.get("enabled"))) {
            return Optional.empty();
        }
        var particles = new EnumMap<LobbyLocation, Particle>(LobbyLocation.class);
        if (config.get("particles") instanceof Map<?, ?> listed) {
            listed.forEach((area, name) -> LobbyLocation.byKey(String.valueOf(area)).flatMap(location -> particle(String.valueOf(name))
                    .map(particle -> particles.put(location, particle))));
        }
        double radius = Math.clamp(config.get("radius") instanceof Number number ? number.doubleValue() : 32.0, 4.0, 96.0);
        long ticks = Math.clamp(config.get("ticks") instanceof Number number ? number.longValue() : 15L, 5L, 200L);
        return Optional.of(new AreaAmbience(plugin, places, particles, radius, ticks));
    }

    /** Only particles without extra data, so a name in config.yml is all it takes. */
    private static Optional<Particle> particle(String name) {
        try {
            var particle = Particle.valueOf(name.toUpperCase(Locale.ROOT));
            return particle.getDataType() == Void.class ? Optional.of(particle) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void tick() {
        var settings = BlockcraftApi.get().settings();
        var where = places.get();
        double radiusSquared = radius * radius;
        for (var player : Bukkit.getOnlinePlayers()) {
            if (!settings.enabled(player, PlayerSetting.EFFECTS)) {
                continue;
            }
            var at = player.getLocation();
            particles.forEach((area, particle) -> {
                var place = where.get(area);
                if (place != null && place.getWorld().equals(at.getWorld()) && place.distanceSquared(at) <= radiusSquared) {
                    player.spawnParticle(particle, place.clone().add(0, HEIGHT, 0), COUNT, SPREAD, SPREAD, SPREAD, SPEED);
                }
            });
        }
    }

    @Override
    public void close() {
        task.cancel();
    }
}
