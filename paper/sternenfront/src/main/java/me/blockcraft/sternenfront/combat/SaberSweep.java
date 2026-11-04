package me.blockcraft.sternenfront.combat;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class SaberSweep {

    private final Location origin;
    private final Color colour;
    private final double damage;
    private final List<Vector> rays;
    private final Set<UUID> hit = new HashSet<>();
    private final long started;
    private final int ticks;
    private int emitted;

    SaberSweep(Location origin, Color colour, double damage, double range, double angle, int ticks, long started, boolean reverse) {
        this.origin = origin.clone();
        this.colour = colour;
        this.damage = damage;
        this.ticks = ticks;
        this.started = started;
        double yaw = Math.toRadians(origin.getYaw());
        rays = CombatArc.rays(origin.getDirection(), new Vector(Math.cos(yaw), 0, Math.sin(yaw)), range, angle, reverse);
    }

    Location origin() {
        return origin.clone();
    }

    Color colour() {
        return colour;
    }

    double damage() {
        return damage;
    }

    Optional<Vector> trailStart() {
        return emitted == 0 ? Optional.empty() : Optional.of(rays.get(emitted - 1).clone());
    }

    List<Vector> advance(long tick) {
        int end = (int) (rays.size() * Math.clamp(tick - started + 1, 0, ticks) / ticks);
        var frame = rays.subList(emitted, Math.max(emitted, end));
        emitted = Math.max(emitted, end);
        return frame;
    }

    boolean contact(UUID target) {
        return hit.add(target);
    }

    boolean finished() {
        return emitted == rays.size();
    }
}
