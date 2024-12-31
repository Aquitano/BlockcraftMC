package me.blockcraft.rush.tools;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.RushManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class Timer_DM implements Listener {
    public static int i;
    public static int schedulerDM;
    public static boolean started;

    static {
        Timer_DM.i = 1200;
        Timer_DM.schedulerDM = 1200;
        Timer_DM.started = false;
    }

    public static void start() {
        Timer_DM.schedulerDM = Bukkit.getScheduler().scheduleSyncRepeatingTask(Main.getInstance(), () -> {
            --Timer_DM.i;
            if (Timer_DM.i == 1197) {
                for (final Player players : Bukkit.getOnlinePlayers()) {
                    players.showPlayer(players);
                }
            }
            if (Timer_DM.i == 900) {
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(Main.Prefix + "\u00a775 Minuten sind vorbei, alle Spieler bekommen einen Tracker!");
                Bukkit.broadcastMessage("");
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    all.getInventory().addItem(RushManager.Stack("\u00a79Kompass", 345, "\u00a77Tracke Spieler in deiner Nähe", 1, (short) 0));
                    all.playSound(all.getLocation(), Sound.LEVEL_UP, 4.0f, 4.0f);
                }
            }
            if (Timer_DM.i == 600) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7cDas Spiel endet in 10 Minuten!");
            }
            if (Timer_DM.i == 300) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7cDas Spiel endet in 5 Minuten!");
            }
            if (Timer_DM.i == 180) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7cDas Spiel endet in 3 Minuten!");
            }
            if (Timer_DM.i == 60) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7cDas Spiel endet in 1 Minute!");
            }
            if (Timer_DM.i == 1) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7cUnentschieden, die Runde wird Restartet!");
                Bukkit.getScheduler().cancelAllTasks();
                Bukkit.shutdown();
            }
        }, 20L, 20L);
    }
}
