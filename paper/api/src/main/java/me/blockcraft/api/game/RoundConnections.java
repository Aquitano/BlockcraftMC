package me.blockcraft.api.game;

import me.blockcraft.api.vanish.VanishChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Players coming and going on a game server: joins and quits go to the game without a chat line, a vanish change
 * reaches it after the {@link Round} has moved the player, and every change redraws the sidebars, as does the
 * proxy's word on the pack, which arrives after the join and decides the sidebar's icons.
 */
public final class RoundConnections implements Listener {

    public interface Seating {

        void join(Player player);

        void quit(Player player);

        void vanished(Player player);

        void refreshAll();
    }

    private final Seating game;

    public RoundConnections(Seating game) {
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
    public void onResourcePack(PlayerResourcePackStatusEvent event) {
        game.refreshAll();
    }

    @EventHandler
    public void onVanish(VanishChangeEvent event) {
        if (event.isVanished()) {
            game.vanished(event.getPlayer());
        }
        game.refreshAll();
    }
}
