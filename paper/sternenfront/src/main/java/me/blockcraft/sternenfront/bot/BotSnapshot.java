package me.blockcraft.sternenfront.bot;

import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

public record BotSnapshot(UUID id, int number, Faction faction, CombatClass role, Motion motion, Decision decision,
                          Path path, BotConfig.Tuning tuning, float heat, boolean venting) {

    public record Point(double x, double y, double z) {
        public static Point of(Vector vector) {
            return vector == null ? null : new Point(vector.getX(), vector.getY(), vector.getZ());
        }

        public static Point of(Location location) {
            return location == null ? null : of(location.toVector());
        }
    }

    public record Motion(Point position, Point velocity, List<Double> bounds, float yaw, float pitch, float bodyYaw,
                         boolean grounded, boolean jumping, boolean inWater, float fallDistance, double health) { }

    public record Decision(String activity, String navigationResult, long navigatedAt, Point destination,
                           int objective, String post, int waypoint, int stuckTicks, Point progress,
                           UUID target, Point targetPosition, boolean lineOfSight, Point aim, Point lastSeen,
                           int fireteam, int guarding, long reactAt, long seenUntil, long pauseUntil, boolean volley,
                           Point idleAim, UUID patient, Point patientPosition, double patientHealth) { }

    public record Path(boolean active, boolean reachable, int nextPoint, List<Point> points) { }
}
