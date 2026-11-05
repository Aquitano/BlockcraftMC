package me.blockcraft.sternenfront.bot;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

final class BotCover {

    private BotCover() { }

    static boolean arrived(Location feet, Location eye, Location destination, Location threat, boolean peeking) {
        return feet.distanceSquared(destination) <= 0.36 && (peeking
                ? !blocked(eye, threat) : shields(feet, eye.getY() - feet.getY(), threat));
    }

    static boolean shields(Location feet, double eyeHeight, Location threat) {
        return blocked(threat, feet.clone().add(0, eyeHeight, 0))
                && blocked(threat, feet.clone().add(0, 0.9, 0));
    }

    static boolean blocked(Location from, Location to) {
        var direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        return distance > 0.01 && from.getWorld().rayTraceBlocks(from, direction.normalize(), distance,
                FluidCollisionMode.NEVER, true) != null;
    }

    static Optional<Location> find(Mob mob, Location around, List<Location> preferred, double radius,
                                   Predicate<Location> suitable) {
        var candidates = new ArrayList<>(preferred);
        candidates.add(around);
        for (double distance = 2; distance <= radius; distance += 2) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = direction * Math.PI / 4;
                var at = around.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                at.setX(at.getBlockX() + 0.5);
                at.setZ(at.getBlockZ() + 0.5);
                candidates.add(at);
            }
        }
        var here = mob.getLocation();
        return candidates.stream().filter(at -> around.distanceSquared(at) <= radius * radius)
                .filter(BotCover::standingSpace)
                .filter(suitable)
                .sorted(Comparator.comparingDouble(here::distanceSquared))
                .filter(at -> {
                    var path = mob.getPathfinder().findPath(at);
                    return path != null && path.canReachFinalPoint();
                }).findFirst();
    }

    private static boolean standingSpace(Location feet) {
        return feet.getBlock().isPassable() && feet.clone().add(0, 1, 0).getBlock().isPassable()
                && feet.clone().subtract(0, 0.1, 0).getBlock().getType().isSolid();
    }
}
