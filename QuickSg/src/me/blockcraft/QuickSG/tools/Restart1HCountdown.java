package me.blockcraft.QuickSG.tools;

import me.blockcraft.QuickSG.Main;
import org.bukkit.Bukkit;

public class Restart1HCountdown {
    public static int i = 3000;
    public static int restartsched1h;

    public static void on() {
        restartsched1h = Bukkit.getScheduler().scheduleSyncRepeatingTask(Main.getInstance(), () -> {
            if (--Restart1HCountdown.i == 1) {
                if (Bukkit.getOnlinePlayers().isEmpty()) {
                    Bukkit.shutdown();
                } else {
                    Restart1HCountdown.restartsched1h = 2000;
                }
            }
        }, 20, 20);
    }

}

