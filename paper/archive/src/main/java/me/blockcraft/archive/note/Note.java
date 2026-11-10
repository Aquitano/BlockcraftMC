package me.blockcraft.archive.note;

import me.blockcraft.api.world.Point;
import me.blockcraft.common.player.PlayerReference;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;

/**
 * What a staff member left for later visitors of an exhibit. {@code at} is where the author stood and which way they
 * looked; the note hangs a little in front of their eyes, so a visitor sent to {@code at} reads it straight ahead.
 */
public record Note(long id, String exhibit, Point at, PlayerReference author, String text, Instant createdAt) {

    private static final double EYE_HEIGHT = 1.6;
    private static final double AHEAD = 1.5;

    public Location display(World world) {
        var standing = at.in(world);
        var ahead = standing.getDirection().setY(0);
        if (ahead.lengthSquared() > 0) {
            ahead.normalize().multiply(AHEAD);
        }
        return standing.add(ahead).add(0, EYE_HEIGHT, 0);
    }
}
