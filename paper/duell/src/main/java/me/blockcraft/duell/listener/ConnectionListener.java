package me.blockcraft.duell.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.duell.game.Duel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public final class ConnectionListener implements Listener {

    private final Duel duel;

    public ConnectionListener(Duel duel) {
        this.duel = duel;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        duel.join(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        duel.quit(event.getPlayer());
    }

    /** The proxy's word on the pack arrives after the join, and the sidebar's icons depend on it. */
    @EventHandler
    public void onResourcePack(PlayerResourcePackStatusEvent event) {
        duel.refreshScoreboards();
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!duel.isParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
