package me.blockcraft.lobby.listener;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Coins are labelled in the boss bar; the experience bar shows progress towards the next network level. */
public final class LobbyHud implements Listener {

    private final Plugin plugin;
    private final Messages messages;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private BukkitTask task;
    private boolean refreshing;

    public LobbyHud(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void start(long intervalTicks) {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1L, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        Bukkit.getOnlinePlayers().forEach(this::hide);
        bars.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var bar = BossBar.bossBar(messages.get("hud.loading"), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        bars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
        // The API loads XP and creates a first-time coin account independently of the join event.
        Tasks.later(plugin, 20L, this::refresh);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hide(event.getPlayer());
    }

    private void hide(Player player) {
        var bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private void refresh() {
        if (refreshing || bars.isEmpty()) {
            return;
        }
        refreshing = true;
        var players = List.copyOf(Bukkit.getOnlinePlayers());
        var ids = players.stream().map(Player::getUniqueId).toList();
        var coins = BlockcraftApi.get().coins();
        Tasks.async(plugin, () -> {
            var balances = new HashMap<UUID, Integer>();
            try {
                for (var id : ids) {
                    balances.put(id, coins.get(id));
                }
            } catch (DatabaseException e) {
                plugin.getSLF4JLogger().warn("Could not refresh lobby balances", e);
            }
            return balances;
        }, balances -> {
            refreshing = false;
            var xp = BlockcraftApi.get().xp();
            for (var player : players) {
                var bar = bars.get(player.getUniqueId());
                if (!player.isOnline() || bar == null) {
                    continue;
                }
                var progress = xp.levels().progress(xp.total(player));
                player.setLevel(progress.level());
                player.setExp(Math.clamp(progress.into() / (float) progress.needed(), 0.0f, 1.0f));
                var balance = balances.get(player.getUniqueId());
                if (balance != null) {
                    bar.name(messages.get("hud.balance", Formatter.number("coins", balance),
                            Formatter.number("level", progress.level()), Formatter.number("xp", progress.into()),
                            Formatter.number("needed", progress.needed())));
                }
            }
        });
    }
}
