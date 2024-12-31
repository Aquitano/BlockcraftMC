package me.blockcraft.QuickSG.Listener;

import me.blockcraft.MySQL.StatsAPI;
import me.blockcraft.QuickSG.LocationAPI;
import me.blockcraft.QuickSG.Main;
import me.blockcraft.QuickSG.tools.OnlineAbfrage;
import me.blockcraft.QuickSG.tools.Scoreboards.Scoreboard_InGame;
import me.blockcraft.QuickSG.tools.Spectator;
import me.blockcraft.coins.CAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerDeath
        implements Listener {
    public static final String deathmsg = "\u00a7f";
    public static final String killmsg = "\u00a7f";

    @EventHandler
    public void on(PlayerDeathEvent e) {
        e.setDeathMessage("");
        Player p = e.getEntity();
        Player death = e.getEntity();
        Player killer = e.getEntity().getKiller();
        if (Main.State_InGame) {
            if (e.getEntity().getKiller() != null) {
                Bukkit.broadcastMessage(Main.Prefix + deathmsg + death.getName() + "\u00a77 wurde von " + killmsg + killer.getName() + "\u00a77 getötet.");
                CAPI.addCoins(killer.getUniqueId().toString(), 5);
                StatsAPI.setCoins(killer.getUniqueId().toString(), StatsAPI.getCoins(killer.getUniqueId().toString()) + 5, killer.getName());
                StatsAPI.setKills(killer.getUniqueId().toString(), StatsAPI.getKills(killer.getUniqueId().toString()) + 1, killer.getName());
                StatsAPI.setDeaths(death.getUniqueId().toString(), StatsAPI.getDeaths(death.getUniqueId().toString()) + 1, death.getName());
                StatsAPI.setLose(death.getUniqueId().toString(), StatsAPI.getLoses(death.getUniqueId().toString()) + 1, death.getName());
                Spectator.add(death);
                for (Player all : Bukkit.getOnlinePlayers()) {
                    Scoreboard_InGame.add(all);
                }
                OnlineAbfrage.abfragen(p);
            } else if (e.getEntity().getKiller() == null) {
                Bukkit.broadcastMessage(Main.Prefix + deathmsg + death.getName() + " \u00a77ist gestorben");
                StatsAPI.setDeaths(death.getUniqueId().toString(), StatsAPI.getDeaths(death.getUniqueId().toString()) + 1, death.getName());
                StatsAPI.setLose(death.getUniqueId().toString(), StatsAPI.getLoses(death.getUniqueId().toString()) + 1, death.getName());
                StatsAPI.setCoins(death.getUniqueId().toString(), StatsAPI.getCoins(death.getUniqueId().toString()) + 2, death.getName());
                CAPI.addCoins(death.getUniqueId().toString(), 2);
                Spectator.add(death);
                for (Player all : Bukkit.getOnlinePlayers()) {
                    Scoreboard_InGame.add(all);
                }
                OnlineAbfrage.abfragen(p);
            }
        }
    }

    @EventHandler
    public void on(PlayerRespawnEvent e) {
        e.getPlayer().teleport(LocationAPI.getLocation("Spectator"));
    }
}

