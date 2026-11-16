package me.blockcraft.api;

import me.blockcraft.api.pack.DisplayFont;
import me.blockcraft.common.config.StrictConfig;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.NetworkConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * BlockcraftAPI's config.yml and messages.yml with network.yml's addresses and palette and the title lettering's
 * {@code <display>} folded in as tags. The bootstrapper loads them for the registry dialog before the plugin exists,
 * so the plugin loads them again.
 */
record ApiResources(StrictConfig config, NetworkConfig network, Messages messages, TagResolver tags) {

    static ApiResources load(Path dataPath, Function<String, InputStream> bundled) {
        var config = StrictConfig.of("BlockcraftAPI config.yml",
                YamlFile.loadWithDefaults(dataPath.resolve("config.yml"), () -> bundled.apply("config.yml")));
        var network = NetworkConfig.load(dataPath);
        var tags = TagResolver.resolver(network.tags(), DisplayFont.TAG);
        return new ApiResources(config, network, Messages.load(dataPath, () -> bundled.apply("messages.yml"), tags), tags);
    }
}
