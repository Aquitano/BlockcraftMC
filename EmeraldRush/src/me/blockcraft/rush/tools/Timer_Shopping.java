package me.blockcraft.rush.tools;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.RushManager;
import me.blockcraft.rush.Scoreboards.Scoreboard_DM;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;

public class Timer_Shopping implements Listener {
    public static boolean started;
    public static int i;
    public static int schedulershopping;
    public static HashMap<Player, ItemStack[]> invcontents;
    public static HashMap<Player, ItemStack[]> armorContents;

    static {
        Timer_Shopping.started = false;
        Timer_Shopping.i = 90;
        Timer_Shopping.schedulershopping = 0;
        Timer_Shopping.invcontents = new HashMap<>();
        Timer_Shopping.armorContents = new HashMap<>();
    }

    public static void add() {
        if (!Timer_Shopping.started) {
            Timer_Shopping.started = true;
            Timer_Shopping.schedulershopping = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Main.getInstance(), () -> {
                --Timer_Shopping.i;
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    RushManager.sendActionBar(all, "\u00a76Shopping \u00a77»\u00a7f " + Timer_Shopping.i);
                }
                if (Timer_Shopping.i == 15) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                    }
                    Bukkit.broadcastMessage(Main.Prefix + "Die \u00a7fShopping Phase \u00a77endet in \u00a7f15\u00a77 Sekunden!");
                }
                if (Timer_Shopping.i == 5) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                    }
                    Bukkit.broadcastMessage(Main.Prefix + "Die \u00a7fShopping Phase \u00a77endet in \u00a7f5\u00a77 Sekunden!");
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.hidePlayer(all);
                    }
                }
                if (Timer_Shopping.i == 4) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                    }
                }
                if (Timer_Shopping.i == 3) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                    }
                }
                if (Timer_Shopping.i == 1) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                    }
                }
                if (Timer_Shopping.i == 0) {
                    Bukkit.getScheduler().cancelTask(Timer_Shopping.schedulershopping);
                    Bukkit.broadcastMessage(Main.Prefix + "\u00a77Das Deathmatch hat begonnen!");
                    Main.State_Shopping = false;
                    Main.State_Lobby = false;
                    Main.State_SuchZeit = false;
                    Main.State_DM = true;
                    Timer_DM.start();
                    for (final Player all : Main.online) {
                        all.playSound(all.getLocation(), Sound.LEVEL_UP, 4.0f, 4.0f);
                        all.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 80, 10));
                        Scoreboard_DM.add(all);
                        all.setGameMode(GameMode.SURVIVAL);
                        all.getInventory().remove(Material.CHEST);
                    }
                    int i = 0;
                    for (final Player all2 : Bukkit.getOnlinePlayers()) {
                        ++i;
                        Timer_Shopping.invcontents.put(all2, all2.getInventory().getContents());
                        Timer_Shopping.armorContents.put(all2, all2.getInventory().getArmorContents());
                        all2.teleport(Timer_Shopping.getDMPos(i));
                        all2.getLocation().getChunk().load();
                        if (i == 6) {
                            break;
                        }
                    }
                    for (final Player spec : Main.spectator) {
                        spec.teleport(Timer_Shopping.getDMPos(1));
                    }
                }
            }, 20L, 20L);
        }
    }

    public static Location getDMPos(final int i) {
        Location loc;
        final String world = Main.getInstance().getConfig().getString("DM" + i + ".World");
        final double x = Main.getInstance().getConfig().getDouble("DM" + i + ".X");
        final double y = Main.getInstance().getConfig().getDouble("DM" + i + ".Y");
        final double z = Main.getInstance().getConfig().getDouble("DM" + i + ".Z");
        final float yaw = (float) Main.getInstance().getConfig().getDouble("DM" + i + ".Yaw");
        final float pitch = (float) Main.getInstance().getConfig().getDouble("DM" + i + ".Pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }
}
