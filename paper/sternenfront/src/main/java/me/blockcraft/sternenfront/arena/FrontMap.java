package me.blockcraft.sternenfront.arena;

import me.blockcraft.sternenfront.game.Faction;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.Map;

/**
 * A built map, as the round needs it: the box players fight in (the world border and the replay's play area), each
 * faction's headquarters, the command posts in letter order (A first), where the relic blade floats, the spot above
 * the map that dead players watch from while they choose where to deploy, the lobby and spectator spawns, and where
 * the map hides its secrets.
 */
public record FrontMap(String name, World world, BoundingBox playArea, Map<Faction, Headquarters> headquarters,
                       List<PostSite> posts, Location relic, Location overview, Location lobbySpawn,
                       Location spectatorSpawn, SecretSpots secrets, List<ControlSite> controls) {

    /** Where a faction deploys at its base, and the zone around it where enemies are hurt. */
    public record Headquarters(List<Location> spawns, BoundingBox zone) {
    }

    public record Route(List<Location> approach, Location cover, Location peek, Location retreat, Location capture) { }

    public enum Control { VENT, BULKHEAD, SHUTTERS }

    public record ControlSite(Control control, List<Block> switches, List<Block> panels, Location center) { }

    /**
     * A command post. {@code key} names it in messages.yml; its zone reaches {@code radius} blocks round
     * {@code center}, from {@link #BELOW} blocks below it to {@link #ABOVE} above. Its owner deploys at
     * {@code spawns}. {@code indicators} are the dyed blocks (concrete, wool, carpet, terracotta, stained glass) that
     * the round recolours to show the owner, among them the glass over the post's beacon, which tints its beam.
     * {@code label} is where the post's name floats.
     */
    public record PostSite(String key, Location center, double radius, List<Location> spawns, List<Block> indicators,
                           Location label, Map<Faction, List<Route>> routes) {

        public static final double BELOW = 2;
        public static final double ABOVE = 5;

        public boolean contains(Location location) {
            if (!location.getWorld().equals(center.getWorld())) {
                return false;
            }
            double dx = location.getX() - center.getX();
            double dz = location.getZ() - center.getZ();
            double dy = location.getY() - center.getY();
            return dx * dx + dz * dz <= radius * radius && dy >= -BELOW && dy <= ABOVE;
        }
    }
}
