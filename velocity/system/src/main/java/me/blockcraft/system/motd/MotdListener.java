package me.blockcraft.system.motd;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.Favicon;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.ProxySettings;
import me.blockcraft.system.SystemConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Server list entry: default MOTD plus a second line, the configured player cap, the maintenance banner, the icon
 * from {@code server-icon.png} and the network's addresses where the client would list player names. The second line
 * celebrates the network's birthday or the most special occasion active today, else greets the returning player behind
 * the address, else is a random one.
 */
public final class MotdListener {

    private static final int MAINTENANCE_PROTOCOL = 0;
    private static final String ICON_FILE = "server-icon.png";
    private static final List<Map.Entry<Occasion, String>> SEASONS = List.of(
            Map.entry(Occasion.NEW_YEARS_EVE, "motd.season.new-years-eve"),
            Map.entry(Occasion.CHRISTMAS, "motd.season.christmas"),
            Map.entry(Occasion.ADVENT, "motd.season.advent"),
            Map.entry(Occasion.WINTER, "motd.season.winter"),
            Map.entry(Occasion.HALLOWEEN, "motd.season.halloween"),
            Map.entry(Occasion.EASTER, "motd.season.easter"));

    private final PlayerVisibility visibility;
    private final SystemConfig config;
    private final ProxySettings settings;
    private final ReturningPlayers returning;
    private final Messages messages;
    private final Optional<Favicon> favicon;
    private final Random random = new Random();

    public MotdListener(PlayerVisibility visibility, SystemConfig config, ProxySettings settings, ReturningPlayers returning,
                        Messages messages, Path dataDirectory, Logger logger) {
        this.visibility = visibility;
        this.config = config;
        this.settings = settings;
        this.returning = returning;
        this.messages = messages;
        this.favicon = loadFavicon(dataDirectory, logger);
    }

    @Subscribe
    public EventTask onPing(ProxyPingEvent event) {
        var address = event.getConnection().getRemoteAddress().getAddress();
        return EventTask.async(() -> event.setPing(answer(event.getPing(), address)));
    }

    private ServerPing answer(ServerPing original, InetAddress address) {
        int online = visibility.publicCount();
        int max = config.maxPlayers();
        var ping = original.asBuilder().onlinePlayers(online).maximumPlayers(max);
        favicon.ifPresent(ping::favicon);
        ping.clearSamplePlayers().samplePlayers(sample(online, max));
        var firstLine = firstLine(config, messages);
        if (settings.maintenance()) {
            ping.version(new ServerPing.Version(MAINTENANCE_PROTOCOL, legacy(messages.get("motd.maintenance-version",
                    Formatter.number("online", online), Formatter.number("max", max)))));
            ping.description(firstLine.append(Component.newline()).append(messages.get("motd.maintenance-line")));
        } else {
            ping.version(new ServerPing.Version(original.getVersion().getProtocol(), legacy(messages.get("motd.wrong-version"))));
            ping.description(firstLine.append(Component.newline()).append(secondLine(address)));
        }
        return ping.build();
    }

    static Component firstLine(SystemConfig config, Messages messages) {
        if (!config.defaultMotd().isBlank()) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(config.defaultMotd());
        }
        var address = String.join(" ", config.network().ip().toUpperCase(Locale.ROOT).split(""));
        return messages.get("motd.brand").replaceText(replacement ->
                replacement.matchLiteral(config.network().ip()).replacement(address));
    }

    private Component secondLine(InetAddress address) {
        return seasonalLine(config.calendar(), messages)
                .or(() -> returning.at(address).map(this::greeting))
                .orElseGet(this::randomLine);
    }

    /** A forced birthday has no age to count on any other day, so it gives way to the next occasion. */
    static Optional<Component> seasonalLine(NetworkCalendar calendar, Messages messages) {
        var today = calendar.occasions();
        var age = calendar.age(calendar.today());
        if (today.contains(Occasion.BIRTHDAY) && age.isPresent()) {
            return Optional.of(age.getAsInt() == 1
                    ? messages.get("motd.birthday-one")
                    : messages.get("motd.birthday", Formatter.number("years", age.getAsInt())));
        }
        return SEASONS.stream().filter(season -> today.contains(season.getKey())).findFirst()
                .map(season -> messages.get(season.getValue()));
    }

    private Component greeting(ReturningPlayers.Returning player) {
        var name = Placeholder.unparsed("player", player.name());
        return switch (player.friendsOnline()) {
            case 0 -> messages.get("motd.welcome-back", name);
            case 1 -> messages.get("motd.welcome-back-friend", name);
            default -> messages.get("motd.welcome-back-friends", name, Formatter.number("count", player.friendsOnline()));
        };
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
