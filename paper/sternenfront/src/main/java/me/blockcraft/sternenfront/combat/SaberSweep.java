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
    private final boolean reverse;
    private int emitted;

    SaberSweep(Location origin, Color colour, double damage, double range, double angle, int ticks, long started, boolean reverse) {
        this.origin = origin.clone();
        this.colour = colour;
        this.damage = damage;
        this.ticks = ticks;
        this.started = started;
        this.reverse = reverse;
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

    /** Where the sweep is for the body: it starts on the hero's right unless reversed. */
    Stance.Swing stance(long tick) {
        return new Stance.Swing(reverse, (int) Math.clamp(tick - started, 0, ticks), ticks);
    }

    boolean contact(UUID target) {
        return hit.add(target);
    }

    boolean finished() {
        return emitted == rays.size();
    }
}
