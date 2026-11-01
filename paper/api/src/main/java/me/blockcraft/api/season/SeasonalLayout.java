package me.blockcraft.api.season;

import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** {@code frozen-pads} are where lily pads the ice takes stay visible inside it; elsewhere the ice simply takes them. */
record SeasonalLayout(List<Box> snow, List<Box> freeze, List<Box> frozenPads, List<Box> keepClear, int snowLayers,
                      int snowfallRadius, List<Decoration> decorations) {

    record Point(int x, int y, int z) {
        static Point read(Object value) {
            if (!(value instanceof Map<?, ?> point)) {
                throw new IllegalArgumentException("Seasonal position must contain x, y and z");
            }
            return new Point(coordinate(point, "x"), coordinate(point, "y"), coordinate(point, "z"));
        }

        private static int coordinate(Map<?, ?> point, String axis) {
            if (!(point.get(axis) instanceof Number value) || value.doubleValue() != value.intValue()) {
                throw new IllegalArgumentException("Seasonal position needs an integer " + axis);
            }
            return value.intValue();
        }
    }

    record Box(Point min, Point max) {
        Box {
            if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
                throw new IllegalArgumentException("Seasonal bounds must be ordered from min to max");
            }
        }

        boolean contains(Point point) {
            return point.x() >= min.x() && point.x() <= max.x() && point.y() >= min.y() && point.y() <= max.y()
                    && point.z() >= min.z() && point.z() <= max.z();
        }

        boolean intersects(int chunkX, int chunkZ) {
            return max.x() >= (chunkX << 4) && min.x() <= (chunkX << 4) + 15
                    && max.z() >= (chunkZ << 4) && min.z() <= (chunkZ << 4) + 15;
        }
    }

    record Decoration(Set<Occasion> during, Point at, BlockData from, BlockData to) { }

    static SeasonalLayout load(Path file) {
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        var decorations = yaml.getMapList("decorations").stream().map(entry -> {
            if (!(entry.get("during") instanceof List<?> during) || during.isEmpty()) {
                throw new IllegalArgumentException("Seasonal decoration needs at least one occasion");
            }
            var occasions = during.stream().map(value -> Occasion.valueOf(String.valueOf(value).toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toUnmodifiableSet());
            var from = Bukkit.createBlockData(String.valueOf(entry.get("from")));
            var to = Bukkit.createBlockData(String.valueOf(entry.get("to")));
            if (!from.getMaterial().isAir() || !to.getMaterial().isBlock()) {
                throw new IllegalArgumentException("Seasonal decorations must place blocks into air");
            }
            return new Decoration(occasions, Point.read(entry.get("at")), from, to);
        }).toList();
        return new SeasonalLayout(boxes(yaml, "snow"), boxes(yaml, "freeze"), boxes(yaml, "frozen-pads"), boxes(yaml, "keep-clear"),
                Math.clamp(yaml.getInt("snow-layers", 1), 1, 8), Math.clamp(yaml.getInt("snowfall-radius", 0), 0, 512), decorations);
    }

    private static List<Box> boxes(YamlConfiguration yaml, String key) {
        return yaml.getMapList(key).stream().map(entry -> new Box(Point.read(entry.get("min")), Point.read(entry.get("max")))).toList();
    }

    boolean clear(Point point) {
        return keepClear.stream().anyMatch(box -> box.contains(point));
    }
}
