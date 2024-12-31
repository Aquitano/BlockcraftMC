package me.blockcraft.duell.listeners.vs;

import me.blockcraft.coins.CAPI;
import me.blockcraft.duell.Duell;
import me.blockcraft.duell.methods.InventoryManager;
import me.blockcraft.duell.methods.StatsAPI;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitScheduler;

public class DeathListenerVS implements Listener {
    public static Duell main;

    public DeathListenerVS(final Duell main) {
        DeathListenerVS.main = main;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onDeath(final PlayerDeathEvent e) {
        final Player p = e.getEntity();
        e.setKeepInventory(true);
        if (DeathListenerVS.main.vs && e.getEntity() != null && e.getEntity().getKiller() != null) {
            e.setDeathMessage(Data.DUELL_PREFIX + "\u00a7r" + p.getName() + "\u00a77 ist gestorben.");
            ++Duell.round;
            final Player k = p.getKiller();
            StatsAPI.addKills(k.getUniqueId().toString(), k.getName(), 1);
            StatsAPI.addDeaths(p.getUniqueId().toString(), p.getName(), 1);
            int u = k.getLevel();
            ++u;
            k.setLevel(u);
            final BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
            scheduler.scheduleSyncDelayedTask(DeathListenerVS.main, () -> p.spigot().respawn(), 5L);
            if (Duell.round == 4) {
                if (p.getLevel() > p.getKiller().getLevel()) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + p.getName() + " \u00a77hat das Duell gewonnen.");
                } else {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + p.getKiller().getName() + " \u00a77hat das Duell gewonnen.");
                    p.getKiller().sendTitle("\u00a7aGewonnen!", "\u00a7r" + p.getKiller().getName() + " \u00a77hat das Duell gewonnen.");
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + p.getKiller().getName() + " \u00a77erhält \u00a7a10 Coins\u00a7f.");
                    CAPI.addCoins(p.getKiller().getUniqueId().toString(), 10);
                    StatsAPI.addWin(p.getKiller().getUniqueId().toString(), p.getKiller().getName(), 1);
                }
                DeathListenerVS.main.noPvP = true;
                Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde wurde beendet.");
                Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Der Server startet in \u00a7c10 \u00a77Sekunden neu..");
                DeathListenerVS.main.RestartCountdown();
                DeathListenerVS.main.CountdownRunning = true;
            } else {
                for (final Player all : Duell.online) {
                    all.sendTitle("\u00a79Runde " + Duell.round, "\u00a77Start!");
                }
            }
        }
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent e) {
        final Player p = e.getPlayer();
        if (DeathListenerVS.main.vs) {
            final BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
            scheduler.scheduleSyncDelayedTask(DeathListenerVS.main, () -> InventoryManager.addVSInventory(p), 5L);
            if (RoundStartListener.map1) {
                e.setRespawnLocation(RoundStartListener.getpos(1, 1));
                for (final Player all : Duell.online) {
                    if (all != p) {
                        all.teleport(RoundStartListener.getpos(2, 1));
                        InventoryManager.addVSInventory(all);
                    }
                }
            }
            if (RoundStartListener.map2) {
                e.setRespawnLocation(RoundStartListener.getpos(1, 2));
                for (final Player all : Duell.online) {
                    if (all != p) {
                        all.teleport(RoundStartListener.getpos(2, 2));
                        InventoryManager.addVSInventory(all);
                    }
                }
            }
            if (RoundStartListener.map3) {
                e.setRespawnLocation(RoundStartListener.getpos(1, 3));
                for (final Player all : Duell.online) {
                    if (all != p) {
                        all.teleport(RoundStartListener.getpos(2, 3));
                        InventoryManager.addVSInventory(all);
                    }
                }
            }
            if (RoundStartListener.map4) {
                e.setRespawnLocation(RoundStartListener.getpos(1, 4));
                for (final Player all : Duell.online) {
                    if (all != p) {
                        all.teleport(RoundStartListener.getpos(2, 4));
                        InventoryManager.addVSInventory(all);
                    }
                }
            }
        }
    }
}
