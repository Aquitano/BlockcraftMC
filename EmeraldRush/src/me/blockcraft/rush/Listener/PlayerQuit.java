package me.blockcraft.rush.Listener;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.MySQL.StatsAPI;
import me.blockcraft.rush.Scoreboards.Scoreboard_DM;
import me.blockcraft.rush.Scoreboards.Scoreboard_Lobby;
import me.blockcraft.rush.Scoreboards.Scoreboard_Shopping;
import me.blockcraft.rush.tools.OnlineAbfrage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuit implements Listener {
    @SuppressWarnings("unused")
    @EventHandler
    public void on(final PlayerQuitEvent e) {
        final Player p = e.getPlayer();
        Main.online.remove(p);
        Main.spectator.remove(p);
        if (Main.State_InGame) {
            StatsAPI.setLose(p.getUniqueId().toString(), StatsAPI.getLoses(p.getUniqueId().toString()) + 1, p.getName());
            OnlineAbfrage.abfragen(p);
        }
        if (Main.spectator.contains(p)) {
            e.setQuitMessage("");
        } else {
            e.setQuitMessage(Main.Prefix + "\u00a7f" + p.getName() + "\u00a77 hat das Spiel verlassen!");
        }
        if (Main.State_DM) {
            for (final Player all : Bukkit.getOnlinePlayers()) {
                Scoreboard_DM.add(all);
            }
            OnlineAbfrage.abfragen(p);
        }
        final boolean state_InGame = Main.State_InGame;
        if (Main.State_Shopping) {
            for (final Player all : Bukkit.getOnlinePlayers()) {
                Scoreboard_Shopping.add(all);
            }
        }
        if (Main.State_Lobby) {
            for (final Player all : Bukkit.getOnlinePlayers()) {
                Scoreboard_Lobby.add(all);
            }
        }
    }
}
