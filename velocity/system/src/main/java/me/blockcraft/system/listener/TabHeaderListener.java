package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.LiveSession;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Keeps every player's tab header and footer (player count as they may see it, current server, links) current.
 * The ticking refresh is what keeps the count honest: a player hopping servers changes what others may see.
 */
public final class TabHeaderListener implements Runnable {

    private record Rendered(Component header, Component footer) {}
    private final Map<UUID, Rendered> rendered = new HashMap<>();
    private final ProxyServer proxy;
    private final PlayerVisibility visibility;
    private final SystemConfig config;
    private final Messages messages;

    public TabHeaderListener(ProxyServer proxy, PlayerVisibility visibility, SystemConfig config, Messages messages) {
        this.proxy = proxy;
        this.visibility = visibility;
        this.config = config;
        this.messages = messages;
    }

    @Subscribe
    public synchronized void onServerPostConnect(ServerPostConnectEvent event) {
        rendered.remove(event.getPlayer().getUniqueId());
        refreshAll();
    }

    @Subscribe
    public synchronized void onDisconnect(DisconnectEvent event) {
        if (LiveSession.endedBy(event)) {
            refreshExcept(Set.of(event.getPlayer().getUniqueId()));
        }
    }

    @Override
    public void run() {
        refreshAll();
    }

    public synchronized void refreshAll() {
        refreshExcept(Set.of());
    }

    private void refreshExcept(Set<UUID> excluded) {
        var max = Formatter.number("max", config.maxPlayers());
        var players = proxy.getAllPlayers().stream().filter(player -> player.isActive() && !excluded.contains(player.getUniqueId())).toList();
        rendered.keySet().retainAll(players.stream().map(Player::getUniqueId).collect(Collectors.toSet()));
        int publicCount = (int) players.stream().filter(player -> !visibility.isVanished(player)).count();
        for (Player player : players) {
            int visible = player.hasPermission(SystemPermissions.SEE_VANISHED) ? players.size()
                    : publicCount + (visibility.isVanished(player) ? 1 : 0);
            var online = Formatter.number("online", visible);
            var header = Component.join(JoinConfiguration.newlines(), messages.getLines("tab.header", max, online));
            var footer = Component.join(JoinConfiguration.newlines(), messages.getLines("tab.footer", max, online,
                    Placeholder.unparsed("server", CommandSupport.currentServer(player))));
            var next = new Rendered(header, footer);
            if (!next.equals(rendered.put(player.getUniqueId(), next))) {
                player.sendPlayerListHeaderAndFooter(header, footer);
            }
        }
    }
}
