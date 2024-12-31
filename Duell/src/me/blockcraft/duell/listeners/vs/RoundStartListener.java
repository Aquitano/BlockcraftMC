package me.blockcraft.duell.listeners.vs;

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

public class RoundStartListener implements Listener {
    public static Duell main;
    public static int i;
    public static boolean map1;
    public static boolean map2;
    public static boolean map3;
    public static boolean map4;

    static {
        RoundStartListener.i = 30;
        RoundStartListener.map1 = false;
        RoundStartListener.map2 = false;
        RoundStartListener.map3 = false;
        RoundStartListener.map4 = false;
    }

    public RoundStartListener(final Duell main) {
        RoundStartListener.main = main;
    }

    public static Location getpos(final int player, final int map) {
        Location loc;
        final String world = RoundStartListener.main.getConfig().getString("1vs1" + map + "p" + player + ".world");
        final double x = RoundStartListener.main.getConfig().getDouble("1vs1" + map + "p" + player + ".x");
        final double y = RoundStartListener.main.getConfig().getDouble("1vs1" + map + "p" + player + ".y");
        final double z = RoundStartListener.main.getConfig().getDouble("1vs1" + map + "p" + player + ".z");
        final float yaw = (float) RoundStartListener.main.getConfig().getDouble("1vs1" + map + "p" + player + ".yaw");
        final float pitch = (float) RoundStartListener.main.getConfig().getDouble("1vs1" + map + "p" + player + ".pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    @SuppressWarnings({"deprecation", "unused"})
    public static void startVSCountDown(final Player p) {
        Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r30 \u00a77Sekunden.");
        final BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(RoundStartListener.main, () -> {
            if (Duell.online.size() == 2) {
                --RoundStartListener.i;
                for (final Player all : Duell.online) {
                    all.setLevel(RoundStartListener.i);
                }
                if (RoundStartListener.i == 15) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r15 \u00a77Sekunden.");
                }
                if (RoundStartListener.i == 10) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r10 \u00a77Sekunden.");
                }
                if (RoundStartListener.i == 5) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r5 \u00a77Sekunden.");
                }
                if (RoundStartListener.i == 4) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r4 \u00a77Sekunden.");
                }
                if (RoundStartListener.i == 3) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r3 \u00a77Sekunden.");
                }
                if (RoundStartListener.i == 2) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r2 \u00a77Sekunden.");
                }
                if (RoundStartListener.i == 1) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r1 \u00a77Sekunden.");
                }
                if (RoundStartListener.i == 0) {
                    Bukkit.getScheduler().cancelAllTasks();
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde wurde gestartet.");
                    RoundStartListener.main.Lobby = false;
                    RoundStartListener.main.InGame = true;
                    RoundStartListener.main.noPvP = false;
                    RoundStartListener.main.motd = false;
                    RoundStartListener.main.RoundTimer();
                    final Random ran = new Random();
                    final int ranint = ran.nextInt(3);
                    if (ranint == 2) {
                        int o = 0;
                        for (final Player all2 : Duell.online) {
                            ++o;
                            all2.teleport(RoundStartListener.getpos(o, 2));
                            final String worldname = p.getWorld().getName();
                            all2.sendTitle("\u00a79Runde 1", "\u00a77Start!");
                            InventoryManager.addVSInventory(all2);
                        }
                        RoundStartListener.map2 = true;
                        BoardManager.Mapname = RoundStartListener.main.vs2m;
                        for (final Player all2 : Duell.online) {
                            all2.setLevel(0);
                            BoardManager.sendInGameBoard(all2);
                        }
                    }
                    if (ranint == 1) {
                        int o = 0;
                        for (final Player all2 : Duell.online) {
                            ++o;
                            all2.teleport(RoundStartListener.getpos(o, 1));
                            final String worldname = p.getWorld().getName();
                            all2.sendTitle("\u00a79Runde 1", "\u00a77Start!");
                            InventoryManager.addVSInventory(all2);
                        }
                        RoundStartListener.map1 = true;
                        BoardManager.Mapname = RoundStartListener.main.vs1m;
                        for (final Player all2 : Duell.online) {
                            all2.setLevel(0);
                            BoardManager.sendInGameBoard(all2);
                        }
                    }
                    if (ranint == 0) {
                        int o = 0;
                        for (final Player all2 : Duell.online) {
                            ++o;
                            all2.teleport(RoundStartListener.getpos(o, 4));
                            all2.sendTitle("\u00a79Runde 1", "\u00a77Start!");
                            InventoryManager.addVSInventory(all2);
                        }
                        RoundStartListener.map4 = true;
                        BoardManager.Mapname = RoundStartListener.main.vs4m;
                        for (final Player all2 : Duell.online) {
                            all2.setLevel(0);
                            BoardManager.sendInGameBoard(all2);
                        }
                    }
                    RoundStartListener.main.addCooldown();
                }
            } else {
                Bukkit.getScheduler().cancelAllTasks();
                Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7cEs sind nicht genügend Spieler online.");
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    all.setLevel(0);
                }
                RoundStartListener.i = 30;
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        if (RoundStartListener.main.vs && !RoundStartListener.main.full && Duell.online.size() == 2) {
            RoundStartListener.main.full = true;
            startVSCountDown(p);
        }
    }
}
