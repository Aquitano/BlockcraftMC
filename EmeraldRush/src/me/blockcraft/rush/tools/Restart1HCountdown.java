package me.blockcraft.rush.tools;

import me.blockcraft.rush.Main;
import org.bukkit.Bukkit;

public class Restart1HCountdown {
    public static int i;
    public static int restartsched1h;

    static {
        Restart1HCountdown.i = 3000;
        Restart1HCountdown.restartsched1h = 0;
    }

    public static void on() {
        Restart1HCountdown.restartsched1h = Bukkit.getScheduler().scheduleSyncRepeatingTask(Main.getInstance(), () -> {
            --Restart1HCountdown.i;
            if (Restart1HCountdown.i == 1) {
                if (Bukkit.getOnlinePlayers().isEmpty()) {
                    Bukkit.shutdown();
                } else {
                    Restart1HCountdown.restartsched1h = 2000;
                }
            }
        }, 20L, 20L);
    }
}
