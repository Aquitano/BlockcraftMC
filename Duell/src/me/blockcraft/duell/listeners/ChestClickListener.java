package me.blockcraft.duell.listeners;

import me.blockcraft.duell.Duell;
import me.blockcraft.duell.methods.StackManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ChestClickListener implements Listener {
    public static Duell main;

    public ChestClickListener(final Duell main) {
        ChestClickListener.main = main;
    }

    @EventHandler
    public void onClick(final PlayerInteractEvent e) {
        final Player p = e.getPlayer();
        if (p.getItemInHand().getType() == Material.CHEST) {
            if (e.getAction() == Action.RIGHT_CLICK_AIR) {
                final Inventory inv = Bukkit.createInventory(null, 9, "Spielmodi");
                if (ChestClickListener.main.voted) {
                    final ItemStack voting = StackManager.Stack("\u00a78● \u00a7cVOTING BEENDET \u00a78●", Material.BARRIER, "\u00a78» \u00a77Das Voting wurde bereits beendet", 1, (short) 0);
                    inv.setItem(4, voting);
                    p.openInventory(inv);
                } else {
                    final ItemStack vs = StackManager.Stack("\u00a78● \u00a791vs1 \u00a78●", Material.IRON_SWORD, "\u00a78» \u00a77Normales 1vs1", 1, (short) 0);
                    final ItemStack SG = StackManager.Stack("\u00a78● \u00a79SurvivalGames \u00a78●", Material.CHAINMAIL_CHESTPLATE, "\u00a78» \u00a77Syndicate Stil", 1, (short) 0);
                    final ItemStack SJ = StackManager.Stack("\u00a78● \u00a79SuperJump \u00a78●", Material.GOLD_BOOTS, "\u00a78» \u00a77Jump 1vs1", 1, (short) 0);
                    inv.setItem(0, vs);
                    inv.setItem(1, SG);
                    inv.setItem(2, SJ);
                    p.openInventory(inv);
                }
            } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                final Inventory inv = Bukkit.createInventory(null, 9, "Spielmodi");
                if (ChestClickListener.main.voted) {
                    final ItemStack voting = StackManager.Stack("\u00a78● \u00a7cVOTING BEENDET \u00a78●", Material.BARRIER, "\u00a78» \u00a77Das Voting wurde bereits beendet", 1, (short) 0);
                    inv.setItem(4, voting);
                    p.openInventory(inv);
                } else {
                    final ItemStack vs = StackManager.Stack("\u00a78● \u00a791vs1 \u00a78●", Material.IRON_SWORD, "\u00a78» \u00a77Normales 1vs1", 1, (short) 0);
                    final ItemStack SG = StackManager.Stack("\u00a78● \u00a79SurvivalGames \u00a78●", Material.CHAINMAIL_CHESTPLATE, "\u00a78» \u00a77Syndicate Stil", 1, (short) 0);
                    final ItemStack SJ = StackManager.Stack("\u00a78● \u00a79SuperJump \u00a78●", Material.GOLD_BOOTS, "\u00a78» \u00a77Jump 1vs1", 1, (short) 0);
                    inv.setItem(0, vs);
                    inv.setItem(1, SG);
                    inv.setItem(2, SJ);
                    p.openInventory(inv);
                }
            }
        }
        if (p.getItemInHand().getType() == Material.SLIME_BALL && !ChestClickListener.main.InGame) {
            if (e.getAction() == Action.RIGHT_CLICK_AIR) {
                Duell.connect(p, "lobby");
            } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Duell.connect(p, "lobby");
            }
        }
    }
}
