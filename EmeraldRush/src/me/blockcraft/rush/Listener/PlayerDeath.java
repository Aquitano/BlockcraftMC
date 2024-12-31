package me.blockcraft.rush.Listener;

import me.blockcraft.coins.CAPI;
import me.blockcraft.rush.Main;
import me.blockcraft.rush.MySQL.StatsAPI;
import me.blockcraft.rush.RushManager;
import me.blockcraft.rush.Scoreboards.Scoreboard_DM;
import me.blockcraft.rush.tools.OnlineAbfrage;
import me.blockcraft.rush.tools.Spectator;
import me.blockcraft.rush.tools.Timer_Shopping;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public class PlayerDeath implements Listener {
    @EventHandler
    public void on(final PlayerDeathEvent e) {
        e.setDeathMessage("");
        final Player p = e.getEntity();
        final Player death = e.getEntity();
        final Player killer = e.getEntity().getKiller();
        e.getDrops().clear();
        if (Main.State_DM) {
            if (e.getEntity().getKiller() != null) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7f" + death.getPlayerListName() + "\u00a77 wurde von " + "\u00a7f" + killer.getPlayerListName() + "\u00a77 getötet.");
                CAPI.addCoins(killer.getUniqueId().toString(), 5);
                StatsAPI.setKills(killer.getUniqueId().toString(), StatsAPI.getKills(killer.getUniqueId().toString()) + 1, killer.getName());
                StatsAPI.setDeaths(death.getUniqueId().toString(), StatsAPI.getDeaths(death.getUniqueId().toString()) + 1, killer.getName());
                StatsAPI.setCoins(killer.getUniqueId().toString(), StatsAPI.getCoins(killer.getUniqueId().toString()) + 5, killer.getName());
                killer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1));
                RushManager.removeLeben(death, 1);
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    Scoreboard_DM.add(all);
                }
                if (RushManager.getLeben(death) == 0) {
                    Spectator.add(death);
                    OnlineAbfrage.abfragen(p);
                    StatsAPI.setLose(death.getUniqueId().toString(), StatsAPI.getLoses(death.getUniqueId().toString()) + 1, death.getName());
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.showPlayer(all);
                    }
                }
            } else if (e.getEntity().getKiller() == null) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a7f" + death.getPlayerListName() + "\u00a7f" + " \u00a77ist gestorben");
                StatsAPI.setDeaths(death.getUniqueId().toString(), StatsAPI.getDeaths(death.getUniqueId().toString()) + 1, death.getName());
                RushManager.removeLeben(death, 1);
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    Scoreboard_DM.add(all);
                }
                if (RushManager.getLeben(death) == 0) {
                    Spectator.add(death);
                    OnlineAbfrage.abfragen(p);
                    StatsAPI.setLose(death.getUniqueId().toString(), StatsAPI.getLoses(death.getUniqueId().toString()) + 1, death.getName());
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        all.showPlayer(all);
                    }
                }
            }
        }
    }

    @EventHandler
    public void on(final PlayerRespawnEvent e) {
        final Player p = e.getPlayer();
        if (Main.State_DM && (RushManager.getLeben(p) == 3 || RushManager.getLeben(p) == 2 || RushManager.getLeben(p) == 1)) {
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 120, 5));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 5));
                p.getInventory().setContents(Timer_Shopping.invcontents.get(p));
                p.getInventory().setArmorContents(Timer_Shopping.armorContents.get(p));
                final Random ran = new Random();
                final int ranint = ran.nextInt(6);
                if (ranint == 5) {
                    p.teleport(RushManager.getLocation("DM6"));
                }
                if (ranint == 4) {
                    p.teleport(RushManager.getLocation("DM5"));
                }
                if (ranint == 3) {
                    p.teleport(RushManager.getLocation("DM4"));
                }
                if (ranint == 2) {
                    p.teleport(RushManager.getLocation("DM3"));
                }
                if (ranint == 1) {
                    p.teleport(RushManager.getLocation("DM2"));
                }
                if (ranint == 0) {
                    p.teleport(RushManager.getLocation("DM1"));
                }
            }, 20L);
        }
    }
}
