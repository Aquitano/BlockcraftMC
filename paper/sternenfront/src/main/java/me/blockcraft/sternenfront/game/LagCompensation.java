package me.blockcraft.sternenfront.game;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Judges a player's shots against the fighters where that player's screen showed them: a round trip of ping and the
 * client's smoothing of other entities behind the server. The {@code combat.lag-compensation} section of config.yml.
 */
public final class LagCompensation {

    private static final double MS_PER_TICK = 50;

    public record Settings(boolean enabled, int maxMs, int interpolationMs) {

        public static Settings from(ConfigurationSection config) {
            return new Settings(config.getBoolean("enabled"), Math.clamp(config.getInt("max-ms"), 0, 1000),
                    Math.clamp(config.getInt("interpolation-ms"), 0, 500));
        }

        public double rewindTicks(int pingMs) {
            return enabled ? Math.clamp((long) pingMs + interpolationMs, 0, maxMs) / MS_PER_TICK : 0;
        }

        int ticksKept() {
            return (int) Math.ceil(maxMs / MS_PER_TICK) + 2;
        }
    }

    public record Hit(LivingEntity victim, Vector at, double eyeY) { }

    private final Settings settings;
    private final Map<LivingEntity, PoseHistory> tracks = new HashMap<>();

    public LagCompensation(Settings settings) {
        this.settings = settings;
    }

    public void record(long tick, List<LivingEntity> fighters) {
        if (!settings.enabled()) {
            return;
        }
        tracks.keySet().retainAll(new HashSet<>(fighters));
        for (var fighter : fighters) {
            var pose = new PoseHistory.Pose(fighter.getBoundingBox(), fighter.getEyeLocation().getY());
            var track = tracks.get(fighter);
            if (track == null) {
                tracks.put(fighter, new PoseHistory(settings.ticksKept(), tick, pose));
            } else {
                track.record(tick, pose);
            }
        }
    }

    /** Bots aim on the server and see the present. */
    public double rewind(LivingEntity shooter) {
        return shooter instanceof Player player ? settings.rewindTicks(player.getPing()) : 0;
    }

    /** The nearest fighter the ray meets as they stood at {@code tick}. */
    public Optional<Hit> trace(Vector start, Vector direction, double reach, double margin, double tick,
                               Predicate<LivingEntity> hittable) {
        Hit nearest = null;
        double distance = Double.MAX_VALUE;
        for (var track : tracks.entrySet()) {
            if (!hittable.test(track.getKey())) {
                continue;
            }
            var pose = track.getValue().at(tick);
            var at = pose.hit(start, direction, reach, margin);
            if (at.isPresent() && at.get().distanceSquared(start) < distance) {
                distance = at.get().distanceSquared(start);
                nearest = new Hit(track.getKey(), at.get(), pose.eyeY());
            }
        }
        return Optional.ofNullable(nearest);
    }

    public void clear() {
        tracks.clear();
    }
}
