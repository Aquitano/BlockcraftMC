package me.blockcraft.duell.listener;

import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelMode;
import me.blockcraft.duell.game.GameItems;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
    private final Messages messages;
    private final GameItems items;

    public VsListener(Plugin plugin, Duel duel, Messages messages, GameItems items) {
        this.plugin = plugin;
        this.duel = duel;
        this.messages = messages;
        this.items = items;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        var killer = victim.getKiller();
        if (!duel.isPlaying(DuelMode.VS) || killer == null) {
            return;
        }
        event.deathMessage(messages.get("round.died", Placeholder.unparsed("player", victim.getName())));
        duel.handleVsKill(victim, killer);
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
        }));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                items.giveVsKit(player);
            }
        }, KIT_DELAY_TICKS);
    }
}
