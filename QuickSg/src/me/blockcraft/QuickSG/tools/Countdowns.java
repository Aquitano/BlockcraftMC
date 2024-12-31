package me.blockcraft.QuickSG.tools;

import me.blockcraft.QuickSG.ItemAPI;
import me.blockcraft.QuickSG.Main;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class Countdowns
        implements Listener {
    public static int countdown = 1200;

    public static void start() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Main.getInstance(), () -> {
            if (--Countdowns.countdown == 900) {
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(Main.Prefix + "\u00a775 Minuten sind vorbei, alle Spieler bekommen einen Tracker!");
                Bukkit.broadcastMessage("");
                for (Player all : Bukkit.getOnlinePlayers()) {
                    all.getInventory().addItem(ItemAPI.Stack("\u00a79Kompass", 345, "\u00a77Tracke Spieler in deiner Nähe", 1, (short) 0));
                    all.playSound(all.getLocation(), Sound.LEVEL_UP, 4.0f, 4.0f);
                }
            }
            if (Countdowns.countdown == 600) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7cDas Spiel endet in 10 Minuten!");
            }
            if (Countdowns.countdown == 300) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7cDas Spiel endet in 5 Minuten!");
            }
            if (Countdowns.countdown == 180) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7cDas Spiel endet in 3 Minuten!");
            }
            if (Countdowns.countdown == 60) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7cDas Spiel endet in 1 Minute!");
            }
            if (Countdowns.countdown == 1) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7cUnentschieden, die Runde wird Restartet!");
                Bukkit.shutdown();
            }
        }, 20, 20);
    }

}

