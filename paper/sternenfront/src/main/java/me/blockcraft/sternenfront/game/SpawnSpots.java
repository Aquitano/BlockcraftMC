package me.blockcraft.sternenfront.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

/** Where a deploying fighter lands within a spawn, and where the dropship sets down beside the priority post. */
final class SpawnSpots {

    private static final int DROPSHIP_SPREAD = 2;
    /** An enemy farther off than this makes a spot no safer; nearer ones count in steps of {@link #SAFETY_STEP}. */
    private static final double SAFE_DISTANCE = 24;
    private static final double SAFETY_STEP = 4;
    /** Nobody within this many blocks leaves a spot free. */
    private static final double ROOM = 3;
    /** A spot just handed out stays taken this long, through a lander's descent, so fighters deploying together spread. */
    private static final int CLAIM_TICKS = 60;

    private record Claim(Location spot, int tick) { }

    private final SternenfrontGame game;
    private final List<Claim> claims = new ArrayList<>();

    SpawnSpots(SternenfrontGame game) {
        this.game = game;
    }

    static boolean hasClearSpawn(List<Location> spots, List<Location> enemies, double clearance) {
        return spots.stream().anyMatch(spot -> enemies.stream().filter(enemy -> Objects.equals(enemy.getWorld(), spot.getWorld()))
                .noneMatch(enemy -> enemy.distanceSquared(spot) < clearance * clearance));
    }

    Location safest(List<Location> spots, Faction faction) {
        int now = Bukkit.getCurrentTick();
        claims.removeIf(claim -> now - claim.tick() > CLAIM_TICKS);
        var field = game.combatants();
        var enemies = field.stream().filter(other -> game.factionOf(other).filter(faction.enemy()::equals).isPresent())
                .map(LivingEntity::getLocation).toList();
        var crowd = Stream.concat(field.stream().map(LivingEntity::getLocation), claims.stream().map(Claim::spot)).toList();
        var spot = safest(spots, enemies, crowd, ThreadLocalRandom.current());
        claims.add(new Claim(spot.clone(), now));
        return spot;
    }

    /**
     * The spot whose nearest enemy is farthest off, then the one with the most room, then any of the equals, so a
     * fighter does not come down on the same spot every life.
     */
    static Location safest(List<Location> spots, List<Location> enemies, List<Location> crowd, RandomGenerator random) {
        var shuffled = new ArrayList<>(spots);
        Collections.shuffle(shuffled, random);
        return shuffled.stream().max(Comparator.comparingDouble((Location spot) ->
                                Math.floor(Math.min(Math.sqrt(nearest(spot, enemies)), SAFE_DISTANCE) / SAFETY_STEP))
                        .thenComparingDouble(spot -> Math.min(nearest(spot, crowd), ROOM * ROOM)))
                .orElseThrow().clone();
    }

    /** The spots lie so close together that the one farthest from an enemy stays the same, which would stack a crowd. */
    Location leastCrowded(List<Location> spots) {
        var field = game.combatants().stream().map(LivingEntity::getLocation).toList();
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

    private static double nearest(Location spot, List<Location> others) {
        return others.stream().filter(at -> at.getWorld().equals(spot.getWorld()))
                .mapToDouble(at -> at.distanceSquared(spot)).min().orElse(Double.MAX_VALUE);
    }
}
