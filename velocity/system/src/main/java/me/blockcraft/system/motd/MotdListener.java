package me.blockcraft.system.motd;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.ProxySettings;
import me.blockcraft.system.SystemConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.Random;

/** Server list entry: default MOTD plus a random second line, the configured player cap, and the maintenance banner. */
public final class MotdListener {

    private static final int MAINTENANCE_PROTOCOL = 0;

    private final ProxyServer proxy;
    private final SystemConfig config;
    private final ProxySettings settings;
    private final Messages messages;
    private final Random random = new Random();

    public MotdListener(ProxyServer proxy, SystemConfig config, ProxySettings settings, Messages messages) {
        this.proxy = proxy;
        this.config = config;
        this.settings = settings;
        this.messages = messages;
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        int online = proxy.getPlayerCount();
        int max = config.maxPlayers();
        var ping = event.getPing().asBuilder().onlinePlayers(online).maximumPlayers(max);
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

    private Component randomLine() {
        List<String> lines = config.randomMotds();
        if (lines.isEmpty()) {
            return Component.empty();
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(lines.get(random.nextInt(lines.size())));
    }

    private static String legacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
