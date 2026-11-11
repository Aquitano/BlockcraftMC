package me.blockcraft.story.game;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A step of the mission from {@code stages} in config.yml, in the order the party goes through them. On entering it
 * the boss bar takes its {@code bar} colour and {@code progress}, the party hears its {@code cue} and, where given, its
 * {@code music} and {@code ambience} ("stop" ends them). {@code spawn} and {@code facts} are where a jump to it puts
 * the party and what is true by then.
 */
public record Stage(String name, int order, BossBar.Color bar, float progress, String cue, Optional<String> music,
                    Optional<String> ambience, Optional<Location> spawn, Map<String, String> facts) {

    public static List<Stage> load(World world, ConfigurationSection section) {
        var keys = List.copyOf(section.getKeys(false));
        return keys.stream().map(name -> {
            var stage = section.getConfigurationSection(name);
            var facts = new LinkedHashMap<String, String>();
            var preset = stage.getConfigurationSection("facts");
            if (preset != null) preset.getKeys(false).forEach(key -> facts.put(key, preset.getString(key)));
            return new Stage(name, keys.indexOf(name), BossBar.Color.valueOf(stage.getString("bar", "blue").toUpperCase(Locale.ROOT)),
                    (float) stage.getDouble("progress", 1), stage.getString("cue", "stage"),
                    Optional.ofNullable(stage.getString("music")), Optional.ofNullable(stage.getString("ambience")),
                    Optional.ofNullable(stage.getString("spawn")).map(text -> Spots.parse(world, text)), Map.copyOf(facts));
        }).toList();
    }
}
