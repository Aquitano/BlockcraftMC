package me.blockcraft.lobby.activity;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Small effects at fixed spots of the map: fireflies over the ponds, mist where the waterfall lands. Each effect of
 * {@code ambient.effects} shows count of its particle, spread blocks round each of its spots, every ticks, to players
 * within radius who have effects on. Its spots are the map's {@code ambient-<effect>-<n>}, up to {@code spots} of them.
 */
public final class AmbientSpots implements AutoCloseable {

    private static final long PERIOD_TICKS = 5L;

    private record Effect(String name, Particle particle, int count, double spread, double speed, long every, int spots) {
    }

    private final Locations<String> places;
    private final List<Effect> effects;
    private final double radius;
    private final BukkitTask task;
    private long ticks;

    public AmbientSpots(Plugin plugin, Locations<String> places, Map<String, Object> config) {
        this.places = places;
        this.effects = effects(config);
        this.radius = Math.clamp(number(config, "radius", 40), 4, 96);
        this.task = Boolean.FALSE.equals(config.get("enabled")) ? null
                : Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    /** The {@code /lobbyset} keys of every configured effect's spots. */
    public List<String> spots() {
        return effects.stream().flatMap(effect -> IntStream.rangeClosed(1, effect.spots()).mapToObj(n -> spot(effect.name(), n))).toList();
    }

    private static String spot(String effect, int n) {
        return "ambient-" + effect + "-" + n;
    }

    private static List<Effect> effects(Map<String, Object> config) {
        var effects = new ArrayList<Effect>();
        if (config.get("effects") instanceof Map<?, ?> listed) {
            listed.forEach((name, value) -> {
                if (value instanceof Map<?, ?> effect) {
                    particle(effect.get("particle")).ifPresent(particle -> effects.add(new Effect(String.valueOf(name), particle,
                            (int) Math.clamp(number(effect, "count", 1), 0, 50), Math.clamp(number(effect, "spread", 0.5), 0, 16),
                            Math.clamp(number(effect, "speed", 0), 0, 2),
                            Math.max(1, Math.round(Math.clamp(number(effect, "ticks", 10), PERIOD_TICKS, 200) / PERIOD_TICKS)),
                            (int) Math.clamp(number(effect, "spots", 4), 0, 50))));
                }
            });
        }
        return List.copyOf(effects);
    }

    /** Only particles without extra data, so a name in config.yml is all it takes. */
    private static Optional<Particle> particle(Object name) {
        try {
            var particle = Particle.valueOf(String.valueOf(name).toUpperCase(Locale.ROOT));
            return particle.getDataType() == Void.class ? Optional.of(particle) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static double number(Map<?, ?> config, String key, double fallback) {
        return config.get(key) instanceof Number number ? number.doubleValue() : fallback;
    }

    private void tick() {
        ticks++;
        var settings = BlockcraftApi.get().settings();
        double radiusSquared = radius * radius;
        for (var effect : effects) {
            if (ticks % effect.every() != 0) {
                continue;
            }
            for (int n = 1; n <= effect.spots(); n++) {
                var at = places.get(spot(effect.name(), n));
                if (at.isEmpty()) {
                    continue;
                }
                for (var player : at.get().getWorld().getPlayers()) {
                    if (player.getLocation().distanceSquared(at.get()) <= radiusSquared && settings.enabled(player, PlayerSetting.EFFECTS)) {
                        show(player, effect, at.get());
                    }
                }
            }
        }
    }

    private static void show(Player player, Effect effect, Location at) {
        player.spawnParticle(effect.particle(), at, effect.count(), effect.spread(), effect.spread() / 2, effect.spread(), effect.speed());
    }

    @Override
    public void close() {
        if (task != null) {
            task.cancel();
        }
    }
}
