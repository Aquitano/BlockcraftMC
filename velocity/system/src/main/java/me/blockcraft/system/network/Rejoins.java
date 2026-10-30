package me.blockcraft.system.network;

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
import java.util.concurrent.TimeUnit;

/**
 * Sends a player back into the round whose server keeps their place ({@code bc_rejoins}): straight there when they
 * connect again, and with /rejoin from a lobby, which reminds them on arrival.
 */
public final class Rejoins {

    /** A switch to the lobby reaches the game server only after the lobby, so its place is written a moment later. */
    private static final long OFFER_DELAY_SECONDS = 2;

    private final Object plugin;
    private final ProxyServer proxy;
    private final ServerDirectory directory;
    private final RejoinRepository repository;
    private final Messages messages;
    private final Logger logger;

    public Rejoins(Object plugin, ProxyServer proxy, ServerDirectory directory, RejoinRepository repository, Messages messages,
                   Logger logger) {
        this.plugin = plugin;
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
    public void onServerPostConnect(ServerPostConnectEvent event) {
        var player = event.getPlayer();
        if (!onLobby(player)) {
            return;
        }
        proxy.getScheduler().buildTask(plugin, () -> {
            if (onLobby(player) && place(player.getUniqueId()).isPresent()) {
                player.sendMessage(messages.get("rejoin.offer").append(Component.space()).append(button()));
            }
        }).delay(OFFER_DELAY_SECONDS, TimeUnit.SECONDS).schedule();
    }

    private boolean onLobby(Player player) {
        return player.isActive() && player.getCurrentServer()
                .filter(connection -> directory.isLobby(connection.getServerInfo().getName())).isPresent();
    }

    private Component button() {
        return messages.get("rejoin.button")
                .clickEvent(ClickEvent.runCommand("/rejoin"))
                .hoverEvent(HoverEvent.showText(messages.get("rejoin.button-hover")));
    }
}
