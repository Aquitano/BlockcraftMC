package me.blockcraft.QuickSG.tools;

import me.blockcraft.QuickSG.Main;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

public class Schutzzeit
        implements Listener {
    public static int Countdown = 21;
    public static int schutzzeit;

    public static void start() {
        schutzzeit = Bukkit.getScheduler().scheduleSyncRepeatingTask(Main.getInstance(), () -> {
            if (--Schutzzeit.Countdown == 20) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Schutzzeit endet in\u00a7f 20\u00a77 Sekunden!");
            }
            if (Schutzzeit.Countdown == 15) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Schutzzeit endet in\u00a7f 15\u00a77 Sekunden!");
            }
            if (Schutzzeit.Countdown == 10) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Schutzzeit endet in\u00a7f 10\u00a77 Sekunden!");
            }
            if (Schutzzeit.Countdown == 5) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Schutzzeit endet in\u00a7f 5\u00a77 Sekunden!");
            }
            if (Schutzzeit.Countdown == 4) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Schutzzeit endet in\u00a7f 4\u00a77 Sekunden!");
            }
            if (Schutzzeit.Countdown == 3) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Schutzzeit endet in\u00a7f 3\u00a77 Sekunden!");
            }
            if (Schutzzeit.Countdown == 2) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Schutzzeit endet in\u00a7f 2\u00a77 Sekunden!");
            }
            if (Schutzzeit.Countdown == 1) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Schutzzeit endet in\u00a7f 1\u00a77 Sekunden!");
            }
            if (Schutzzeit.Countdown == 0) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7cDie Schutzzeit ist vorbei!");
                Bukkit.getScheduler().cancelTask(Schutzzeit.schutzzeit);
                Main.Schutzzeit = false;
            }
        }, 20, 20);
    }

}

