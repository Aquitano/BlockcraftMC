package me.blockcraft.duell.listeners.sg;

import me.blockcraft.coins.CAPI;
import me.blockcraft.duell.Duell;
import me.blockcraft.duell.methods.StatsAPI;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class DeathListenerSG implements Listener {
    public static Duell main;

    public DeathListenerSG(final Duell main) {
        DeathListenerSG.main = main;
    }

    @EventHandler
    public void onDeathSG(final PlayerDeathEvent e) {
        if (DeathListenerSG.main.SG) {
            e.setDeathMessage("");
            if (e.getEntity() != null) {
                final Player p = e.getEntity();
                if (p.getKiller() != null) {
                    final Player k = p.getKiller();
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + p.getName() + " \u00a77ist gestorben.");
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + k.getName() + " \u00a77hat das Duell gewonnen.");
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + k.getName() + " \u00a77erhält \u00a7a10 Coins\u00a7f.");
                    CAPI.addCoins(k.getUniqueId().toString(), 10);
                    StatsAPI.addWin(k.getUniqueId().toString(), k.getName(), 1);
                    StatsAPI.addLose(p.getUniqueId().toString(), p.getName(), 1);
                    StatsAPI.addKills(k.getUniqueId().toString(), k.getName(), 1);
                    StatsAPI.addDeaths(p.getUniqueId().toString(), p.getName(), 1);
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde wurde beendet.");
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Der Server startet in \u00a7c10 \u00a77Sekunden neu.");
                    DeathListenerSG.main.noPvP = true;
                    DeathListenerSG.main.RestartCountdown();
                    DeathListenerSG.main.CountdownRunning = true;
                    p.spigot().respawn();
                } else {
                    String gewinner;
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        if (all != p) {
                            gewinner = all.getName();
                            Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + gewinner + " \u00a77hat das Duell gewonnen.");
                        }
                        p.sendTitle("\u00a7aSpiel vorbei!", "");

                    }
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + p.getName() + " \u00a77ist gestorben.");
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde wurde beendet.");
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Der Server startet in \u00a7c10 \u00a77Sekunden neu.");
                    DeathListenerSG.main.noPvP = true;
                    DeathListenerSG.main.RestartCountdown();
                    DeathListenerSG.main.CountdownRunning = true;
                }
            }
        }
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent e) {
        final Player p = e.getPlayer();
        if (DeathListenerSG.main.SG) {
            e.setRespawnLocation(DeathListenerSG.main.getSpawn());
            for (final Player all : Bukkit.getOnlinePlayers()) {
                if (all != p) {
                    all.teleport(DeathListenerSG.main.getSpawn());
                    all.setFireTicks(0);
                }
            }
            p.setFireTicks(0);
        }
    }
}
