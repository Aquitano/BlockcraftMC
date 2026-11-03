package me.blockcraft.sternenfront.arena;

import me.blockcraft.sternenfront.game.Faction;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class TacticalRoutes {

    private record Lane(int wallX, int z, int entryZ, int retreatX, int retreatZ, int peekX, int peekZ) { }

    private TacticalRoutes() { }

    static Map<Faction, List<FrontMap.Route>> build(Canvas canvas, Terrain terrain, String post, int z) {
        var lanes = switch (post) {
            case "raffinerie" -> List.of(new Lane(12, z - 12, z, 34, z - 5, 6, z - 10),
                    new Lane(12, z + 16, z + 6, 30, z + 18, 6, z + 8));
            case "wrack" -> List.of(new Lane(18, z - 12, z - 12, 32, z - 12, 21, z - 10),
                    new Lane(18, z + 12, z + 12, 32, z + 12, 10, z + 9));
            default -> List.of(new Lane(14, z - 14, z - 14, 28, z - 14, 11, z - 4),
                    new Lane(14, z + 10, z + 10, 28, z + 10, 3, z + 10));
        };
        var material = switch (post) {
            case "raffinerie" -> Material.IRON_BLOCK;
            case "wrack" -> Material.POLISHED_DEEPSLATE;
            default -> Material.CUT_SANDSTONE;
        };
        var routes = new EnumMap<Faction, List<FrontMap.Route>>(Faction.class);
        for (var faction : Faction.values()) {
            int side = faction == Faction.KOLONIEN ? -1 : 1;
            var choices = new ArrayList<FrontMap.Route>();
            for (var lane : lanes) {
                var cover = at(canvas, terrain, side * (lane.wallX() + 2), lane.z());
                var peek = at(canvas, terrain, side * lane.peekX(), lane.peekZ());
                var retreat = at(canvas, terrain, side * lane.retreatX(), lane.retreatZ());
                var first = at(canvas, terrain, side * 44, lane.entryZ());
                var capture = at(canvas, terrain, side * 4, z + (lane.z() < z ? -2 : 2));
                var approach = List.of(first, retreat, peek);
                for (var spot : List.of(approach.getFirst(), retreat, cover, peek, capture)) {
                    int x = spot.getBlockX();
                    int bz = spot.getBlockZ();
                    int floor = spot.getBlockY() - 1;
                    canvas.fill(x - 1, floor, bz - 1, x + 1, floor, bz + 1, material);
                    canvas.fill(x - 1, floor + 1, bz - 1, x + 1, floor + 3, bz + 1, Material.AIR);
                }
                canvas.fill(side * lane.wallX(), cover.getBlockY(), lane.z() - 2,
                        side * lane.wallX(), cover.getBlockY() + 1, lane.z() + 2, material);
                choices.add(new FrontMap.Route(approach, cover, peek, retreat, capture));
            }
            routes.put(faction, List.copyOf(choices));
        }
        return Map.copyOf(routes);
    }

    private static Location at(Canvas canvas, Terrain terrain, int x, int z) {
        return new Location(canvas.world(), x + 0.5, terrain.height(x, z) + 1, z + 0.5);
    }
}
