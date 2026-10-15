package me.blockcraft.system.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PlayerRepository;
import me.blockcraft.common.moderation.PunishmentRepository;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.ProxySettings;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.moderation.PunishmentService;
import me.blockcraft.system.welcome.Anniversaries;
import me.blockcraft.system.welcome.Newcomers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Gate at login (maintenance, player cap, active ban), remember the player, then welcome first-timers, congratulate
 * a player on the anniversary of their first join and pull everyone to the lobby two seconds after they joined, as
 * the old proxy did. A first-timer's first lobby is also told to greet them with the rules.
 */
public final class LoginListener {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyMessageCodec.CHANNEL);

    private final Object plugin;
    private final ProxyServer proxy;
    private final SystemConfig config;
    private final ProxySettings settings;
    private final PlayerRepository players;
    private final PunishmentRepository punishments;
    private final PunishmentService punishmentService;
    private final LobbyConnector lobby;
    private final Newcomers newcomers;
    private final Anniversaries anniversaries;
    private final Messages messages;
    private final Logger logger;
    private final Map<UUID, Newcomers.Arrival> firstJoins = new ConcurrentHashMap<>();
    private final Set<UUID> awaitingWelcome = ConcurrentHashMap.newKeySet();

    public LoginListener(Object plugin, ProxyServer proxy, SystemConfig config, ProxySettings settings, PlayerRepository players,
                         PunishmentRepository punishments, PunishmentService punishmentService, LobbyConnector lobby,
                         Newcomers newcomers, Anniversaries anniversaries, Messages messages, Logger logger) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.config = config;
        this.settings = settings;
        this.players = players;
        this.punishments = punishments;
        this.punishmentService = punishmentService;
        this.lobby = lobby;
        this.newcomers = newcomers;
        this.anniversaries = anniversaries;
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
                var known = players.byUuid(player.getUniqueId());
                players.upsert(player.getUniqueId(), player.getUsername());
                known.ifPresentOrElse(returning -> {
                    firstJoins.put(player.getUniqueId(), newcomers.returning(player));
                    if (players.verifiedFirstSeen(player.getUniqueId())) {
                        anniversaries.arrive(player.getUniqueId(), returning.firstSeen());
                    }
                }, () -> {
                    firstJoins.put(player.getUniqueId(), newcomers.arrive(player));
                    awaitingWelcome.add(player.getUniqueId());
                });
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
            var arrival = firstJoins.remove(player.getUniqueId());
            if (!player.isActive()) {
                return;
            }
            if (arrival != null) {
                newcomers.welcome(player, arrival);
            }
            anniversaries.years(player.getUniqueId()).ifPresent(years -> player.sendMessage(years == 1
                    ? messages.get("welcome.anniversary-one")
                    : messages.get("welcome.anniversary", Formatter.number("years", years))));
            if (!lobby.isOnLobby(player)) {
                lobby.send(player);
            }
        }).delay(2, TimeUnit.SECONDS).schedule();
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        if (!lobby.isOnLobby(player) || !awaitingWelcome.remove(player.getUniqueId())) {
            return;
        }
        var rules = GsonComponentSerializer.gson().serialize(screen("info.regeln"));
        player.getCurrentServer().ifPresent(connection ->
                connection.sendPluginMessage(CHANNEL, ProxyMessageCodec.encode(new ProxyMessage.Welcome(rules))));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        firstJoins.remove(event.getPlayer().getUniqueId());
        awaitingWelcome.remove(event.getPlayer().getUniqueId());
    }

    private Component screen(String key, TagResolver... placeholders) {
        return Component.join(JoinConfiguration.newlines(), messages.getLines(key, placeholders));
    }
}
