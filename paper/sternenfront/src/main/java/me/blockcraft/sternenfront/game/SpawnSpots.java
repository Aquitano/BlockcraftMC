package me.blockcraft.sternenfront.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Where a deploying fighter lands within a spawn, and where the dropship sets down beside the priority post. */
final class SpawnSpots {

    private static final int DROPSHIP_SPREAD = 2;

    private final SternenfrontGame game;

    SpawnSpots(SternenfrontGame game) {
        this.game = game;
    }

    static boolean hasClearSpawn(List<Location> spots, List<Location> enemies, double clearance) {
        return spots.stream().anyMatch(spot -> enemies.stream().filter(enemy -> Objects.equals(enemy.getWorld(), spot.getWorld()))
                .noneMatch(enemy -> enemy.distanceSquared(spot) < clearance * clearance));
    }

    /** The spot farthest from the nearest enemy, and among equals the one farthest from anyone, so a crowd spreads. */
    Location farthestFromEnemies(List<Location> spots, Faction faction) {
        var field = game.combatants();
        var enemies = field.stream().filter(other -> game.factionOf(other).filter(faction.enemy()::equals).isPresent()).toList();
        return spots.stream().max(Comparator.comparingDouble((Location spot) -> nearest(spot, enemies))
                        .thenComparingDouble(spot -> nearest(spot, field)))
                .orElseThrow().clone();
    }

    /** The spots lie so close together that the one farthest from an enemy stays the same, which would stack a crowd. */
    Location leastCrowded(List<Location> spots) {
        var field = game.combatants();
        return spots.stream().max(Comparator.comparingDouble(spot -> nearest(spot, field))).orElseThrow().clone();
    }

    List<Location> dropshipSpots(PriorityPost assault) {
        var spots = new ArrayList<Location>();
        for (int across = -DROPSHIP_SPREAD; across <= DROPSHIP_SPREAD; across += DROPSHIP_SPREAD) {
            for (int along = -DROPSHIP_SPREAD; along <= DROPSHIP_SPREAD; along += DROPSHIP_SPREAD) {
                spots.add(dropshipSpot(assault, across, along));
            }
        }
        return spots;
    }

    /** On the ground on the trailing faction's side of the post, looking at it. */
    Location dropshipSpot(PriorityPost assault, int across, int along) {
        var map = game.map();
        var post = map.posts().get(assault.post()).center();
        int distance = game.config().acts().dropshipDistance();
        int x = post.getBlockX() + (assault.trailing() == Faction.KOLONIEN ? -distance : distance) + across;
        int z = post.getBlockZ() + along;
        var spot = new Location(map.world(), x + 0.5, standingHeight(x, z, post.getY()), z + 0.5);
        return spot.setDirection(post.toVector().subtract(spot.toVector()).setY(0));
    }

    /** Above the highest solid block; barriers only fence the map's cliffs and hidden places in. */
    private int standingHeight(int x, int z, double fallback) {
        var map = game.map();
        for (int y = (int) map.playArea().getMaxY() - 1; y >= map.playArea().getMinY(); y--) {
            var type = map.world().getBlockAt(x, y, z).getType();
            if (type.isSolid() && type != Material.BARRIER) {
                return y + 1;
            }
        }
        return (int) fallback;
    }

    private static double nearest(Location spot, List<LivingEntity> players) {
        return players.stream().map(LivingEntity::getLocation).filter(at -> at.getWorld().equals(spot.getWorld()))
                .mapToDouble(at -> at.distanceSquared(spot)).min().orElse(Double.MAX_VALUE);
    }
}
