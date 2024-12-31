package me.blockcraft.QuickSG.tools;

import me.blockcraft.QuickSG.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class Restart
        implements Listener {
    public static int countdown = 11;

    public static void start(Player p) {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Main.getInstance(), () -> {
            if (--Restart.countdown == 10) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Der Server startet in \u00a7c10 \u00a77Sekunden neu!");
            }
            if (Restart.countdown == 5) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Der Server startet in \u00a7c5 \u00a77Sekunden neu!");
                Restart.clearitems(p);
            }
            if (Restart.countdown == 4) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Der Server startet in \u00a7c4 \u00a77Sekunden neu!");
            }
            if (Restart.countdown == 3) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Der Server startet in \u00a7c3 \u00a77Sekunden neu!");
            }
            if (Restart.countdown == 2) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Der Server startet in \u00a7c2 \u00a77Sekunden neu!");
            }
            if (Restart.countdown == 1) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Der Server startet in \u00a7c1 \u00a77Sekunden neu!");
            }
            if (Restart.countdown == 0) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7cDer Server Restartet Jetzt!");
                Bukkit.shutdown();
            }
        }, 20, 20);
    }

    @SuppressWarnings("unused")
    public static void clearitems(Player p) {
        for (Player all : Bukkit.getOnlinePlayers()) {
            for (Entity ent : p.getWorld().getEntities()) {
                if (ent.getType() != EntityType.DROPPED_ITEM) continue;
                ent.remove();
            }
        }
    }

}

