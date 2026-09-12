package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Keeps every player's tab header and footer (player count, current server, links) current. */
public final class TabHeaderListener {

    private final ProxyServer proxy;
    private final SystemConfig config;
    private final Messages messages;

    public TabHeaderListener(ProxyServer proxy, SystemConfig config, Messages messages) {
        this.proxy = proxy;
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

    private void refreshAll() {
        var shared = TagResolver.resolver(
                Placeholder.unparsed("ip", config.network().ip()),
                Placeholder.unparsed("discord", config.network().discord()),
                Placeholder.unparsed("website", config.network().website()),
                Formatter.number("online", proxy.getPlayerCount()),
                Formatter.number("max", config.maxPlayers()));
        var header = Component.join(JoinConfiguration.newlines(), messages.getLines("tab.header", shared));
        for (Player player : proxy.getAllPlayers()) {
            var footer = Component.join(JoinConfiguration.newlines(), messages.getLines("tab.footer", shared,
                    Placeholder.unparsed("server", CommandSupport.currentServer(player))));
            player.sendPlayerListHeaderAndFooter(header, footer);
        }
    }
}
