package me.blockcraft.system.motd;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.Favicon;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.ProxySettings;
import me.blockcraft.system.SystemConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Server list entry: default MOTD plus a random second line, the configured player cap, the maintenance banner,
 * the icon from {@code server-icon.png} and the network's addresses where the client would list player names.
 */
public final class MotdListener {

    private static final int MAINTENANCE_PROTOCOL = 0;
    private static final String ICON_FILE = "server-icon.png";

    private final PlayerVisibility visibility;
    private final SystemConfig config;
    private final ProxySettings settings;
    private final Messages messages;
    private final Optional<Favicon> favicon;
    private final Random random = new Random();

    public MotdListener(PlayerVisibility visibility, SystemConfig config, ProxySettings settings, Messages messages,
                        Path dataDirectory, Logger logger) {
        this.visibility = visibility;
        this.config = config;
        this.settings = settings;
        this.messages = messages;
        this.favicon = loadFavicon(dataDirectory, logger);
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        int online = visibility.publicCount();
        int max = config.maxPlayers();
        var ping = event.getPing().asBuilder().onlinePlayers(online).maximumPlayers(max);
        favicon.ifPresent(ping::favicon);
        ping.clearSamplePlayers().samplePlayers(sample(online, max));
        var firstLine = LegacyComponentSerializer.legacyAmpersand().deserialize(config.defaultMotd());
        if (settings.maintenance()) {
            ping.version(new ServerPing.Version(MAINTENANCE_PROTOCOL, legacy(messages.get("motd.maintenance-version",
                    Formatter.number("online", online), Formatter.number("max", max)))));
            ping.description(firstLine.append(Component.newline()).append(messages.get("motd.maintenance-line")));
        } else {
            ping.version(new ServerPing.Version(event.getPing().getVersion().getProtocol(), legacy(messages.get("motd.wrong-version"))));
            ping.description(firstLine.append(Component.newline()).append(randomLine()));
        }
        event.setPing(ping.build());
    }

    /** The hover over the player count carries the network's addresses instead of who happens to be online. */
    private List<ServerPing.SamplePlayer> sample(int online, int max) {
        var sample = new ArrayList<ServerPing.SamplePlayer>();
        for (Component line : messages.getLines("motd.sample", Formatter.number("online", online), Formatter.number("max", max))) {
            sample.add(new ServerPing.SamplePlayer(legacy(line), new UUID(0, sample.size())));
        }
        return sample;
    }

    private Component randomLine() {
        List<String> lines = config.randomMotds();
        if (lines.isEmpty()) {
            return Component.empty();
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(lines.get(random.nextInt(lines.size())));
    }

    private static Optional<Favicon> loadFavicon(Path dataDirectory, Logger logger) {
        Path icon = dataDirectory.resolve(ICON_FILE);
        try {
            if (Files.notExists(icon)) {
                Files.createDirectories(dataDirectory);
                try (InputStream bundled = MotdListener.class.getResourceAsStream("/" + ICON_FILE)) {
                    if (bundled == null) {
                        return Optional.empty();
                    }
                    Files.copy(bundled, icon, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return Optional.of(Favicon.create(icon));
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            logger.warn("Could not read {}, the server list stays without an icon", icon, e);
            return Optional.empty();
        }
    }

    private static String legacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
