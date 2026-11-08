package me.blockcraft.sternenfront.menu;

import me.blockcraft.sternenfront.game.BattlePlan.Point;
import me.blockcraft.sternenfront.game.Spawn;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Spawns on a map of nine by three slots from {@code first}, north up and east right, at one scale for both axes. The
 * bottom-right cell stays free for the dropship, and markers that would share a cell take the nearest free one.
 */
final class TacticalLayout {

    record Marker(Spawn spawn, Point point) { }

    static final int COLUMNS = 9;
    static final int ROWS = 3;
    static final int DROPSHIP_CELL = COLUMNS * ROWS - 1;

    private TacticalLayout() { }

    static Map<Spawn, Integer> project(List<Marker> markers, int first) {
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
            var cell = IntStream.range(0, DROPSHIP_CELL).boxed().filter(candidate -> !slots.containsValue(first + candidate))
                    .min(Comparator.comparingDouble(candidate -> Math.pow(candidate % COLUMNS - x, 2)
                            + Math.pow(candidate / COLUMNS - z, 2)));
            cell.ifPresent(value -> slots.put(marker.spawn(), first + value));
        }
        return slots;
    }
}
