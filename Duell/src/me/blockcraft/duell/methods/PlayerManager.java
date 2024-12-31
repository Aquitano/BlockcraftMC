package me.blockcraft.duell.methods;

import me.blockcraft.duell.Duell;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerManager implements Listener {
    public static Duell main;

    public PlayerManager(final Duell main) {
        PlayerManager.main = main;
    }

    public static void setupPlayer(final Player p) {
        final ItemStack Chest = StackManager.Stack("\u00a78● \u00a79Style", Material.CHEST, "", 1, (short) 0);
        final ItemStack Hub = StackManager.Stack("\u00a78● \u00a79Hub", Material.SLIME_BALL, "", 1, (short) 0);
        final ItemStack stack = StackManager.Stack("\u00a78● \u00a79Stats", Material.SKULL_ITEM, "", 1, (short) 3);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setItem(4, Chest);
        p.getInventory().setItem(8, Hub);
        p.getInventory().setItem(0, stack);
        p.teleport(PlayerManager.main.getSpawn());
        p.setLevel(0);
        p.setFireTicks(0);
    }

    @EventHandler
    public void onJoin(final AsyncPlayerChatEvent e) {
        final Player p = e.getPlayer();
        e.setCancelled(!Duell.online.contains(p));
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        e.setJoinMessage("");
        setupPlayer(p);
        p.teleport(PlayerManager.main.getSpawn());
        if (PlayerManager.main.full) {
            p.setGameMode(GameMode.SPECTATOR);
            p.sendMessage(Data.DUELL_PREFIX + "\u00a77Du bist als Spectator gejoint.");
        } else {
            p.setGameMode(GameMode.SURVIVAL);
            setupPlayer(p);
            Duell.online.add(p);
            Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + p.getName() + " \u00a77hat den Server betreten. \u00a7r" + Duell.online.size() + "/2");
        }
        if (Duell.online.size() == 2) {
            PlayerManager.main.full = true;
        }
        if (PlayerManager.main.voted) {
            for (final Player all : Duell.online) {
                BoardManager.sendVotedBoard(all);
            }
        } else {
            for (final Player all : Duell.online) {
                BoardManager.sendLobbyBoard(all);
            }
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent e) {
        final Player p = e.getPlayer();
        e.setQuitMessage("");
        if (Duell.online.contains(p)) {
            Duell.online.remove(p);
            Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + p.getName() + " \u00a77hat den Server verlassen. \u00a7r" + Duell.online.size() + "/2");
        }
        if (PlayerManager.main.InGame && (Duell.online.size() == 1 || Duell.online.isEmpty())) {
            for (final Player all : Duell.online) {
                Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a7r" + all.getName() + " \u00a77hat das Duell gewonnen.");
            }
            Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Die Runde wurde beendet.");
            Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Der Server startet in \u00a7c10 \u00a77Sekunden neu.");
            PlayerManager.main.RestartCountdown();
            PlayerManager.main.CountdownRunning = true;
        }
    }
}
