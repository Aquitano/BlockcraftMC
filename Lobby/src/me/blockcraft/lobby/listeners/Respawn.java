package me.blockcraft.lobby.listeners;

import me.blockcraft.lobby.Lobby;
import me.blockcraft.lobby.utils.Inventar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class Respawn implements Listener {
    public static Lobby main;

    public Respawn(final Lobby main) {
        Respawn.main = main;
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent e) {
        final Player p = e.getPlayer();
        e.setRespawnLocation(Respawn.main.getSpawn());
        Inventar.setupStandardItems(p);
    }
}
