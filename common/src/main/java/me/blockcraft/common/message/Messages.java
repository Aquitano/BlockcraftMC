package me.blockcraft.common.message;

import me.blockcraft.common.config.YamlFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * MiniMessage strings loaded from a plugin's {@code messages.yml}, bundled defaults underneath;
 * {@code <prefix>} resolves to the {@code prefix} key everywhere.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, String> messages;
    private final TagResolver prefix;

    private Messages(Map<String, String> messages) {
        this.messages = messages;
        this.prefix = Placeholder.parsed("prefix", messages.getOrDefault("prefix", ""));
    }

    public static Messages load(Path dataDirectory, Supplier<InputStream> bundledDefault) {
        return new Messages(YamlFile.flatten(YamlFile.loadWithDefaults(dataDirectory.resolve("messages.yml"), bundledDefault)));
    }

    public Component get(String key, TagResolver... resolvers) {
        return MINI.deserialize(raw(key), TagResolver.resolver(prefix, TagResolver.resolver(resolvers)));
    }

    /** Multi-line messages are written as a YAML list. */
    public List<Component> getLines(String key, TagResolver... resolvers) {
        TagResolver all = TagResolver.resolver(prefix, TagResolver.resolver(resolvers));
        return Arrays.stream(raw(key).split("\n", -1)).map(line -> MINI.deserialize(line, all)).toList();
    }

    public boolean has(String key) {
        return messages.containsKey(key);
    }

    public String raw(String key) {
        return messages.getOrDefault(key, "<red><missing: " + key + ">");
    }
}
