package me.blockcraft.api.config;

import me.blockcraft.common.config.StrictConfig;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A plugin's {@code config.yml} on disk holds only an operator's overrides, and Bukkit's {@code getInt(path, fallback)}
 * answers with the fallback for every key the file lacks, never with the bundled default. Filling the loaded config
 * with the bundled values makes every read see them; nothing writes the result back to disk.
 */
public final class BundledDefaults {

    private BundledDefaults() {
    }

    public static void apply(JavaPlugin plugin) {
        var config = plugin.getConfig();
        config.options().copyDefaults(true);
        fill(config);
    }

    /** {@code section} of a config {@link #apply} filled, read without fallbacks of its own. */
    public static StrictConfig strict(ConfigurationSection section) {
        return new StrictConfig("config.yml", section.getCurrentPath(), tree(section));
    }

    private static Map<String, Object> tree(ConfigurationSection section) {
        var tree = new LinkedHashMap<String, Object>();
        section.getValues(false).forEach((key, value) ->
                tree.put(key, value instanceof ConfigurationSection nested ? tree(nested) : value));
        return tree;
    }

    static void fill(Configuration config) {
        var defaults = config.getDefaults();
        if (defaults == null) {
            return;
        }
        for (var key : defaults.getKeys(true)) {
            if (config.contains(key, true)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                config.createSection(key);
            } else {
                config.set(key, defaults.get(key));
            }
        }
    }
}
