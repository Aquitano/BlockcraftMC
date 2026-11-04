package me.blockcraft.api;

import me.blockcraft.api.pack.DisplayFont;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.NetworkAddresses;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * BlockcraftAPI's config.yml and messages.yml with the palette, the network addresses and the title lettering's
 * {@code <display>} folded in as tags. The bootstrapper loads them for the registry dialog before the plugin exists,
 * so the plugin loads them again.
 */
record ApiResources(Map<String, Object> config, Messages messages, TagResolver tags, List<String> addresses) {

    static ApiResources load(Path dataPath, Function<String, InputStream> bundled) {
        var config = YamlFile.loadWithDefaults(dataPath.resolve("config.yml"), () -> bundled.apply("config.yml"));
        var loaded = Messages.load(dataPath, () -> bundled.apply("messages.yml"));
        var network = NetworkAddresses.fromYaml(config);
        var tags = TagResolver.resolver(Messages.tagsOf(network), loaded.tagsOf("palette"), DisplayFont.TAG);
        return new ApiResources(config, loaded.with(tags), tags, network.values().stream().map(String::valueOf).toList());
    }
}
