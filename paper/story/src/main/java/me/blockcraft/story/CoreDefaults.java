package me.blockcraft.story;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A story game's {@code config.yml} and {@code messages.yml} lie on this plugin's own: the game's files hold its
 * mission and whatever it words or tunes differently, and every other key is the core's. An operator's overrides in
 * the game's data folder lie on both.
 */
public final class CoreDefaults {

    private CoreDefaults() {
    }

    /** Reads the game's config.yml again and fills in every key only the core has. */
    public static void underConfig(JavaPlugin game) {
        game.reloadConfig();
        BundledDefaults.apply(game);
        var config = game.getConfig();
        var core = yaml(core().getResource("config.yml"));
        for (var key : core.getKeys(true)) {
            if (config.contains(key, true)) continue;
            if (core.isConfigurationSection(key)) {
                config.createSection(key);
            } else {
                config.set(key, core.get(key));
            }
        }
    }

    public static Messages underMessages(JavaPlugin game, TagResolver... globals) {
        return Messages.load(game.getDataPath(), () -> {
            var merged = yaml(core().getResource("messages.yml"));
            var own = yaml(game.getResource("messages.yml"));
            own.getKeys(true).stream().filter(key -> !own.isConfigurationSection(key)).forEach(key -> merged.set(key, own.get(key)));
            return new ByteArrayInputStream(merged.saveToString().getBytes(StandardCharsets.UTF_8));
        }, globals);
    }

    private static JavaPlugin core() {
        return JavaPlugin.getPlugin(StoryPlugin.class);
    }

    private static YamlConfiguration yaml(InputStream resource) {
        return resource == null ? new YamlConfiguration()
                : YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
    }
}
