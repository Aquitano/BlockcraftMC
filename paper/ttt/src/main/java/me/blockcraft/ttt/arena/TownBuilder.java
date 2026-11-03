package me.blockcraft.ttt.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Optional;

import static me.blockcraft.ttt.arena.Site.GROUND;

/**
 * Schattenbach, the abbey village TTT builds into an empty world at every start. The abbey stands on a terrace in the
 * north-west: church and bell tower, the crypt below, the cloister, the graveyard and the herb garden. Below it the
 * village crowds round the market where the round begins, with the town hall and its tester, the inn, smithy,
 * bakery and apothecary; a brook crosses the south past the water mill, and beyond it lie the quarry, the orchard
 * with its chapel and the charcoal burner's clearing, with the farm under the terrace in the north-east. Cellars,
 * a crypt, a miners' tunnel and two traitors' passages run underneath; balconies, a flat roof, the cloister roof,
 * the belfry and a hunting stand above.
 *
 * <p>Blocks are set without physics and joined by {@link Site#connect} once everything stands, and every random
 * choice draws from a fixed seed, so the map is the same at every start.
 */
public final class TownBuilder {

    public static final String NAME = "Schattenbach";
    public static final String KEY = "schattenbach";

    private static final long SEED = 0x5C4A77EBAC4L;
    private static final double BORDER_SIZE = 96;

    private TownBuilder() {
    }

    /** Builds the map into {@code world}, which must be empty, and draws the world border round it. */
    public static Town build(World world) {
        var site = new Site(world, SEED);
        var terrain = new Terrain(site);
        terrain.build();
        var abbey = new Abbey(site);
        abbey.build();
        var village = new Village(site);
        village.build();
        var mill = new Mill(site);
        mill.build();
        var outskirts = new Outskirts(site);
        outskirts.build();
        terrain.forest();
        terrain.decorate();
        site.connect();

        site.chore(new Chore.Fetch("grain", outskirts.grainSacks(), mill.grainBin(), Material.WHEAT, false));
        site.chore(new Chore.Fetch("letter", village.letterDesk(), abbey.abbotsDesk(), Material.PAPER, false));
        site.chore(new Chore.Fetch("herbs", abbey.herbBasket(), village.kettle(), Material.SHORT_GRASS, false));
        site.chore(new Chore.Fetch("stone", outskirts.stonecutter(), abbey.brokenWall(), Material.COBBLESTONE, false));
        site.chore(new Chore.Fetch("horseshoe", village.anvil(), outskirts.stableGate(), Material.IRON_INGOT, false));

        world.getWorldBorder().setCenter(0.5, 0.5);
        world.getWorldBorder().setSize(BORDER_SIZE);
        var tasks = new TaskLayout(site.chores(), site.doors(), site.lights(), mill.generator());
        var spectator = new Location(world, 0.5, GROUND + 32, 40.5, 180, 35);
        return new Town(NAME, world, village.spawns(), village.tester(), spectator, site.traitorDoors(), Optional.of(tasks));
    }
}
