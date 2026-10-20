package me.blockcraft.lobby.listener;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.rank.Ranks;
import me.blockcraft.api.scoreboard.Sidebar;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The sidebar is the player's card: rank, coins and network level, plus where to find us. The experience bar
 * shows the progress towards the next level.
 */
public final class LobbyHud implements Listener {

    private final Plugin plugin;
    private final Messages messages;
    private final Sidebar sidebar;
    private final Map<UUID, Integer> shownCoins = new HashMap<>();
    private BukkitTask task;
    private boolean refreshing;

    public LobbyHud(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.sidebar = new Sidebar(messages.get("sidebar.title"));
    }

    public void start(long intervalTicks) {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // The API loads XP and creates a first-time coin account independently of the join event.
        Tasks.later(plugin, 20L, () -> refresh(List.of(event.getPlayer()), () -> { }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        shownCoins.remove(event.getPlayer().getUniqueId());
    }

    /** The balance the sidebar shows, at most one refresh old; empty until the first refresh after the join. */
    public OptionalInt coins(Player player) {
        var coins = shownCoins.get(player.getUniqueId());
        return coins == null ? OptionalInt.empty() : OptionalInt.of(coins);
    }

    /** The proxy's word on the pack arrives after the join, and the icons depend on it. */
    @EventHandler
    public void onResourcePack(PlayerResourcePackStatusEvent event) {
        refresh(List.of(event.getPlayer()), () -> { });
    }

    /** One round at a time; a slow database must not pile up reads. */
    private void refreshAll() {
        if (refreshing || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        refreshing = true;
        refresh(List.copyOf(Bukkit.getOnlinePlayers()), () -> refreshing = false);
    }

    private void refresh(List<Player> players, Runnable done) {
        var ids = players.stream().map(Player::getUniqueId).toList();
        var coins = BlockcraftApi.get().coins();
        Tasks.async(plugin, () -> {
            try {
                return coins.balances(ids);
            } catch (DatabaseException e) {
                plugin.getSLF4JLogger().warn("Could not refresh lobby balances", e);
                return Map.<UUID, Integer>of();
            }
        }, balances -> {
            done.run();
            for (var player : players) {
                var balance = balances.get(player.getUniqueId());
                if (player.isOnline() && balance != null) {
                    show(player, balance);
                }
            }
        });
    }

    private void show(Player player, int coins) {
        shownCoins.put(player.getUniqueId(), coins);
        var api = BlockcraftApi.get();
        var progress = api.xp().levels().progress(api.xp().total(player));
        player.setLevel(progress.level());
        player.setExp(Math.clamp(progress.into() / (float) progress.needed(), 0.0f, 1.0f));
        sidebar.show(player, messages.getLines("sidebar.lines",
                api.resourcePack().icons(player),
                Placeholder.component("rank", Ranks.prefix(player).orElse(messages.get("sidebar.no-rank"))),
                Formatter.number("coins", coins),
                Formatter.number("level", progress.level())));
    }
}
