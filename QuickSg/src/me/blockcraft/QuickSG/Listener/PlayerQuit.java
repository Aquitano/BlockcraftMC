package me.blockcraft.QuickSG.Listener;

import me.blockcraft.MySQL.StatsAPI;
import me.blockcraft.QuickSG.Main;
import me.blockcraft.QuickSG.tools.OnlineAbfrage;
import me.blockcraft.QuickSG.tools.Scoreboards.Scoreboard_InGame;
import me.blockcraft.QuickSG.tools.Scoreboards.Scoreboard_Lobby;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuit
        implements Listener {
    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!Main.spectator.contains(p)) {
            e.setQuitMessage(Main.Prefix + "\u00a7f" + p.getName() + " \u00a77hat die Runde verlassen!");
        } else {
            e.setQuitMessage("");
        }
        Main.online.remove(p);
        Main.builder.remove(p);
        Main.spectator.remove(p);
        OnlineAbfrage.abfragen(p);
        if (Main.State_Lobby) {
            for (Player all : Bukkit.getOnlinePlayers()) {
                Scoreboard_Lobby.add(all);
            }
        }
        if (Main.State_InGame) {
            for (Player all : Bukkit.getOnlinePlayers()) {
                Scoreboard_InGame.add(all);
            }
            StatsAPI.setLose(p.getUniqueId().toString(), StatsAPI.getLoses(p.getUniqueId().toString()) + 1, p.getName());
            StatsAPI.setDeaths(p.getUniqueId().toString(), StatsAPI.getLoses(p.getUniqueId().toString()) + 1, p.getName());
        }
    }
}

