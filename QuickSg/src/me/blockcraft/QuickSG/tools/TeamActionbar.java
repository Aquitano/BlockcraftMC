package me.blockcraft.QuickSG.tools;

import me.blockcraft.QuickSG.Main;
import me.blockcraft.QuickSG.TitlesAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class TeamActionbar
        implements Listener {
    public static int countdown = 3;

    public static void start() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Main.getInstance(), () -> {
            if (--TeamActionbar.countdown == 2) {
                for (Player all : Bukkit.getOnlinePlayers()) {
                    TitlesAPI.sendActionBar(all, Main.getInstance().getConfig().getString("Actionbar").replace("&", "\u00a7"));
                }
            }
            if (TeamActionbar.countdown == 1) {
                TeamActionbar.countdown = 3;
            }
        }, 20, 20);
    }

}

