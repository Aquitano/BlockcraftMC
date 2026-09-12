package me.blockcraft.quicksg.listener;

import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.Game;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ConnectionListener implements Listener {

    private static final long LOBBY_TIME = 1000L;

    private final Game game;
    private final Messages messages;

    public ConnectionListener(Game game, Messages messages) {
        this.game = game;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (var world : Bukkit.getWorlds()) {
            world.setThundering(false);
            world.setStorm(false);
            world.setTime(LOBBY_TIME);
        }
        event.joinMessage(null);
        game.join(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        event.quitMessage(game.isSpectator(player)
                ? null
                : messages.get("quit.broadcast", Placeholder.unparsed("player", player.getName())));
        game.quit(player);
    }
}
