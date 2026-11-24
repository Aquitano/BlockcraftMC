package me.blockcraft.lobby;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/** Who is sent the lobby's particles: never a player who turned effects off. */
public final class ParticleViewers {

    private ParticleViewers() {
    }

    public static boolean wants(Player player) {
        return BlockcraftApi.get().settings().enabled(player, PlayerSetting.EFFECTS);
    }

    /** Players in {@code at}'s world at most {@code radius} blocks from it who want particles. */
    public static List<Player> near(Location at, double radius) {
        double radiusSquared = radius * radius;
        return at.getWorld().getPlayers().stream()
                .filter(player -> player.getLocation().distanceSquared(at) <= radiusSquared && wants(player))
                .toList();
    }

    /** Players, {@code source} included, within a box {@code radius} blocks round {@code source} who can see it and want particles. */
    public static List<Player> seeing(Player source, double radius) {
        return source.getWorld().getNearbyPlayers(source.getLocation(), radius).stream()
                .filter(viewer -> viewer.canSee(source) && wants(viewer))
                .toList();
    }
}
