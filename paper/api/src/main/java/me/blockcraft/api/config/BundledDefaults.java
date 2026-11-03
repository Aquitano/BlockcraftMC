package me.blockcraft.api.config;

import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

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
