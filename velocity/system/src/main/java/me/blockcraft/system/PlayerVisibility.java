package me.blockcraft.system;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.moderation.VanishRepository;
import me.blockcraft.common.nick.Nick;
import me.blockcraft.common.nick.NickRepository;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * What the rest of the network gets to know about a player: vanished players are left out of counts and lookups,
 * nicked players go by their nick. The Paper servers own both states ({@code bc_vanished}, {@code bc_nicks}) and
 * report every change over the plugin channel; the tables are read once at login for players who arrive
 * already vanished or nicked, and {@code bc_vanished} again after a server switch ({@code VanishSwitchListener}).
 * Staff holding the see permissions look through both.
 */
public final class PlayerVisibility {

    private final ProxyServer proxy;
    private final VanishRepository vanish;
    private final NickRepository nicks;
    private final Logger logger;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Nick> worn = new ConcurrentHashMap<>();

    public PlayerVisibility(ProxyServer proxy, VanishRepository vanish, NickRepository nicks, Logger logger) {
        this.proxy = proxy;
        this.vanish = vanish;
        this.nicks = nicks;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onPostLogin(PostLoginEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        return EventTask.async(() -> {
            try {
                if (vanish.isVanished(player)) {
                    vanished.add(player);
                }
                nicks.get(player).ifPresent(nick -> worn.put(player, nick));
            } catch (DatabaseException e) {
                logger.warn("Could not read the vanish and nick state of {}", player, e);
            }
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (!LiveSession.endedBy(event)) {
            return;
        }
        vanished.remove(event.getPlayer().getUniqueId());
        worn.remove(event.getPlayer().getUniqueId());
    }

    public void setVanished(Player player, boolean isVanished) {
        if (isVanished) {
            vanished.add(player.getUniqueId());
        } else {
            vanished.remove(player.getUniqueId());
        }
    }

    public void setNick(Player player, Optional<Nick> nick) {
        nick.ifPresentOrElse(value -> worn.put(player.getUniqueId(), value), () -> worn.remove(player.getUniqueId()));
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    public Optional<Nick> nick(Player player) {
        return Optional.ofNullable(worn.get(player.getUniqueId()));
    }

    /** The name other players see: the nick while one is worn. */
    public String shownName(Player player) {
        return nick(player).map(Nick::name).orElseGet(player::getUsername);
    }

    public boolean canSee(CommandSource viewer, Player target) {
        return !isVanished(target) || viewer.equals(target) || viewer.hasPermission(SystemPermissions.SEE_VANISHED);
    }

    public boolean seesRealNames(CommandSource viewer) {
        return viewer.hasPermission(SystemPermissions.SEE_NICKS);
    }

    public Stream<Player> visibleTo(CommandSource viewer) {
        return proxy.getAllPlayers().stream().filter(player -> canSee(viewer, player));
    }

    public int countFor(CommandSource viewer) {
        return (int) visibleTo(viewer).count();
    }

    /** The count shown to the outside world, vanished players excluded. */
    public int publicCount() {
        return proxy.getPlayerCount() - vanished.size();
    }

    /** Names worth suggesting to the viewer: what they would see in the tab list. */
    public Stream<String> namesFor(CommandSource viewer) {
        return visibleTo(viewer).map(player -> nameFor(viewer, player));
    }

    public String nameFor(CommandSource viewer, Player target) {
        return seesRealNames(viewer) ? target.getUsername() : shownName(target);
    }

    /**
     * Every online player the viewer may mean by a name: a nick always answers, the account name behind a nick only
     * for viewers who see real names, and a vanished player only for viewers who see vanished players.
     */
    public List<Player> matches(CommandSource viewer, String name) {
        return named(name)
                .filter(player -> canSee(viewer, player)
                        && (seesRealNames(viewer) || nick(player).map(Nick::name).map(name::equalsIgnoreCase).orElse(true)))
                .toList();
    }

    /** Every online player who goes by that name, as account or as nick, whoever asks. */
    public Stream<Player> named(String name) {
        return proxy.getAllPlayers().stream().filter(player -> player.getUsername().equalsIgnoreCase(name)
                || nick(player).map(Nick::name).filter(name::equalsIgnoreCase).isPresent());
    }
}
