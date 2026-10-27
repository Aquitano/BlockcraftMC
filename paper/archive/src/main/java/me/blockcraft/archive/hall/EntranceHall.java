package me.blockcraft.archive.hall;

import me.blockcraft.api.world.Worlds;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

/**
 * Where everyone arrives: a small reading room of stone, bookshelves and lamplight, built anew into an empty world at
 * every start, so nothing of the last run is left in it.
 */
public final class EntranceHall {

    private static final NamespacedKey KEY = new NamespacedKey("archive", "entrance");
    private static final int FLOOR = 64;
    private static final int HALF = 8;
    private static final int CEILING = FLOOR + 7;
    private static final int PILLAR_SPACING = 4;
    private static final int WELCOME_LINE_WIDTH = 220;

    private final Location spawn;

    private EntranceHall(Location spawn) {
        this.spawn = spawn;
    }

    public static EntranceHall build(Component welcome) {
        Worlds.discard(KEY);
        var world = Worlds.createArena(KEY);
        floor(world);
        walls(world);
        ceiling(world);
        world.getBlockAt(0, FLOOR + 1, 0).setType(Material.LECTERN, false);
        var spawn = new Location(world, 0.5, FLOOR + 1, -HALF + 3.5, 0.0f, 0.0f);
        world.setSpawnLocation(spawn);
        world.spawn(new Location(world, 0.5, FLOOR + 3.5, HALF - 1.5), TextDisplay.class, display -> {
            display.text(welcome);
            display.setBillboard(Display.Billboard.CENTER);
            display.setLineWidth(WELCOME_LINE_WIDTH);
            display.setPersistent(false);
            display.setShadowed(true);
        });
        return new EntranceHall(spawn);
    }

    public Location spawn() {
        return spawn.clone();
    }

    private static void floor(World world) {
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                Material floor;
                if (Math.abs(x) == HALF || Math.abs(z) == HALF) {
                    floor = Material.POLISHED_DEEPSLATE;
                } else if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    floor = Material.CHISELED_STONE_BRICKS;
                } else {
                    floor = (x + z) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS;
                }
                world.getBlockAt(x, FLOOR, z).setType(floor, false);
            }
        }
    }

    /** Deepslate pillars every few blocks, stone at the foot and the top, shelves of old books between. */
    private static void walls(World world) {
        for (int along = -HALF; along <= HALF; along++) {
            boolean pillar = Math.floorMod(along, PILLAR_SPACING) == 0;
            for (int y = FLOOR + 1; y < CEILING; y++) {
                Material wall;
                if (pillar) {
                    wall = Material.POLISHED_DEEPSLATE;
                } else if (y == FLOOR + 1 || y >= CEILING - 2) {
                    wall = Material.STONE_BRICKS;
                } else {
                    wall = Material.BOOKSHELF;
                }
                world.getBlockAt(along, y, -HALF).setType(wall, false);
                world.getBlockAt(along, y, HALF).setType(wall, false);
                world.getBlockAt(-HALF, y, along).setType(wall, false);
                world.getBlockAt(HALF, y, along).setType(wall, false);
            }
        }
    }

    private static void ceiling(World world) {
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                boolean lamp = Math.abs(x) < HALF && Math.abs(z) < HALF
                        && Math.floorMod(x, PILLAR_SPACING) == 2 && Math.floorMod(z, PILLAR_SPACING) == 2;
                world.getBlockAt(x, CEILING, z).setType(lamp ? Material.SEA_LANTERN : Material.DARK_OAK_PLANKS, false);
            }
        }
    }
}
