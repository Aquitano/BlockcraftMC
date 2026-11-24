package me.blockcraft.ttt.arena;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Lightable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The wooden doors (each by its lower half) and the lights of an imported map, found inside its world border because
 * a hand-kept list of hundreds of blocks would fall behind every edit of the map. A light is a torch or lantern of
 * any kind, which a blackout takes away, or a lit block that can go unlit.
 */
record Fittings(List<Block> doors, List<Block> lights) {

    private static final int SECTION = 16;
    private static final Set<Material> LIGHTABLE = materials(type -> type.createBlockData() instanceof Lightable);
    private static final Set<Material> LAMPS = materials(type -> !LIGHTABLE.contains(type)
            && (type.name().endsWith("TORCH") || type.name().endsWith("LANTERN"))
            && type != Material.SEA_LANTERN && type != Material.JACK_O_LANTERN);

    private static Set<Material> materials(Predicate<Material> test) {
        var found = EnumSet.noneOf(Material.class);
        Arrays.stream(Material.values()).filter(type -> type.isBlock() && !type.isLegacy() && test.test(type)).forEach(found::add);
        return found;
    }

    static Fittings inside(World world, TownMap.Border border) {
        int minX = (int) Math.floor(border.x() - border.size() / 2);
        int maxX = (int) Math.ceil(border.x() + border.size() / 2);
        int minZ = (int) Math.floor(border.z() - border.size() / 2);
        int maxZ = (int) Math.ceil(border.z() + border.size() / 2);
        var doors = new ArrayList<Block>();
        var lights = new ArrayList<Block>();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                var snapshot = world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false);
                for (int section = world.getMinHeight() / SECTION; section < world.getMaxHeight() / SECTION; section++) {
                    if (snapshot.isSectionEmpty(section - world.getMinHeight() / SECTION)) {
                        continue;
                    }
                    for (int y = section * SECTION; y < (section + 1) * SECTION; y++) {
                        for (int x = 0; x < SECTION; x++) {
                            for (int z = 0; z < SECTION; z++) {
                                int blockX = chunkX * SECTION + x;
                                int blockZ = chunkZ * SECTION + z;
                                if (blockX < minX || blockX > maxX || blockZ < minZ || blockZ > maxZ) {
                                    continue;
                                }
                                var type = snapshot.getBlockType(x, y, z);
                                if (Tag.WOODEN_DOORS.isTagged(type)) {
                                    if (snapshot.getBlockData(x, y, z) instanceof Bisected door && door.getHalf() == Bisected.Half.BOTTOM) {
                                        doors.add(world.getBlockAt(blockX, y, blockZ));
                                    }
                                } else if (LAMPS.contains(type)
                                        || LIGHTABLE.contains(type) && snapshot.getBlockData(x, y, z) instanceof Lightable lit && lit.isLit()) {
                                    lights.add(world.getBlockAt(blockX, y, blockZ));
                                }
                            }
                        }
                    }
                }
            }
        }
        return new Fittings(List.copyOf(doors), List.copyOf(lights));
    }
}
