package me.blockcraft.api;

import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

/**
 * BlockcraftAPI's config.yml and messages.yml with the palette and the network addresses folded in as tags. The
 * bootstrapper loads them for the registry dialog before the plugin exists, so the plugin loads them again.
 */
record ApiResources(Map<String, Object> config, Messages messages, TagResolver tags) {

    static ApiResources load(Path dataPath, Function<String, InputStream> bundled) {
        var config = YamlFile.loadWithDefaults(dataPath.resolve("config.yml"), () -> bundled.apply("config.yml"));
        var loaded = Messages.load(dataPath, () -> bundled.apply("messages.yml"));
        var tags = TagResolver.resolver(Messages.tagsOf(YamlFile.section(config, "network")), loaded.tagsOf("palette"));
        return new ApiResources(config, loaded.with(tags), tags);
    }
}
