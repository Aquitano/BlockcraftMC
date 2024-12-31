package me.blockcraft.duell.listeners.superjump;

import me.blockcraft.coins.CAPI;
import me.blockcraft.duell.Duell;
import me.blockcraft.duell.methods.StatsAPI;
import me.eventseen.Data.Data;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

public class SuperJumpMoveListener implements Listener {
    public static Duell main;
    public static ArrayList<Player> check1;
    public static ArrayList<Player> check2;
    public static Location spawn;

    static {
        SuperJumpMoveListener.check1 = new ArrayList<>();
        SuperJumpMoveListener.check2 = new ArrayList<>();
        SuperJumpMoveListener.spawn = null;
    }

    boolean roundEnd;

    public SuperJumpMoveListener(final Duell main) {
        this.roundEnd = false;
        SuperJumpMoveListener.main = main;
    }

    public static Location getpos(final Player p) {
        Location loc;
        final String world = SuperJumpMoveListener.main.getConfig().getString(p.getName() + ".world");
        final double x = SuperJumpMoveListener.main.getConfig().getDouble(p.getName() + ".x");
        final double y = SuperJumpMoveListener.main.getConfig().getDouble(p.getName() + ".y");
        final double z = SuperJumpMoveListener.main.getConfig().getDouble(p.getName() + ".z");
        final float yaw = (float) SuperJumpMoveListener.main.getConfig().getDouble(p.getName() + ".yaw");
        final float pitch = (float) SuperJumpMoveListener.main.getConfig().getDouble(p.getName() + ".pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onMove(final PlayerMoveEvent e) {
        final Player p = e.getPlayer();
        if (SuperJumpMoveListener.main.InGame && SuperJumpMoveListener.main.SJ) {
            final Location loc = e.getPlayer().getLocation();
            final Block b = p.getWorld().getBlockAt(loc);
            final Block block = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
            if (b.getType() == Material.IRON_PLATE && Duell.online.contains(p)) {
                if (SuperJumpMoveListener.check1.contains(p)) {
                    return;
                }
                SuperJumpMoveListener.check1.add(p);
                Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + p.getName() + "\u00a77 hat Checkpoint \u00a7r1 \u00a77erreicht!");
                p.setBedSpawnLocation(p.getLocation(), true);
                p.playSound(p.getLocation(), Sound.SUCCESSFUL_HIT, 5.0f, 10.0f);
                p.playEffect(p.getLocation(), Effect.MOBSPAWNER_FLAMES, 5);
            } else if (b.getType() == Material.GOLD_PLATE && Duell.online.contains(p)) {
                if (SuperJumpMoveListener.check2.contains(p)) {
                    return;
                }
                SuperJumpMoveListener.check1.remove(p);
                SuperJumpMoveListener.check2.add(p);
                Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + p.getName() + "\u00a77 hat Checkpoint \u00a7r2 \u00a77erreicht!");
                p.setBedSpawnLocation(p.getLocation(), true);
                p.playSound(p.getLocation(), Sound.SUCCESSFUL_HIT, 5.0f, 10.0f);
                p.playEffect(p.getLocation(), Effect.MOBSPAWNER_FLAMES, 5);
            } else {
                if (block.getType() == Material.DIAMOND_BLOCK) {
                    if (this.roundEnd) {
                        return;
                    }
                    if (Duell.online.contains(p)) {
                        this.roundEnd = true;
                        Bukkit.broadcastMessage(" ");
                        Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + p.getName() + "\u00a77 hat das \u00a7bZiel \u00a77erreicht!");
                        p.sendTitle("\u00a7aGewonnen!", "\u00a7r" + p.getName() + " \u00a77hat das Duell gewonnen.");
                        Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + p.getName() + " \u00a77erhält \u00a7a10 Coins\u00a7f.");
                        CAPI.addCoins(p.getUniqueId().toString(), 10);
                        StatsAPI.addWin(p.getUniqueId().toString(), p.getName(), 1);
                        Bukkit.broadcastMessage(" ");
                        for (final Player all : Duell.online) {
                            if (!Objects.equals(all.getName(), p.getName())) {
                                all.playSound(all.getLocation(), Sound.BLAZE_DEATH, 5.0f, 10.0f);
                            }
                        }
                        p.playSound(p.getLocation(), Sound.EXPLODE, 5.0f, 10.0f);
                        p.playEffect(p.getLocation(), Effect.EXPLOSION, 5);
                        p.setBedSpawnLocation(p.getLocation(), true);
                        SuperJumpMoveListener.main.RestartCountdown();
                        SuperJumpMoveListener.main.CountdownRunning = true;
                        return;
                    }
                }
                if ((p.getLocation().getBlock().getType() == Material.STATIONARY_WATER || p.getLocation().getBlock().getType() == Material.STATIONARY_LAVA || p.getLocation().getBlock().getType() == Material.WATER || p.getLocation().getBlock().getType() == Material.LAVA) && Duell.online.contains(p)) {
                    if (SuperJumpMoveListener.check1.contains(p)) {
                        p.teleport(p.getBedSpawnLocation());
                        return;
                    }
                    if (SuperJumpMoveListener.check2.contains(p)) {
                        p.teleport(p.getBedSpawnLocation());
                        return;
                    }
                    p.teleport(p.getBedSpawnLocation());
                }
            }
        }
    }

    public void setLocation(final Player p) {
        Duell.fileData.set(p.getName() + ".world", p.getLocation().getWorld().getName());
        Duell.fileData.set(p.getName() + ".x", p.getLocation().getX());
        Duell.fileData.set(p.getName() + ".y", p.getLocation().getY());
        Duell.fileData.set(p.getName() + ".z", p.getLocation().getZ());
        Duell.fileData.set(p.getName() + ".yaw", p.getLocation().getYaw());
        Duell.fileData.set(p.getName() + ".pitch", p.getLocation().getPitch());
        try {
            Duell.fileData.save(Duell.Cache);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
