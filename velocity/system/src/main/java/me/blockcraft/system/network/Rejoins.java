package me.blockcraft.system.network;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.RejoinRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Sends a player back into the round whose server keeps their place ({@code bc_rejoins}): straight there when they
 * connect again, and with /rejoin from a lobby, which reminds them on arrival.
 */
public final class Rejoins {

    private final ProxyServer proxy;
    private final ServerDirectory directory;
    private final RejoinRepository repository;
    private final Messages messages;
    private final Logger logger;

    public Rejoins(ProxyServer proxy, ServerDirectory directory, RejoinRepository repository, Messages messages, Logger logger) {
        this.proxy = proxy;
        this.directory = directory;
        this.repository = repository;
        this.messages = messages;
        this.logger = logger;
    }

    /** The server that keeps the player's place, while it still runs. Blocks on the database. */
    public Optional<RegisteredServer> place(UUID player) {
        try {
            return repository.find(player).filter(server -> directory.get(server).isPresent()).flatMap(proxy::getServer);
        } catch (DatabaseException e) {
            logger.warn("Could not look up a kept round place for {}", player, e);
            return Optional.empty();
        }
    }

    public void rejoin(Player player) {
        var server = place(player.getUniqueId()).filter(place -> player.getCurrentServer()
                .filter(current -> current.getServer().equals(place)).isEmpty());
        if (server.isEmpty()) {
            player.sendMessage(messages.get("rejoin.none"));
            return;
        }
        player.sendMessage(messages.get("rejoin.returning"));
        directory.send(player, server.get());
    }

    @Subscribe
    public EventTask onServerPostConnect(ServerPostConnectEvent event) {
        var player = event.getPlayer();
        if (player.getCurrentServer().filter(connection -> directory.isLobby(connection.getServerInfo().getName())).isEmpty()) {
            return null;
        }
        return EventTask.async(() -> place(player.getUniqueId()).ifPresent(server ->
                player.sendMessage(messages.get("rejoin.offer").append(Component.space()).append(button()))));
    }

    private Component button() {
        return messages.get("rejoin.button")
                .clickEvent(ClickEvent.runCommand("/rejoin"))
                .hoverEvent(HoverEvent.showText(messages.get("rejoin.button-hover")));
    }
}
