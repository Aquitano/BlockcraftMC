package me.blockcraft.lobby.activity;

import me.blockcraft.api.config.Locations;
import me.blockcraft.lobby.ParticleViewers;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.config.ParticleEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Particles in the air at fixed points of the map, sent only to players within radius of a point who have effects on.
 * Each effect of {@code ambient.effects} shows round the map's {@code ambient-<effect>-<n>} spots, up to {@code spots}
 * of them: fireflies over the ponds, mist where the waterfall lands. {@code ambience.particles} puts a little of each
 * game in the air round its figure: portal sparks by EnderGames, embers by BedWars.
 */
public final class Ambience implements AutoCloseable {

    private static final long SPOT_PERIOD_TICKS = 5L;
    private static final double FIGURE_HEIGHT = 1.2;
    private static final int FIGURE_COUNT = 3;
    private static final double FIGURE_SPREAD = 1.2;
    private static final double FIGURE_SPEED = 0.01;

    private record SpotEffect(String name, ParticleEffect particles, long every, int spots) {
    }

    private record Emitter(ParticleEffect particles, long every, double radius, double height, Supplier<Optional<Location>> place) {
    }

    private final List<String> spots;
    private final List<Emitter> emitters = new ArrayList<>();
    private final BukkitTask task;
    private long ticks;

    /** {@code figures} are where the game figures stand; either section's {@code enabled: false} turns off its part. */
    public Ambience(Plugin plugin, Locations<String> places, Map<String, Object> ambient,
                    Supplier<Map<LobbyLocation, Location>> figures, Map<String, Object> ambience) {
        var effects = spotEffects(ambient);
        this.spots = effects.stream().flatMap(Ambience::spots).toList();
        if (enabled(ambient)) {
            double radius = Math.clamp(number(ambient, "radius", 40), 4, 96);
            effects.forEach(effect -> spots(effect).forEach(spot ->
                    emitters.add(new Emitter(effect.particles(), effect.every(), radius, 0, () -> places.get(spot)))));
        }
        if (enabled(ambience) && ambience.get("particles") instanceof Map<?, ?> listed) {
            double radius = Math.clamp(number(ambience, "radius", 32), 4, 96);
            long every = Math.clamp(ambience.get("ticks") instanceof Number number ? number.longValue() : 15L, 5L, 200L);
            listed.forEach((area, name) -> LobbyLocation.byKey(String.valueOf(area)).ifPresent(location -> ParticleEffect.named(name)
                    .map(particle -> new ParticleEffect(particle, FIGURE_COUNT, FIGURE_SPREAD, FIGURE_SPREAD, FIGURE_SPEED))
                    .ifPresent(particles -> emitters.add(new Emitter(particles, every, radius, FIGURE_HEIGHT,
                            () -> Optional.ofNullable(figures.get().get(location)))))));
        }
        this.task = emitters.isEmpty() ? null : Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** The {@code /lobbyset} keys of every configured effect's spots. */
    public List<String> spots() {
        return spots;
    }

    private static Stream<String> spots(SpotEffect effect) {
        return IntStream.rangeClosed(1, effect.spots()).mapToObj(n -> "ambient-" + effect.name() + "-" + n);
    }

    private static List<SpotEffect> spotEffects(Map<String, Object> config) {
        var effects = new ArrayList<SpotEffect>();
        if (config.get("effects") instanceof Map<?, ?> listed) {
            listed.forEach((name, value) -> {
                if (value instanceof Map<?, ?> effect) {
                    ParticleEffect.fromYaml(effect).ifPresent(particles -> effects.add(new SpotEffect(String.valueOf(name), particles,
                            SPOT_PERIOD_TICKS * Math.max(1, Math.round(Math.clamp(number(effect, "ticks", 10), SPOT_PERIOD_TICKS, 200) / SPOT_PERIOD_TICKS)),
                            (int) Math.clamp(number(effect, "spots", 4), 0, 50))));
                }
            });
        }
        return List.copyOf(effects);
    }

    private static boolean enabled(Map<String, Object> config) {
        return !Boolean.FALSE.equals(config.get("enabled"));
    }

    private static double number(Map<?, ?> config, String key, double fallback) {
        return config.get(key) instanceof Number number ? number.doubleValue() : fallback;
    }

    private void tick() {
        ticks++;
        for (var emitter : emitters) {
            if (ticks % emitter.every() == 0) {
                emitter.place().get().ifPresent(at -> {
                    var shown = at.clone().add(0, emitter.height(), 0);
                    ParticleViewers.near(at, emitter.radius()).forEach(player -> emitter.particles().show(player, shown));
                });
            }
        }
    }

    @Override
    public void close() {
        if (task != null) {
            task.cancel();
        }
    }
}
