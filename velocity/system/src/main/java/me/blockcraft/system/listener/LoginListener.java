package me.blockcraft.system.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PlayerRepository;
import me.blockcraft.common.moderation.PunishmentRepository;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.ProxySettings;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.moderation.PunishmentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Gate at login (maintenance, player cap, active ban), remember the player, then welcome first-timers
 * and pull everyone to the lobby two seconds after they joined, as the old proxy did.
 */
public final class LoginListener {

    private final Object plugin;
    private final ProxyServer proxy;
    private final SystemConfig config;
    private final ProxySettings settings;
    private final PlayerRepository players;
    private final PunishmentRepository punishments;
    private final PunishmentService punishmentService;
    private final LobbyConnector lobby;
    private final Messages messages;
    private final Logger logger;
    private final Set<UUID> firstJoins = ConcurrentHashMap.newKeySet();

    public LoginListener(Object plugin, ProxyServer proxy, SystemConfig config, ProxySettings settings, PlayerRepository players,
                         PunishmentRepository punishments, PunishmentService punishmentService, LobbyConnector lobby, Messages messages,
                         Logger logger) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.config = config;
        this.settings = settings;
        this.players = players;
        this.punishments = punishments;
        this.punishmentService = punishmentService;
        this.lobby = lobby;
        this.messages = messages;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        return EventTask.async(() -> {
            Player player = event.getPlayer();
            if (settings.maintenance() && !player.hasPermission(SystemPermissions.MAINTENANCE_JOIN)) {
                event.setResult(ComponentResult.denied(screen("login.maintenance")));
                return;
            }
            if (proxy.getPlayerCount() >= config.maxPlayers() && !player.hasPermission(SystemPermissions.FULL_JOIN)) {
                event.setResult(ComponentResult.denied(screen("login.full", Formatter.number("max", config.maxPlayers()))));
                return;
            }
            try {
                var ban = punishments.activeBan(player.getUniqueId());
                if (ban.isPresent()) {
                    event.setResult(ComponentResult.denied(punishmentService.banScreen(ban.get())));
                    return;
                }
                if (!players.exists(player.getUniqueId())) {
                    firstJoins.add(player.getUniqueId());
                }
                players.upsert(player.getUniqueId(), player.getUsername());
            } catch (DatabaseException e) {
                // Velocity would log the exception and let the login through, ban check included.
                logger.error("Could not check {} against the database, refusing the login", player.getUsername(), e);
                event.setResult(ComponentResult.denied(screen("login.error")));
            }
        });
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        proxy.getScheduler().buildTask(plugin, () -> {
            if (!player.isActive()) {
                firstJoins.remove(player.getUniqueId());
                return;
            }
            if (firstJoins.remove(player.getUniqueId())) {
                var welcome = messages.getLines("login.welcome",
                        Placeholder.unparsed("player", player.getUsername()), Formatter.number("number", players.count()));
                for (Player all : proxy.getAllPlayers()) {
                    welcome.forEach(all::sendMessage);
                }
            }
            if (!lobby.isOnLobby(player)) {
                lobby.send(player);
            }
        }).delay(2, TimeUnit.SECONDS).schedule();
    }

    private Component screen(String key, TagResolver... placeholders) {
        return Component.join(JoinConfiguration.newlines(), messages.getLines(key, placeholders));
    }
}
