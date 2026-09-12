package me.blockcraft.rush.listener;

import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.game.Game;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ConnectionListener implements Listener {

    private final Game game;
    private final Messages messages;

    public ConnectionListener(Game game, Messages messages) {
        this.game = game;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        game.join(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        boolean quietly = game.quit(player);
        event.quitMessage(quietly ? null : messages.get("lobby.left", Placeholder.unparsed("player", player.getName())));
    }
}
