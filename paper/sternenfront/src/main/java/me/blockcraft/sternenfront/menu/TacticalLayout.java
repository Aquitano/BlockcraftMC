package me.blockcraft.sternenfront.menu;

import me.blockcraft.sternenfront.game.BattleNavigation.Point;
import me.blockcraft.sternenfront.game.Spawn;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class TacticalLayout {

    record Marker(Spawn spawn, Point point) { }

    private static final int COLUMNS = 9;
    private static final int ROWS = 4;

    private TacticalLayout() { }

    static Map<Spawn, Integer> project(List<Marker> markers) {
        var slots = new LinkedHashMap<Spawn, Integer>();
        if (markers.isEmpty()) return slots;
        double minX = markers.stream().mapToDouble(marker -> marker.point().x()).min().orElseThrow();
        double maxX = markers.stream().mapToDouble(marker -> marker.point().x()).max().orElseThrow();
        double minZ = markers.stream().mapToDouble(marker -> marker.point().z()).min().orElseThrow();
        double maxZ = markers.stream().mapToDouble(marker -> marker.point().z()).max().orElseThrow();
        double scale = Math.max((maxX - minX) / (COLUMNS - 1), (maxZ - minZ) / (ROWS - 1));
        for (var marker : markers) {
            double x = scale == 0 ? (COLUMNS - 1) / 2.0
                    : (marker.point().x() - (minX + maxX) / 2) / scale + (COLUMNS - 1) / 2.0;
            double z = scale == 0 ? (ROWS - 1) / 2.0
                    : (marker.point().z() - (minZ + maxZ) / 2) / scale + (ROWS - 1) / 2.0;
            var slot = IntStream.range(COLUMNS, COLUMNS * (ROWS + 1)).boxed().filter(candidate -> !slots.containsValue(candidate))
                    .min(Comparator.comparingDouble(candidate -> Math.pow(candidate % COLUMNS - x, 2)
                            + Math.pow(candidate / COLUMNS - 1 - z, 2)));
            slot.ifPresent(value -> slots.put(marker.spawn(), value));
        }
        return slots;
    }
}
