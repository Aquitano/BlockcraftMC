package me.blockcraft.zombies.arena;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Slab;

final class StarterArena {

    private StarterArena() { }

    static void build(ArenaMap arena, BlockBox bounds) {
        World world = arena.world();
        int centerX = (bounds.minX() + bounds.maxX()) / 2;
        int centerZ = (bounds.minZ() + bounds.maxZ()) / 2;
        int floor = bounds.minY();
        for (int x = -29; x <= 29; x++) {
            for (int z = -29; z <= 29; z++) {
                var material = Math.abs(x) <= 2 || Math.abs(z) <= 2 ? Material.POLISHED_ANDESITE
                        : (Math.floorMod(x * 7 + z * 13, 11) == 0 ? Material.CRACKED_STONE_BRICKS : Material.DEEPSLATE_TILES);
                put(world, centerX + x, floor, centerZ + z, material);
            }
        }
        for (int y = 1; y <= 6; y++) {
            for (int edge = -25; edge <= 25; edge++) {
                var material = y == 6 ? Material.WAXED_CUT_COPPER : Material.DEEPSLATE_BRICKS;
                put(world, centerX + edge, floor + y, centerZ - 25, material);
                put(world, centerX + edge, floor + y, centerZ + 25, material);
                put(world, centerX - 25, floor + y, centerZ + edge, material);
                put(world, centerX + 25, floor + y, centerZ + edge, material);
                for (int partition : new int[] {-10, 10}) {
                    put(world, centerX + edge, floor + y, centerZ + partition, material);
                    put(world, centerX + partition, floor + y, centerZ + edge, material);
                }
            }
        }
        for (var room : arena.rooms()) {
            if (!room.id().equals("yard")) {
                var accent = switch (room.id()) {
                    case "depot" -> Material.CYAN_TERRACOTTA;
                    case "workshop" -> Material.ORANGE_TERRACOTTA;
                    case "power" -> Material.YELLOW_TERRACOTTA;
                    default -> Material.WHITE_TERRACOTTA;
                };
                for (int x = room.bounds().minX() + 1; x < room.bounds().maxX(); x++) {
                    for (int z = room.bounds().minZ() + 1; z < room.bounds().maxZ(); z++) {
                        put(world, x, floor + 7, z, Material.DEEPSLATE_TILES);
                        if (Math.floorMod(x + z, 7) == 0) {
                            put(world, x, floor, z, accent);
                        }
                    }
                }
                room.door().blocks(world).forEach(block -> block.setType(Material.WAXED_CUT_COPPER, false));
            }
        }
        for (var window : arena.windows()) {
            window.bay().ifPresent(bay -> {
                boolean eastWest = window.boards().minX() == window.boards().maxX();
                int front = eastWest
                        ? (window.boards().minX() < bay.minX() ? bay.minX() : bay.maxX())
                        : (window.boards().minZ() < bay.minZ() ? bay.minZ() : bay.maxZ());
                bay.blocks(world).forEach(block -> {
                    int x = block.getX();
                    int y = block.getY();
                    int z = block.getZ();
                    if (y == bay.maxY()) {
                        block.setType(Material.WAXED_CUT_COPPER, false);
                    } else if (eastWest ? x == front && z > bay.minZ() && z < bay.maxZ()
                            : z == front && x > bay.minX() && x < bay.maxX()) {
                        var bars = (MultipleFacing) Material.IRON_BARS.createBlockData();
                        bars.setFace(eastWest ? BlockFace.NORTH : BlockFace.EAST, true);
                        bars.setFace(eastWest ? BlockFace.SOUTH : BlockFace.WEST, true);
                        block.setBlockData(bars, false);
                    } else if (x == bay.minX() || x == bay.maxX() || z == bay.minZ() || z == bay.maxZ()) {
                        block.setType(Material.DEEPSLATE_BRICKS, false);
                    }
                });
            });
            window.boards().blocks(world).forEach(block -> {
                var slab = (Slab) Material.OAK_SLAB.createBlockData();
                slab.setType(Slab.Type.TOP);
                block.setBlockData(slab, false);
            });
        }
        for (int x : new int[] {-8, 8}) {
            for (int z : new int[] {-8, 8}) {
                for (int y = 1; y <= 5; y++) {
                    put(world, centerX + x, floor + y, centerZ + z, Material.POLISHED_BASALT);
                }
                put(world, centerX + x, floor + 6, centerZ + z, Material.SEA_LANTERN);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                put(world, centerX + x, floor, centerZ + z, Material.OXIDIZED_COPPER);
            }
        }
        for (var station : arena.stations()) {
            var location = station.location();
            put(world, location.getBlockX(), location.getBlockY() - 1, location.getBlockZ(), Material.WAXED_CUT_COPPER);
            location.getBlock().setType(station.kind().material(), false);
            var lamp = location.clone().add(0, 4, 0).getBlock();
            lamp.setType(Material.SHROOMLIGHT, false);
            location.clone().add(0, 5, 0).getBlock().setType(Material.IRON_CHAIN, false);
        }
        for (int distance : new int[] {-22, -16, 16, 22}) {
            for (int side : new int[] {-7, 7}) {
                put(world, centerX + side, floor + 1, centerZ + distance, Material.BARREL);
                put(world, centerX + distance, floor + 1, centerZ + side, Material.BARREL);
            }
        }
    }

    private static void put(World world, int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }
}
