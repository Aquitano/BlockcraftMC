package me.blockcraft.lobby.config;

import me.blockcraft.api.pack.PackModel;
import org.bukkit.Material;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Lobby's {@code props} section: the set pieces staff place with /setprop. Each is a group of block and item displays
 * round its spot that turns once every {@code spin-seconds} and rises and sinks by {@code bob} blocks every
 * {@code bob-seconds}; a part marked {@code still} stays put, such as a pedestal. Displays are drawn up to
 * {@code view-range} times the vanilla distance, and move by one smoothed keyframe every {@code keyframe-ticks}.
 */
public record PropsConfig(float viewRange, int keyframeTicks, Map<String, Prop> props) {

    public record Prop(double spinSeconds, double bob, double bobSeconds, List<Part> parts) {
    }

    /** What a part shows: a block, or an item, which may ask for a pack model and is its vanilla self without it. */
    public sealed interface Look {
    }

    public record BlockLook(Material material) implements Look {
    }

    public record ItemLook(Material material, Optional<PackModel> model) implements Look {
    }

    /** One display: centred on {@code at} blocks from the spot, {@code size} blocks large, turned by {@code yaw} degrees. */
    public record Part(Look look, Vector3f at, Vector3f size, float yaw, boolean still) {
    }

    public static PropsConfig fromYaml(Map<String, Object> config, Logger logger) {
        var props = new LinkedHashMap<String, Prop>();
        if (config.get("pieces") instanceof Map<?, ?> pieces) {
            pieces.forEach((name, value) -> {
                if (value instanceof Map<?, ?> prop) {
                    props.put(String.valueOf(name), prop(prop, String.valueOf(name), logger));
                }
            });
        }
        return new PropsConfig((float) number(config, "view-range", 0.5, 0.1, 4),
                (int) number(config, "keyframe-ticks", 20, 2, 200), Map.copyOf(props));
    }

    private static Prop prop(Map<?, ?> prop, String name, Logger logger) {
        var parts = new ArrayList<Part>();
        if (prop.get("parts") instanceof List<?> list) {
            for (var entry : list) {
                if (entry instanceof Map<?, ?> part) {
                    part(part).ifPresentOrElse(parts::add, () -> logger.warning("Prop " + name + " has a part without a known block, item or model: " + part));
                }
            }
        }
        return new Prop(number(prop, "spin-seconds", 0, 0, 3600), number(prop, "bob", 0, 0, 4),
                number(prop, "bob-seconds", 4, 0.5, 600), List.copyOf(parts));
    }

    private static Optional<Part> part(Map<?, ?> part) {
        return look(part).map(look -> new Part(look, vector(part.get("at"), 0), vector(part.get("size"), 1),
                (float) number(part, "yaw", 0, -360, 360), Boolean.TRUE.equals(part.get("still"))));
    }

    private static Optional<Look> look(Map<?, ?> part) {
        if (part.get("block") instanceof String block) {
            return Optional.ofNullable(Material.matchMaterial(block)).filter(Material::isBlock).map(BlockLook::new);
        }
        var model = part.get("model") instanceof String path ? PackModel.byPath(path) : Optional.<PackModel>empty();
        if (part.get("item") instanceof String item) {
            return Optional.ofNullable(Material.matchMaterial(item)).filter(Material::isItem)
                    .map(material -> new ItemLook(material, model.filter(found -> found.fallback() == material)));
        }
        return model.map(found -> new ItemLook(found.fallback(), Optional.of(found)));
    }

    /** {@code [x, y, z]}, or one number for all three. */
    private static Vector3f vector(Object value, float fallback) {
        if (value instanceof Number number) {
            return new Vector3f(number.floatValue());
        }
        if (value instanceof List<?> list && list.size() == 3 && list.stream().allMatch(Number.class::isInstance)) {
            return new Vector3f(((Number) list.get(0)).floatValue(), ((Number) list.get(1)).floatValue(), ((Number) list.get(2)).floatValue());
        }
        return new Vector3f(fallback);
    }

    private static double number(Map<?, ?> config, String key, double fallback, double min, double max) {
        return Math.clamp(config.get(key) instanceof Number number ? number.doubleValue() : fallback, min, max);
    }
}
