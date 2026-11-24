package me.blockcraft.common.network;

import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.config.StrictConfig;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.elo.EloFormula;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@code network.yml}, which common bundles so the proxy and every Paper server start from the same file. A
 * {@code network.yml} in a plugin's data folder overrides keys for that plugin, and {@code BLOCKCRAFT_NETWORK_<KEY>}
 * overrides an address everywhere it is set.
 *
 * @param addresses the {@code network} section, by key
 * @param tags      the addresses as unparsed tags and the {@code palette} as parsed ones, resolved in every message
 */
public record NetworkConfig(Map<String, String> addresses, TagResolver tags, NetworkCalendar calendar,
                            int calendarPollSeconds, EloFormula elo) {

    public static final String FILE = "network.yml";

    public static NetworkConfig load(Path dataDirectory) {
        return load(dataDirectory, System.getenv());
    }

    public static NetworkConfig load(Path dataDirectory, Map<String, String> environment) {
        var root = YamlFile.loadWithDefaults(dataDirectory.resolve(FILE), () -> NetworkConfig.class.getResourceAsStream("/" + FILE));
        var addresses = new LinkedHashMap<String, String>();
        YamlFile.section(root, "network").forEach((key, value) -> {
            String override = environment.get("BLOCKCRAFT_NETWORK_" + key.toUpperCase(Locale.ROOT));
            addresses.put(key, override == null || override.isBlank() ? String.valueOf(value) : override);
        });
        var tags = TagResolver.resolver(Stream.concat(
                addresses.entrySet().stream().map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue())),
                YamlFile.section(root, "palette").entrySet().stream()
                        .map(entry -> Placeholder.parsed(entry.getKey(), String.valueOf(entry.getValue()))))
                .toList());
        var strict = StrictConfig.of(FILE, root);
        var calendar = strict.section("calendar");
        return new NetworkConfig(Collections.unmodifiableMap(addresses), tags, NetworkCalendar.from(calendar),
                Math.max(1, calendar.integer("poll-seconds")),
                new EloFormula(strict.integer("elo.start-rating"), strict.integer("elo.k-factor")));
    }
}
