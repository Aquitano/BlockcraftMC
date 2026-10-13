package me.blockcraft.duell.listener;

import me.blockcraft.duell.config.DuellConfig;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelMode;
import me.blockcraft.duell.game.GameItems;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

public final class VsListener implements Listener {

    private static final long KIT_DELAY_TICKS = 5L;

    private final Plugin plugin;
    private final Duel duel;
    private final DuellConfig config;
    private final GameItems items;

    public VsListener(Plugin plugin, Duel duel, DuellConfig config, GameItems items) {
        this.plugin = plugin;
        this.duel = duel;
        this.config = config;
        this.items = items;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (duel.isPlaying(DuelMode.VS)) {
            duel.handleDeath(event);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (duel.isLobby() || !duel.isMode(DuelMode.VS)) {
            return;
        }
        var player = event.getPlayer();
        duel.mapSpawn(1).ifPresent(event::setRespawnLocation);
        duel.mapSpawn(2).ifPresent(spawn -> duel.opponentsOf(player).forEach(other -> {
            other.teleport(spawn);
            items.giveVsKit(other);
            config.combat().apply(other);
        }));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                items.giveVsKit(player);
                config.combat().apply(player);
            }
        }, KIT_DELAY_TICKS);
    }
}
