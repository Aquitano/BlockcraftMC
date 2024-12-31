package me.blockcraft.duell.listeners.superjump;

import me.blockcraft.duell.Duell;
import me.blockcraft.duell.methods.BoardManager;
import me.blockcraft.duell.methods.InventoryManager;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Random;

public class RoundStartListenerSJ implements Listener {
    public static Duell main;
    public static int i;
    public static boolean map1;
    public static boolean map2;
    public static boolean map3;
    public static boolean map4;
    public static boolean map5;

    static {
        RoundStartListenerSJ.i = 30;
        RoundStartListenerSJ.map1 = false;
        RoundStartListenerSJ.map2 = false;
        RoundStartListenerSJ.map3 = false;
        RoundStartListenerSJ.map4 = false;
        RoundStartListenerSJ.map5 = false;
    }

    public RoundStartListenerSJ(final Duell main) {
        RoundStartListenerSJ.main = main;
    }

    public static Location getpos(final int player, final int map) {
        Location loc;
        final String world = RoundStartListenerSJ.main.getConfig().getString("SJ" + map + "p" + player + ".world");
        final double x = RoundStartListenerSJ.main.getConfig().getDouble("SJ" + map + "p" + player + ".x");
        final double y = RoundStartListenerSJ.main.getConfig().getDouble("SJ" + map + "p" + player + ".y");
        final double z = RoundStartListenerSJ.main.getConfig().getDouble("SJ" + map + "p" + player + ".z");
        final float yaw = (float) RoundStartListenerSJ.main.getConfig().getDouble("SJ" + map + "p" + player + ".yaw");
        final float pitch = (float) RoundStartListenerSJ.main.getConfig().getDouble("SJ" + map + "p" + player + ".pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public static void startSJCountdown(final Player p) {
        Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r30 \u00a77Sekunden.");
        final BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(RoundStartListenerSJ.main, () -> {
            if (Duell.online.size() == 2) {
                --RoundStartListenerSJ.i;
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    all.setLevel(RoundStartListenerSJ.i);
                }
                if (RoundStartListenerSJ.i == 15) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r15 \u00a77Sekunden.");
                }
                if (RoundStartListenerSJ.i == 10) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r10 \u00a77Sekunden.");
                }
                if (RoundStartListenerSJ.i == 5) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r5 \u00a77Sekunden.");
                }
                if (RoundStartListenerSJ.i == 4) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r4 \u00a77Sekunden.");
                }
                if (RoundStartListenerSJ.i == 3) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r3 \u00a77Sekunden.");
                }
                if (RoundStartListenerSJ.i == 2) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r2 \u00a77Sekunden.");
                }
                if (RoundStartListenerSJ.i == 1) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r1 \u00a77Sekunden.");
                }
                if (RoundStartListenerSJ.i == 0) {
                    Bukkit.getScheduler().cancelAllTasks();
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde wurde gestartet.");
                    RoundStartListenerSJ.main.Lobby = false;
                    RoundStartListenerSJ.main.InGame = true;
                    RoundStartListenerSJ.main.noPvP = true;
                    RoundStartListenerSJ.main.RoundTimer();
                    RoundStartListenerSJ.main.motd = false;
                    final Random ran = new Random();
                    final int ranint = ran.nextInt(4);
                    if (ranint == 3) {
                        int o = 0;
                        for (final Player all2 : Duell.online) {
                            ++o;
                            all2.teleport(RoundStartListenerSJ.getpos(o, 3));
                            final String worldname = p.getWorld().getName();
                            all2.sendTitle("\u00a79SuperJump", "\u00a77" + RoundStartListenerSJ.main.SJ3m);
                            InventoryManager.addSJInventory(all2);
                            all2.setBedSpawnLocation(all2.getLocation(), true);
                        }
                        RoundStartListenerSJ.map3 = true;
                        BoardManager.Mapname = RoundStartListenerSJ.main.SJ3m;
                        for (final Player all2 : Duell.online) {
                            all2.setLevel(0);
                            BoardManager.sendInGameBoard(all2);
                        }
                    }
                    if (ranint == 2) {
                        int o = 0;
                        for (final Player all2 : Duell.online) {
                            ++o;
                            all2.teleport(RoundStartListenerSJ.getpos(o, 2));
                            final String worldname = p.getWorld().getName();
                            all2.sendTitle("\u00a79SuperJump", "\u00a77" + RoundStartListenerSJ.main.SJ2m);
                            InventoryManager.addSJInventory(all2);
                            all2.setBedSpawnLocation(all2.getLocation(), true);
                        }
                        RoundStartListenerSJ.map2 = true;
                        BoardManager.Mapname = RoundStartListenerSJ.main.SJ2m;
                        for (final Player all2 : Duell.online) {
                            all2.setLevel(0);
                            BoardManager.sendInGameBoard(all2);
                        }
                    }
                    if (ranint == 1) {
                        int o = 0;
                        for (final Player all2 : Duell.online) {
                            ++o;
                            all2.teleport(RoundStartListenerSJ.getpos(o, 1));
                            final String worldname = p.getWorld().getName();
                            all2.sendTitle("\u00a79SuperJump", "\u00a77" + RoundStartListenerSJ.main.SJ1m);
                            InventoryManager.addSJInventory(all2);
                            all2.setBedSpawnLocation(all2.getLocation(), true);
                        }
                        RoundStartListenerSJ.map1 = true;
                        BoardManager.Mapname = RoundStartListenerSJ.main.SJ1m;
                        for (final Player all2 : Duell.online) {
                            all2.setLevel(0);
                            BoardManager.sendInGameBoard(all2);
                        }
                    }
                    if (ranint == 0) {
                        int o = 0;
                        for (final Player all2 : Duell.online) {
                            ++o;
                            all2.teleport(RoundStartListenerSJ.getpos(o, 5));
                            final String worldname = p.getWorld().getName();
                            all2.sendTitle("\u00a79SuperJump", "\u00a77" + RoundStartListenerSJ.main.SJ5m);
                            InventoryManager.addSJInventory(all2);
                            all2.setBedSpawnLocation(all2.getLocation(), true);
                        }
                        BoardManager.Mapname = RoundStartListenerSJ.main.SJ5m;
                        RoundStartListenerSJ.map5 = true;
                        for (final Player all2 : Duell.online) {
                            all2.setLevel(0);
                            BoardManager.sendInGameBoard(all2);
                        }
                    }
                    RoundStartListenerSJ.main.addCooldown();
                }
            } else {
                Bukkit.getScheduler().cancelAllTasks();
                Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7cEs sind nicht genügend Spieler online.");
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    all.setLevel(0);
                }
                RoundStartListenerSJ.i = 30;
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        if (RoundStartListenerSJ.main.SJ && !RoundStartListenerSJ.main.full && Duell.online.size() == 2) {
            RoundStartListenerSJ.main.full = true;
            startSJCountdown(p);
        }
    }
}
