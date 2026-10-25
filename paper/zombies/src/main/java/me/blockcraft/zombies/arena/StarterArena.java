package me.blockcraft.zombies.arena;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

final class StarterArena {

    static final int RADIUS = 49;
    static final int HEIGHT = 12;
    private final World world;
    private final int x;
    private final int y;
    private final int z;

    private enum Wing {
        EAST, NORTH, SOUTH, WEST;

        int x(int forward, int side) {
            return switch (this) {
                case EAST -> forward;
                case NORTH -> side;
                case SOUTH -> -side;
                case WEST -> -forward;
            };
        }

        int z(int forward, int side) {
            return switch (this) {
                case EAST -> side;
                case NORTH -> -forward;
                case SOUTH -> forward;
                case WEST -> -side;
            };
        }
    }

    private StarterArena(ArenaMap arena, BlockBox bounds) {
        world = arena.world();
        x = (bounds.minX() + bounds.maxX()) / 2;
        y = bounds.minY() + 1;
        z = (bounds.minZ() + bounds.maxZ()) / 2;
    }

    static void build(ArenaMap arena, BlockBox bounds) {
        var builder = new StarterArena(arena, bounds);
        builder.ground(bounds);
        builder.courtyard();
        for (var wing : Wing.values()) {
            builder.wing(wing);
        }
        arena.windows().forEach(builder::spawnRoom);
        arena.stations().forEach(builder::station);
    }

    private void ground(BlockBox bounds) {
        for (int bx = bounds.minX(); bx <= bounds.maxX(); bx++) {
            for (int bz = bounds.minZ(); bz <= bounds.maxZ(); bz++) {
                int dx = bx - x;
                int dz = bz - z;
                boolean building = Math.abs(dx) <= 16 || Math.abs(dz) <= 16;
                Material floor = building ? Material.STONE_BRICKS : Material.MOSS_BLOCK;
                if (building && Math.floorMod(dx * 7 + dz * 13, 19) == 0) {
                    floor = Material.CRACKED_STONE_BRICKS;
                }
                world.getBlockAt(bx, y - 1, bz).setType(floor, false);
                if (!building && Math.floorMod(dx * 13 + dz * 7, 43) == 0) {
                    world.getBlockAt(bx, y, bz).setType(Material.SHORT_GRASS, false);
                }
            }
        }
        for (int side : new int[] {-1, 1}) {
            fill(-49, 0, side * 49, 49, 1, side * 49, Material.TUFF_BRICKS);
            fill(side * 49, 0, -49, side * 49, 1, 49, Material.TUFF_BRICKS);
            for (int offset = -48; offset <= 48; offset++) {
                data(offset, 2, side * 49, "minecraft:iron_bars[east=true,west=true]");
                data(side * 49, 2, offset, "minecraft:iron_bars[north=true,south=true]");
            }
        }
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                for (int i : new int[] {25, 36, 44}) {
                    tree(sx * i, sz * (i == 36 ? 27 : 36));
                }
                fill(sx * 24, 0, sz * 25, sx * 28, 1, sz * 29, Material.MOSSY_COBBLESTONE);
            }
        }
    }

    private void courtyard() {
        for (int bx = -15; bx <= 15; bx++) {
            for (int bz = -15; bz <= 15; bz++) {
                if (Math.abs(bx) <= 2 || Math.abs(bz) <= 2 || Math.abs(bx) == 12 || Math.abs(bz) == 12) {
                    put(bx, -1, bz, Material.SMOOTH_STONE);
                }
                if ((Math.abs(bx) == 3 && Math.abs(bz) <= 12) || (Math.abs(bz) == 3 && Math.abs(bx) <= 12)) {
                    put(bx, -1, bz, Material.WAXED_WEATHERED_CUT_COPPER);
                }
            }
        }
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                shelter(sx, sz);
                fill(sx * 12, 0, sz * 12, sx * 14, 0, sz * 14, Material.TUFF_BRICKS);
                fill(sx * 12, 1, sz * 12, sx * 14, 1, sz * 14, Material.AZALEA_LEAVES);
                put(sx * 13, 2, sz * 13, Material.FLOWERING_AZALEA);
                lamp(sx * 4, sz * 13);
            }
        }
        fill(-5, 0, 13, 5, 0, 14, Material.POLISHED_ANDESITE);
        fill(-5, 1, 14, 5, 2, 14, Material.TUFF_BRICKS);
        fill(-5, 4, 11, 5, 4, 14, Material.WAXED_WEATHERED_CUT_COPPER_SLAB);
        for (int side : new int[] {-5, 5}) {
            fill(side, 0, 11, side, 3, 11, Material.DARK_OAK_FENCE);
        }
        fill(-2, -1, -2, 2, -1, 2, Material.WAXED_OXIDIZED_CUT_COPPER);
    }

    private void shelter(int sx, int sz) {
        fill(sx * 6, 0, sz * 6, sx * 10, 2, sz * 6, Material.TUFF_BRICKS);
        fill(sx * 10, 0, sz * 6, sx * 10, 2, sz * 10, Material.TUFF_BRICKS);
        fill(sx * 6, 3, sz * 6, sx * 10, 3, sz * 6, Material.POLISHED_ANDESITE_SLAB);
        fill(sx * 10, 3, sz * 6, sx * 10, 3, sz * 10, Material.POLISHED_ANDESITE_SLAB);
        fill(sx * 6, 4, sz * 6, sx * 10, 4, sz * 10, Material.WAXED_WEATHERED_CUT_COPPER_SLAB);
        fill(sx * 6, 0, sz * 10, sx * 6, 3, sz * 10, Material.DARK_OAK_FENCE);
        put(sx * 8, 0, sz * 7, Material.BARREL);
        put(sx * 9, 0, sz * 7, Material.BARREL);
        data(sx * 8, 3, sz * 8, "minecraft:lantern[hanging=true]");
    }

    private void wing(Wing wing) {
        var accent = switch (wing) {
            case EAST -> Material.CYAN_TERRACOTTA;
            case NORTH -> Material.TERRACOTTA;
            case SOUTH -> Material.YELLOW_TERRACOTTA;
            case WEST -> Material.WHITE_TERRACOTTA;
        };
        for (int u = 16; u <= 43; u++) {
            for (int v = -16; v <= 16; v++) {
                local(wing, u, -1, v, Math.abs(v) <= 2 ? Material.SMOOTH_STONE : Material.POLISHED_ANDESITE);
                local(wing, u, 7, v, Math.abs(v) <= 3 && u > 20 && u < 39 ? Material.LIGHT_GRAY_STAINED_GLASS : Material.DEEPSLATE_TILES);
                if (v == -16 || v == 16 || u == 16 || u == 43) {
                    for (int h = 0; h <= 6; h++) {
                        var wall = h == 0 || h == 5 ? Material.POLISHED_TUFF : h == 6 ? Material.WAXED_WEATHERED_CUT_COPPER : Material.TUFF_BRICKS;
                        local(wing, u, h, v, wall);
                    }
                }
                if (Math.abs(v) == 15 && u > 17 && u < 43) {
                    local(wing, u, 1, v, accent);
                }
                if (Math.abs(v) == 16 || u == 16 || u == 43) {
                    local(wing, u, 8, v, Material.DEEPSLATE_TILE_SLAB);
                }
            }
        }
        for (int u : new int[] {16, 25, 34, 43}) {
            for (int v : new int[] {-16, 16}) {
                for (int h = 0; h <= 7; h++) {
                    local(wing, u, h, v, Material.POLISHED_BASALT);
                }
            }
        }
        for (int u : new int[] {20, 29, 38}) {
            for (int offset = 0; offset < 3; offset++) {
                for (int h = 2; h <= 3; h++) {
                    local(wing, u + offset, h, -16, Material.GRAY_STAINED_GLASS);
                    local(wing, u + offset, h, 16, Material.GRAY_STAINED_GLASS);
                }
            }
            local(wing, u + 1, 5, -13, Material.SEA_LANTERN);
            local(wing, u + 1, 5, 13, Material.SEA_LANTERN);
        }
        for (int v : new int[] {-12, -5, 5, 12}) {
            for (int h = 0; h <= 4; h++) {
                local(wing, 16, h, v, Material.POLISHED_BASALT);
            }
        }
        for (int v = -4; v <= 4; v++) {
            local(wing, 16, 4, v, accent);
            local(wing, 15, 5, v, Material.WAXED_WEATHERED_CUT_COPPER_SLAB);
        }
        for (int u = 26; u <= 37; u++) {
            for (int h = 0; h <= 3; h++) {
                local(wing, u, h, 0, h == 3 ? Material.POLISHED_ANDESITE_SLAB : Material.TUFF_BRICKS);
            }
        }
        for (int side : new int[] {-1, 1}) {
            for (int v = 6; v <= 14; v++) {
                for (int h = 0; h <= 3; h++) {
                    local(wing, 30, h, side * v, h == 3 ? Material.POLISHED_ANDESITE_SLAB : Material.TUFF_BRICKS);
                }
            }
            for (int u : new int[] {26, 39}) {
                for (int v = 10; v <= 13; v++) {
                    local(wing, u, 0, side * v, Material.BARREL);
                    local(wing, u, 1, side * v, wing == Wing.WEST ? Material.WHITE_WOOL : Material.SPRUCE_PLANKS);
                    local(wing, u, 2, side * v, Material.SPRUCE_SLAB);
                }
            }
            for (int u = 33; u <= 38; u++) {
                local(wing, u, 0, side * 14, Material.POLISHED_ANDESITE);
                local(wing, u, 1, side * 14, wing == Wing.SOUTH ? Material.WAXED_COPPER_GRATE : Material.SPRUCE_SLAB);
            }
            local(wing, 35, 2, side * 15, wing == Wing.WEST ? Material.RED_CONCRETE : accent);
        }
        for (int u : new int[] {22, 32, 40}) {
            for (int side : new int[] {-1, 1}) {
                local(wing, u, 5, side * 4, Material.IRON_CHAIN);
                local(wing, u, 6, side * 4, Material.IRON_CHAIN);
                data(wing.x(u, side * 4), 4, wing.z(u, side * 4), "minecraft:lantern[hanging=true]");
            }
        }
        for (int u : new int[] {23, 37}) {
            for (int v : new int[] {-10, 10}) {
                local(wing, u, 8, v, Material.WAXED_EXPOSED_COPPER_GRATE);
                local(wing, u, 9, v, Material.DEEPSLATE_TILE_SLAB);
            }
        }
        for (int v = -2; v <= 2; v++) {
            for (int h = 0; h <= 2; h++) {
                local(wing, 16, h, v, Material.WAXED_CUT_COPPER);
            }
        }
        if (wing == Wing.SOUTH) {
            for (int u : new int[] {34, 39}) {
                local(wing, u, 0, 9, Material.POLISHED_BASALT);
                local(wing, u, 1, 9, Material.WAXED_COPPER_GRATE);
                local(wing, u, 2, 9, Material.SEA_LANTERN);
                local(wing, u, 3, 9, Material.LIGHTNING_ROD);
            }
        }
    }

    private void spawnRoom(ArenaMap.Window window) {
        window.bay().ifPresent(bay -> {
            boolean eastWest = window.boards().minX() == window.boards().maxX();
            bay.blocks(world).forEach(block -> {
                int bx = block.getX();
                int by = block.getY();
                int bz = block.getZ();
                boolean shell = bx == bay.minX() || bx == bay.maxX() || bz == bay.minZ() || bz == bay.maxZ();
                block.setType(by == bay.maxY() ? Material.WAXED_WEATHERED_CUT_COPPER : shell ? Material.TUFF_BRICKS : Material.AIR, false);
                if (shell && by == bay.minY() + 2 && (eastWest ? bx == window.boards().minX() : bz == window.boards().minZ())) {
                    block.setBlockData(Bukkit.createBlockData(eastWest ? "minecraft:iron_bars[north=true,south=true]" : "minecraft:iron_bars[east=true,west=true]"), false);
                }
            });
            window.boards().blocks(world).forEach(block -> block.setBlockData(Bukkit.createBlockData("minecraft:oak_slab[type=top]"), false));
            var spawn = window.spawn().clone().add(0, 3, 0).getBlock();
            spawn.setType(Material.SHROOMLIGHT, false);
        });
    }

    private void station(ArenaMap.Station station) {
        var location = station.location();
        location.clone().add(0, -1, 0).getBlock().setType(Material.WAXED_CUT_COPPER, false);
        location.getBlock().setType(station.kind().material(), false);
        location.clone().add(0, 3, 0).getBlock().setBlockData(Bukkit.createBlockData("minecraft:lantern[hanging=true]"), false);
        if (!station.room().equals("yard")) {
            for (int height = 4; height <= 6; height++) {
                location.clone().add(0, height, 0).getBlock().setType(Material.IRON_CHAIN, false);
            }
        }
    }

    private void lamp(int bx, int bz) {
        fill(bx, 0, bz, bx, 3, bz, Material.DEEPSLATE_BRICK_WALL);
        put(bx, 4, bz, Material.SEA_LANTERN);
        data(bx, 5, bz, "minecraft:polished_andesite_slab[type=bottom]");
    }

    private void tree(int bx, int bz) {
        fill(bx, 0, bz, bx, 4, bz, Material.SPRUCE_LOG);
        for (int h = 3; h <= 7; h++) {
            int radius = h == 7 ? 0 : h % 2 == 0 ? 1 : 2;
            fill(bx - radius, h, bz - radius, bx + radius, h, bz + radius, Material.SPRUCE_LEAVES);
        }
    }

    private void local(Wing wing, int forward, int height, int side, Material material) {
        put(wing.x(forward, side), height, wing.z(forward, side), material);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int bx = Math.min(x1, x2); bx <= Math.max(x1, x2); bx++) {
            for (int by = Math.min(y1, y2); by <= Math.max(y1, y2); by++) {
                for (int bz = Math.min(z1, z2); bz <= Math.max(z1, z2); bz++) {
                    put(bx, by, bz, material);
                }
            }
        }
    }

    private void put(int bx, int by, int bz, Material material) {
        world.getBlockAt(x + bx, y + by, z + bz).setType(material, false);
    }

    private void data(int bx, int by, int bz, String data) {
        world.getBlockAt(x + bx, y + by, z + bz).setBlockData(Bukkit.createBlockData(data), false);
    }
}
