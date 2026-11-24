package me.blockcraft.common.message;

import me.blockcraft.common.config.YamlFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * MiniMessage strings loaded from a plugin's {@code messages.yml}, bundled defaults underneath;
 * {@code <prefix>} resolves to the {@code prefix} key everywhere, and the global resolvers a module
 * is loaded with (network.yml's addresses and palette) resolve in every message too.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, String> messages;
    private final TagResolver globals;

    private Messages(Map<String, String> messages, TagResolver globals) {
        this.messages = messages;
        this.globals = globals;
    }

    public static Messages load(Path dataDirectory, Supplier<InputStream> bundledDefault, TagResolver... globals) {
        var messages = YamlFile.flatten(YamlFile.loadWithDefaults(dataDirectory.resolve("messages.yml"), bundledDefault));
        return new Messages(messages, TagResolver.resolver(prefixOf(messages), TagResolver.resolver(globals)));
    }

    /** The same strings with more global resolvers; used to fold tags built from this very file back into it. */
    public Messages with(TagResolver... extra) {
        return new Messages(messages, TagResolver.resolver(globals, TagResolver.resolver(extra)));
    }

    /** The same strings under {@code other}'s {@code <prefix>}: shared texts said in the voice of the module that says them. */
    public Messages prefixedLike(Messages other) {
        return new Messages(messages, TagResolver.resolver(globals, prefixOf(other.messages)));
    }

    private static TagResolver prefixOf(Map<String, String> messages) {
        return Placeholder.parsed("prefix", messages.getOrDefault("prefix", ""));
    }

    public Component get(String key, TagResolver... resolvers) {
        return MINI.deserialize(raw(key), TagResolver.resolver(globals, TagResolver.resolver(resolvers)));
    }

    public Component pick(String key, TagResolver... resolvers) {
        String[] choices = raw(key).split("\n", -1);
        return MINI.deserialize(choices[ThreadLocalRandom.current().nextInt(choices.length)],
                TagResolver.resolver(globals, TagResolver.resolver(resolvers)));
    }

    /** Multi-line messages are written as a YAML list. */
    public List<Component> getLines(String key, TagResolver... resolvers) {
        TagResolver all = TagResolver.resolver(globals, TagResolver.resolver(resolvers));
        return Arrays.stream(raw(key).split("\n", -1)).map(line -> MINI.deserialize(line, all)).toList();
    }

    /** A multi-line message as one component, for screens, headers and tooltips. */
    public Component getJoined(String key, TagResolver... resolvers) {
        return Component.join(JoinConfiguration.newlines(), getLines(key, resolvers));
    }

    public boolean has(String key) {
        return messages.containsKey(key);
    }

    public String raw(String key) {
        return messages.getOrDefault(key, "<red><missing: " + key + ">");
    }
}
