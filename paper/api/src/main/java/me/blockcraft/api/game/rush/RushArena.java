package me.blockcraft.api.game.rush;

import me.blockcraft.api.game.rush.MapPool.ArenaMap;
import me.blockcraft.api.world.Worlds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class RushArena {

    private final World world;
    private ArenaMap map;
    private ArenaLayout layout;
    private final Set<ArenaLayout.Position> placed = new HashSet<>();

    public RushArena(ArenaMap map) {
        this(map, new NamespacedKey("mlgrush", map.key()));
    }

    public RushArena(ArenaMap map, NamespacedKey worldKey) {
        this.map = map;
        var dimensions = map.dimensions();
        layout = new ArenaLayout(dimensions, map.palette(), map.geometry());
        world = Worlds.createArena(worldKey);
        for (int x = layout.minX(); x <= layout.maxX(); x++) {
            for (int z = -layout.radiusZ(); z <= layout.radiusZ(); z++) {
                for (int y = dimensions.floorY(); y <= dimensions.floorY() + dimensions.buildHeight(); y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
        reset();
        world.setSpawnLocation(spawn(0));
    }

    public void selectMap(ArenaMap next) {
        Objects.requireNonNull(next, "map");
        if (map.equals(next)) {
            reset();
            return;
        }
        var nextLayout = new ArenaLayout(next.dimensions(), next.palette(), next.geometry());
        layout.clear(placed, (position, material) -> block(position).setType(material, false));
        map = next;
        layout = nextLayout;
        reset();
        world.setSpawnLocation(spawn(0));
    }

    public String name() {
        return map.name();
    }

    public boolean owns(Block block) {
        return block.getWorld().equals(world);
    }

    public boolean owns(Location location) {
        return world.equals(location.getWorld());
    }

    public static ArenaLayout.Position position(Block block) {
        return new ArenaLayout.Position(block.getX(), block.getY(), block.getZ());
    }

    public boolean canPlace(Block block) {
        return owns(block) && layout.canBuild(position(block));
    }

    public void placed(Block block) {
        placed.add(position(block));
    }

    public boolean remove(Block block) {
        return owns(block) && placed.remove(position(block));
    }

    public int bedOwner(Block block) {
        return owns(block) && block.getBlockData() instanceof Bed ? layout.bedOwner(position(block)) : -1;
    }

    public Location spawn(int slot) {
        return new Location(world, layout.spawnX(slot) + 0.5, layout.dimensions().floorY() + 1, 0.5,
                layout.spawnYaw(slot), 0);
    }

    public Location spectatorSpawn() {
        return new Location(world, layout.dimensions().bridgeLength() / 2.0,
                layout.dimensions().floorY() + 6, 0.5);
    }

    public void reset() {
        layout.reset(placed, (position, material) -> block(position).setType(material, false));
        for (int slot = 0; slot < 2; slot++) {
            for (boolean head : new boolean[] { false, true }) {
                var bed = (Bed) (slot == 0 ? Material.RED_BED : Material.BLUE_BED).createBlockData();
                bed.setFacing(slot == 0 ? BlockFace.WEST : BlockFace.EAST);
                bed.setPart(head ? Bed.Part.HEAD : Bed.Part.FOOT);
                block(layout.bed(slot, head)).setBlockData(bed, false);
            }
        }
    }

    private Block block(ArenaLayout.Position position) {
        return world.getBlockAt(position.x(), position.y(), position.z());
    }
}
