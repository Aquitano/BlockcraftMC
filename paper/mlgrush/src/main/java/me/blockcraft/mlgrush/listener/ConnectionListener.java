package me.blockcraft.mlgrush.listener;

import me.blockcraft.api.spectate.SpectateRequestEvent;
import me.blockcraft.api.vanish.VanishChangeEvent;
import me.blockcraft.mlgrush.game.StandaloneRushGame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public final class ConnectionListener implements Listener {

    private final StandaloneRushGame game;

    public ConnectionListener(StandaloneRushGame game) {
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

    @EventHandler
    public void onSpectate(SpectateRequestEvent event) {
        game.follow(event.getPlayer(), event.target());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVanish(VanishChangeEvent event) {
        if (event.isVanished()) {
            game.vanished(event.getPlayer());
        }
        game.updateLobby();
        game.refreshPresence();
    }

    @EventHandler
    public void onResourcePack(PlayerResourcePackStatusEvent event) {
        game.refresh();
    }
}
