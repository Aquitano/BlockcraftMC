package me.blockcraft.duell.listeners.sg;

import me.blockcraft.duell.Duell;
import me.blockcraft.duell.methods.BoardManager;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Random;

public class RoundStartListenerSG implements Listener {
    public static int i;
    public static boolean map1;
    public static boolean map2;
    public static boolean map3;
    public static Duell main;

    static {
        RoundStartListenerSG.i = 30;
        RoundStartListenerSG.map1 = false;
        RoundStartListenerSG.map2 = false;
        RoundStartListenerSG.map3 = false;
    }

    public RoundStartListenerSG(final Duell main) {
        RoundStartListenerSG.main = main;
    }

    public static Location getposSG(final int player, final int map) {
        Location loc;
        final String world = RoundStartListenerSG.main.getConfig().getString("SG" + map + "p" + player + ".world");
        final double x = RoundStartListenerSG.main.getConfig().getDouble("SG" + map + "p" + player + ".x");
        final double y = RoundStartListenerSG.main.getConfig().getDouble("SG" + map + "p" + player + ".y");
        final double z = RoundStartListenerSG.main.getConfig().getDouble("SG" + map + "p" + player + ".z");
        final float yaw = (float) RoundStartListenerSG.main.getConfig().getDouble("SG" + map + "p" + player + ".yaw");
        final float pitch = (float) RoundStartListenerSG.main.getConfig().getDouble("SG" + map + "p" + player + ".pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public static void startSGCountDown(final Player p) {
        Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r30 \u00a77Sekunden.");
        final BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(RoundStartListenerSG.main, () -> {
            if (Duell.online.size() == 2) {
                --RoundStartListenerSG.i;
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    all.setLevel(RoundStartListenerSG.i);
                }
                if (RoundStartListenerSG.i == 15) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r15 \u00a77Sekunden.");
                }
                if (RoundStartListenerSG.i == 10) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r10 \u00a77Sekunden.");
                }
                if (RoundStartListenerSG.i == 5) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r5 \u00a77Sekunden.");
                }
                if (RoundStartListenerSG.i == 4) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r4 \u00a77Sekunden.");
                }
                if (RoundStartListenerSG.i == 3) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r3 \u00a77Sekunden.");
                }
                if (RoundStartListenerSG.i == 2) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r2 \u00a77Sekunden.");
                }
                if (RoundStartListenerSG.i == 1) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde startet in \u00a7r1 \u00a77Sekunden.");
                }
                if (RoundStartListenerSG.i == 0) {
                    RoundStartListenerSG.main.Lobby = false;
                    RoundStartListenerSG.main.noPvP = false;
                    Bukkit.getScheduler().cancelAllTasks();
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde wurde gestartet.");
                    RoundStartListenerSG.main.InGame = true;
                    RoundStartListenerSG.main.motd = false;
                    RoundStartListenerSG.main.RoundTimer();
                    final Random ran = new Random();
                    final int ranint = ran.nextInt(3);
                    if (ranint == 2) {
                        int o = 0;
                        for (final Player all2 : Duell.online) {
                            ++o;
                            all2.teleport(RoundStartListenerSG.getposSG(o, 2));
                            BoardManager.Mapname = RoundStartListenerSG.main.sg2m;
                            all2.sendTitle("\u00a79SurvivalGames", "\u00a77Start!");
                            all2.getInventory().clear();
                        }
                        RoundStartListenerSG.map2 = true;
                        for (final Player all2 : Duell.online) {
                            all2.setLevel(0);
                            BoardManager.sendInGameBoard(all2);
                        }
                    }
                    if (ranint == 1) {
                        int o = 0;
                        for (final Player all2 : Duell.online) {
                            ++o;
                            all2.teleport(RoundStartListenerSG.getposSG(o, 1));
                            BoardManager.Mapname = RoundStartListenerSG.main.sg1m;
                            all2.sendTitle("\u00a79SurvivalGames", "\u00a77Start!");
                            all2.getInventory().clear();
                        }
                        RoundStartListenerSG.map1 = true;
                        for (final Player all2 : Duell.online) {
                            all2.setLevel(0);
                            BoardManager.sendInGameBoard(all2);
                        }
                    }
                    if (ranint == 0) {
                        int o = 0;
                        for (final Player all2 : Duell.online) {
                            ++o;
                            all2.teleport(RoundStartListenerSG.getposSG(o, 1));
                            BoardManager.Mapname = RoundStartListenerSG.main.sg2m;
                            all2.sendTitle("\u00a79SurvivalGames", "\u00a77Start!");
                            all2.getInventory().clear();
                        }
                        RoundStartListenerSG.map1 = true;
                        for (final Player all2 : Duell.online) {
                            all2.setLevel(0);
                            BoardManager.sendInGameBoard(all2);
                        }
                    }
                    RoundStartListenerSG.main.addCooldown();
                }
            } else {
                Bukkit.getScheduler().cancelAllTasks();
                Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7cEs sind nicht genügend Spieler online.");
                for (final Player all : Duell.online) {
                    all.setLevel(0);
                }
                RoundStartListenerSG.i = 30;
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        if (RoundStartListenerSG.main.SG && !RoundStartListenerSG.main.full && Duell.online.size() == 2) {
            startSGCountDown(p);
            RoundStartListenerSG.main.full = true;
        }
    }
}
