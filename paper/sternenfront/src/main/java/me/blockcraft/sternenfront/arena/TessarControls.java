package me.blockcraft.sternenfront.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

final class TessarControls {

    private static final int FLOOR = TessarMap.FLOOR;

    private TessarControls() { }

    static List<FrontMap.ControlSite> build(Canvas canvas) {
        return List.of(vent(canvas), bulkhead(canvas), shutters(canvas));
    }

    private static FrontMap.ControlSite vent(Canvas canvas) {
        int z = TessarMap.POST_A - 10;
        canvas.fill(-3, FLOOR, z - 2, 3, FLOOR, z + 2, Material.POLISHED_ANDESITE);
        for (int x = -2; x <= 2; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                canvas.set(x, FLOOR, z + dz, "minecraft:iron_trapdoor[facing=north,half=top]");
            }
        }
        var switches = new ArrayList<Block>();
        for (int x : new int[] {-5, 5}) {
            canvas.fill(x - 1, FLOOR, z - 2, x + 1, FLOOR, z + 2, Material.POLISHED_ANDESITE);
            canvas.fill(x - 1, FLOOR + 1, z - 2, x + 1, FLOOR + 3, z + 2, Material.AIR);
            canvas.fill(x, FLOOR, z, x, FLOOR + 1, z, Material.COPPER_BLOCK);
            canvas.set(x, FLOOR + 2, z, "minecraft:stone_button[face=floor,facing=north]");
            switches.add(canvas.world().getBlockAt(x, FLOOR + 2, z));
        }
        return new FrontMap.ControlSite(FrontMap.Control.VENT, List.copyOf(switches), List.of(),
                new Location(canvas.world(), 0.5, FLOOR + 1, z + 0.5));
    }

    private static FrontMap.ControlSite bulkhead(Canvas canvas) {
        var panels = new ArrayList<Block>();
        double ax = Math.sin(Math.toRadians(192));
        double az = -Math.cos(Math.toRadians(192));
        for (int x = -6; x <= 1; x++) {
            for (int z = 10; z <= 16; z++) {
                double dx = x + 0.5 - ax * 7.5;
                double dz = z + 0.5 - az * 7.5;
                double u = dx * ax + dz * az;
                double v = dx * -az + dz * ax;
                if (u >= 5 && u < 6.3 && Math.abs(v) <= 1.6) {
                    for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                        canvas.clear(x, y, z);
                        panels.add(canvas.world().getBlockAt(x, y, z));
                    }
                }
            }
        }
        var switches = new ArrayList<Block>();
        for (int[] console : new int[][] {{-3, 10}, {-4, 16}}) {
            canvas.set(console[0], FLOOR + 1, console[1], Material.IRON_BLOCK);
            canvas.set(console[0], FLOOR + 2, console[1], "minecraft:stone_button[face=floor,facing=north]");
            switches.add(canvas.world().getBlockAt(console[0], FLOOR + 2, console[1]));
        }
        return new FrontMap.ControlSite(FrontMap.Control.BULKHEAD, List.copyOf(switches), List.copyOf(panels),
                new Location(canvas.world(), -2.5, FLOOR + 1, 13));
    }

    private static FrontMap.ControlSite shutters(Canvas canvas) {
        int z = TessarMap.POST_C - 12;
        var panels = new ArrayList<Block>();
        for (int x = -2; x <= 2; x++) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                canvas.clear(x, y, z);
                panels.add(canvas.world().getBlockAt(x, y, z));
            }
            canvas.set(x, FLOOR + 5, z, Material.STRIPPED_SPRUCE_WOOD);
        }
        var switches = new ArrayList<Block>();
        for (int x : new int[] {-6, 6}) {
            int buttonX = x < 0 ? x + 1 : x - 1;
            canvas.set(buttonX, FLOOR + 2, z, "minecraft:stone_button[face=wall,facing=" + (x < 0 ? "east" : "west") + "]");
            switches.add(canvas.world().getBlockAt(buttonX, FLOOR + 2, z));
        }
        return new FrontMap.ControlSite(FrontMap.Control.SHUTTERS, List.copyOf(switches), List.copyOf(panels),
                new Location(canvas.world(), 0.5, FLOOR + 1, z + 0.5));
    }
}
