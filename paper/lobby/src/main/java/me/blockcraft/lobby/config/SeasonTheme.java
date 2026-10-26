package me.blockcraft.lobby.config;

import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import me.blockcraft.common.config.YamlFile;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/** One section of the lobby's {@code seasons}: how the lobby looks and sounds while the calendar celebrates it. */
public record SeasonTheme(Map<Material, List<BlockData>> blocks, Set<Material> coverOn, List<BlockData> cover,
                          double coverChance, Optional<Particle> particle, int particleCount, List<Sound> sounds,
                          Duration soundEvery) {

    /** Every section named after an {@link Occasion}; an entry the server does not know is logged and left out. */
    public static Map<Occasion, SeasonTheme> all(Map<String, Object> seasons, Logger logger) {
        var themes = new EnumMap<Occasion, SeasonTheme>(Occasion.class);
        for (var occasion : Occasion.values()) {
            var section = YamlFile.section(seasons, occasion.name().toLowerCase(Locale.ROOT));
            if (!section.isEmpty()) {
                themes.put(occasion, fromYaml(section, logger));
            }
        }
        return themes;
    }

    private static SeasonTheme fromYaml(Map<String, Object> section, Logger logger) {
        var blocks = new HashMap<Material, List<BlockData>>();
        YamlFile.section(section, "blocks").forEach((key, value) -> {
            var replacements = blockData(value, logger);
            if (!replacements.isEmpty()) {
                materials(key, logger).forEach(material -> blocks.put(material, replacements));
            }
        });
        var cover = YamlFile.section(section, "cover");
        var coverOn = new HashSet<Material>();
        if (cover.get("on") instanceof List<?> on) {
            on.forEach(key -> coverOn.addAll(materials(String.valueOf(key), logger)));
        }
        var particle = YamlFile.section(section, "particle");
        var ambience = YamlFile.section(section, "sounds");
        return new SeasonTheme(Map.copyOf(blocks), Set.copyOf(coverOn), blockData(cover.get("blocks"), logger),
                cover.get("chance") instanceof Number chance ? Math.clamp(chance.doubleValue(), 0, 1) : 1,
                particle(particle.get("type"), logger),
                particle.get("count") instanceof Number count ? Math.clamp(count.intValue(), 0, 100) : 0,
                sounds(ambience.get("list"), logger),
                Duration.ofSeconds(ambience.get("every-seconds") instanceof Number every ? Math.clamp(every.intValue(), 1, 3600) : 60));
    }

    /** A material, or every block of a tag written as {@code #name}. */
    private static Set<Material> materials(String key, Logger logger) {
        if (key.startsWith("#")) {
            var tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(key.substring(1)), Material.class);
            if (tag == null) {
                logger.warning("seasons: unknown block tag " + key);
                return Set.of();
            }
            return tag.getValues();
        }
        var material = Material.matchMaterial(key);
        if (material == null || !material.isBlock()) {
            logger.warning("seasons: unknown block " + key);
            return Set.of();
        }
        return Set.of(material);
    }

    private static List<BlockData> blockData(Object value, Logger logger) {
        List<?> entries = value instanceof List<?> list ? list : value == null ? List.of() : List.of(value);
        return entries.stream().map(String::valueOf).<BlockData>mapMulti((entry, valid) -> {
            try {
                valid.accept(Bukkit.createBlockData(entry));
            } catch (IllegalArgumentException invalid) {
                logger.warning("seasons: unknown block " + entry);
            }
        }).toList();
    }

    private static Optional<Particle> particle(Object value, Logger logger) {
        if (!(value instanceof String name) || name.isBlank()) {
            return Optional.empty();
        }
        Optional<Particle> particle;
        try {
            particle = Optional.of(Particle.valueOf(name.toUpperCase(Locale.ROOT))).filter(type -> type.getDataType() == Void.class);
        } catch (IllegalArgumentException invalid) {
            particle = Optional.empty();
        }
        if (particle.isEmpty()) {
            logger.warning("seasons: particle " + name + " is unknown or needs data");
        }
        return particle;
    }

    private static List<Sound> sounds(Object value, Logger logger) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(Map.class::cast).<Sound>mapMulti((entry, valid) -> {
            try {
                valid.accept(Sound.sound(Key.key(String.valueOf(entry.get("sound"))), Sound.Source.AMBIENT,
                        entry.get("volume") instanceof Number volume ? volume.floatValue() : 1,
                        entry.get("pitch") instanceof Number pitch ? pitch.floatValue() : 1));
            } catch (InvalidKeyException invalid) {
                logger.warning("seasons: invalid sound " + entry.get("sound"));
            }
        }).toList();
    }
}
