package me.blockcraft.duell.listeners;

import me.blockcraft.duell.Duell;
import me.blockcraft.duell.listeners.sg.RoundStartListenerSG;
import me.blockcraft.duell.listeners.superjump.RoundStartListenerSJ;
import me.blockcraft.duell.listeners.vs.RoundStartListener;
import me.blockcraft.duell.methods.BoardManager;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ChestInvClickListener implements Listener {
    public static Duell main;

    public ChestInvClickListener(final Duell main) {
        ChestInvClickListener.main = main;
    }

    @EventHandler
    public void onClickInv(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getClickedInventory() == null) {
            e.getWhoClicked().closeInventory();
            return;
        }
        if (p.getOpenInventory().getTopInventory() == null) {
            return;
        }
        if (e.getClickedInventory() == null) {
            return;
        }
        if (e.getCurrentItem() == null) {
            return;
        }
        if (e.getCurrentItem().getItemMeta() == null) {
            return;
        }
        if (e.getClickedInventory().getName().equals("Spielmodi")) {
            e.setCancelled(true);
            if (!ChestInvClickListener.main.voted) {
                if (e.getCurrentItem().getType() == Material.IRON_SWORD) {
                    e.getView().close();
                    ChestInvClickListener.main.voted = true;
                    ChestInvClickListener.main.vs = true;
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Das Voting wurde beendet.");
                    Duell.currentMode = "1vs1";
                    for (final Player all : Duell.online) {
                        BoardManager.sendVotedBoard(all);
                    }
                    if (Duell.online.size() == 2) {
                        RoundStartListener.startVSCountDown(p);
                    }
                } else if (e.getCurrentItem().getType() == Material.CHAINMAIL_CHESTPLATE) {
                    e.getView().close();
                    ChestInvClickListener.main.voted = true;
                    ChestInvClickListener.main.SG = true;
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Das Voting wurde beendet.");
                    Duell.currentMode = "SurvivalGames";
                    for (final Player all : Duell.online) {
                        BoardManager.sendVotedBoard(all);
                    }
                    if (Duell.online.size() == 2) {
                        RoundStartListenerSG.startSGCountDown(p);
                    }
                } else if (e.getCurrentItem().getType() == Material.GOLD_BOOTS) {
                    e.getView().close();
                    ChestInvClickListener.main.voted = true;
                    ChestInvClickListener.main.SJ = true;
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX + "\u00a77Das Voting wurde beendet.");
                    Duell.currentMode = "SuperJump";
                    for (final Player all : Duell.online) {
                        BoardManager.sendVotedBoard(all);
                    }
                    if (Duell.online.size() == 2) {
                        RoundStartListenerSJ.startSJCountdown(p);
                    }
                } else if (e.getCurrentItem().getType() == Material.BARRIER) {
                    e.getView().close();
                }
            } else {
                e.getView().close();
                p.sendMessage(Data.DUELL_PREFIX + "\u00a77Das Voting wurde bereits beendet.");
            }
        }
    }
}
