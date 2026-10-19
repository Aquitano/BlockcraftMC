package me.blockcraft.lobby.listener;

import me.blockcraft.lobby.LobbyPermissions;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;

/** Only whitelisted commands may be used by players without the bypass permission. */
public final class CommandBlockListener implements Listener {

    private final Set<String> allowedCommands;

    public CommandBlockListener(Set<String> allowedCommands) {
        this.allowedCommands = allowedCommands;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().hasPermission(LobbyPermissions.BYPASS)) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase();
        if (!allowedCommands.contains(label)) {
            event.setCancelled(true);
        }
    }
}
