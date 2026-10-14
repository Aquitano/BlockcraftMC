package me.blockcraft.bedwars.listener;

import me.blockcraft.api.spectate.SpectateRequestEvent;
import me.blockcraft.bedwars.game.Game;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public final class ConnectionListener implements Listener {

    private final Game game;

    public ConnectionListener(Game game) {
        this.game = game;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        game.join(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        game.quit(event.getPlayer());
    }

    /** The proxy names the friend a watching player came for, which reaches the backend after the join. */
    @EventHandler
    public void onSpectateRequest(SpectateRequestEvent event) {
        game.follow(event.getPlayer(), event.target());
    }

    /** The proxy's word on the pack arrives after the join, and the sidebar's icons depend on it. */
    @EventHandler
    public void onResourcePack(PlayerResourcePackStatusEvent event) {
        game.scoreboard().refreshAll();
    }
}
