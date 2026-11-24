package me.blockcraft.duell.arena;

import me.blockcraft.api.world.Worlds;
import me.blockcraft.duell.config.SumoConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class SumoArena {

    private static final Set<Material> AIR_MATERIALS = Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

    public record Position(int x, int y, int z) { }

    private final SumoConfig config;
    private final World world;

    public SumoArena(SumoConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        world = Worlds.createArena(new NamespacedKey("duell", "sumo"));
        generate(config, position -> world.getBlockAt(position.x(), position.y(), position.z()).getType(),
                (position, material) -> world.getBlockAt(position.x(), position.y(), position.z()).setType(material, false));
        world.setSpawnLocation(spawn(1));
    }

    static void generate(SumoConfig config, Function<Position, Material> materialAt,
                         BiConsumer<Position, Material> setBlock) {
        for (int x = -SumoConfig.MAX_RADIUS; x <= SumoConfig.MAX_RADIUS; x++) {
            for (int z = -SumoConfig.MAX_RADIUS; z <= SumoConfig.MAX_RADIUS; z++) {
                for (int y = SumoConfig.MIN_FLOOR_Y; y <= SumoConfig.MAX_FLOOR_Y + 2; y++) {
                    var position = new Position(x, y, z);
                    if (!AIR_MATERIALS.contains(materialAt.apply(position))) {
                        setBlock.accept(position, Material.AIR);
                    }
                }
            }
        }
        for (var position : floorBlocks(config)) {
            setBlock.accept(position, Material.STONE);
        }
    }

    public Location spawn(int slot) {
        return spawn(config, world, slot);
    }

    static Location spawn(SumoConfig config, World world, int slot) {
        if (slot != 1 && slot != 2) {
            throw new IllegalArgumentException("Sumo spawn slot must be 1 or 2");
        }
        return new Location(world, 0.5 + (slot == 1 ? -config.spawnDistance() : config.spawnDistance()),
                config.floorY() + 1, 0.5, slot == 1 ? -90 : 90, 0);
    }

    public Location spectatorSpawn() {
        return new Location(world, 0.5, config.floorY() + 6, 0.5);
    }

    public boolean owns(Location location) {
        return world.equals(location.getWorld());
    }

    public boolean lost(Location location) {
        return owns(location) && location.getY() < config.loseY();
    }

    public static List<Position> floorBlocks(SumoConfig config) {
        var blocks = new ArrayList<Position>();
        int radius = config.radius();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    blocks.add(new Position(x, config.floorY(), z));
                }
            }
        }
        return List.copyOf(blocks);
    }
}
