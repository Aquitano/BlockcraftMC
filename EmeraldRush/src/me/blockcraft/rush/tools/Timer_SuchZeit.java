package me.blockcraft.rush.tools;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.RushManager;
import me.blockcraft.rush.Scoreboards.Scoreboard_Shopping;
import me.blockcraft.rush.Scoreboards.Scoreboard_SuchZeit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class Timer_SuchZeit implements Listener {
    public static int i;
    public static boolean started;
    public static int schedulersuchzeit;

    static {
        Timer_SuchZeit.i = 300;
        Timer_SuchZeit.started = false;
        Timer_SuchZeit.schedulersuchzeit = 0;
    }

    public static void start() {
        for (final Player all : Bukkit.getOnlinePlayers()) {
            all.getInventory().clear();
        }
        if (!Timer_SuchZeit.started) {
            Timer_SuchZeit.started = true;
            Timer_SuchZeit.schedulersuchzeit = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Main.getInstance(), () -> {
                --Timer_SuchZeit.i;
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    RushManager.sendActionBar(all, "\u00a78» \u00a7aEmeralds: \u00a77" + RushManager.getCoins(all));
                    Scoreboard_SuchZeit.add(all);
                }
                if (Timer_SuchZeit.i == 120) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                    }
                    Bukkit.broadcastMessage(Main.Prefix + "Die \u00a7fSuch Phase \u00a77endet in \u00a7f2\u00a77 Minuten!");
                }
                if (Timer_SuchZeit.i == 60) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                    }
                    Bukkit.broadcastMessage(Main.Prefix + "Die \u00a7fSuch Phase \u00a77endet in \u00a7f1 \u00a77Minuten!");
                }
                if (Timer_SuchZeit.i == 15) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                    }
                    Bukkit.broadcastMessage(Main.Prefix + "Die \u00a7fSuch Phase \u00a77endet in \u00a7f15 \u00a77Sekunden!");
                }
                if (Timer_SuchZeit.i == 5) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                    }
                    Bukkit.broadcastMessage(Main.Prefix + "Die \u00a7fSuch Phase \u00a77endet in \u00a7f5 \u00a77Sekunden!");
                }
                if (Timer_SuchZeit.i == 4) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.LEVEL_UP, 4.0f, 4.0f);
                    }
                }
                if (Timer_SuchZeit.i == 3) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.LEVEL_UP, 4.0f, 4.0f);
                    }
                }
                if (Timer_SuchZeit.i == 2) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.LEVEL_UP, 4.0f, 4.0f);
                    }
                }
                if (Timer_SuchZeit.i == 1) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.LEVEL_UP, 4.0f, 4.0f);
                    }
                }
                if (Timer_SuchZeit.i == 0) {
                    Bukkit.getScheduler().cancelTask(Timer_SuchZeit.schedulersuchzeit);
                    Main.State_Shopping = true;
                    Main.State_DM = false;
                    Main.State_SuchZeit = false;
                    for (final Player all : Main.online) {
                        all.playSound(all.getLocation(), Sound.LEVEL_UP, 4.0f, 4.0f);
                        Scoreboard_Shopping.add(all);
                        RushManager.sendTitle(all, 25, 25, 25, "\u00a7f" + RushManager.getCoins(all), "\u00a7a\u00a7lEmeralds");
                        all.sendMessage(Main.Prefix + "Du hast nun 30 Sekunden Zeit um dich auszurüsten!");
                        all.sendMessage(Main.Prefix + "\u00a7cSolltest du kein Shop Item bekommen, kannst du mit /chest den Shop äffnen!");
                        final ItemStack shop = new ItemStack(Material.CHEST);
                        final ItemMeta sm = shop.getItemMeta();
                        sm.setDisplayName("\u00a78» \u00a79Shop");
                        shop.setItemMeta(sm);
                        all.getInventory().clear();
                        all.getInventory().setItem(4, shop);
                        all.teleport(RushManager.getLocation("shopping"));
                        RushManager.setleben(all, 3);
                    }
                    Timer_Shopping.add();
                    for (final Player spec : Main.spectator) {
                        spec.teleport(RushManager.getLocation("shopping"));
                    }
                }
            }, 20L, 20L);
        }
    }
}
