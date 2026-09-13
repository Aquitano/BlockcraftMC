package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Keeps every player's tab header and footer (player count as they may see it, current server, links) current.
 * The ticking refresh is what keeps the count honest: a player hopping servers changes what others may see.
 */
public final class TabHeaderListener implements Runnable {

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
    public void onServerPostConnect(ServerPostConnectEvent event) {
        refreshAll();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        refreshAll();
    }

    @Override
    public void run() {
        refreshAll();
    }

    public void refreshAll() {
        var max = Formatter.number("max", config.maxPlayers());
        for (Player player : proxy.getAllPlayers()) {
            var online = Formatter.number("online", visibility.countFor(player));
            var header = Component.join(JoinConfiguration.newlines(), messages.getLines("tab.header", max, online));
            var footer = Component.join(JoinConfiguration.newlines(), messages.getLines("tab.footer", max, online,
                    Placeholder.unparsed("server", CommandSupport.currentServer(player))));
            player.sendPlayerListHeaderAndFooter(header, footer);
        }
    }
}
