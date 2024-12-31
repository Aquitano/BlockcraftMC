package me.blockcraft.lobby.listeners;

import me.blockcraft.lobby.Lobby;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import static me.blockcraft.lobby.utils.Inventar.addNonDroppableLore;

public class PlayerHider implements Listener {
    public static Lobby main;

    public PlayerHider(final Lobby main) {
        PlayerHider.main = main;
    }

    @SuppressWarnings("unused")
    @EventHandler
    public void onPlayerInteractInLobby2(final PlayerInteractEvent event) {
        final Player p = event.getPlayer();
        final Inventory inv = Bukkit.createInventory(null, 9, PlayerHider.main.y);
        final ItemStack istack2 = new ItemStack(Material.INK_SACK, 1, (short) 8);
        final ItemMeta istackm2 = istack2.getItemMeta();
        istackm2.setLore(addNonDroppableLore("\u00a77Rechtsklick"));
        istackm2.setDisplayName("\u00a78» \u00a79Spieler \u00a78● \u00a7cAUS");
        istack2.setItemMeta(istackm2);
        final ItemStack istack3 = new ItemStack(Material.INK_SACK, 1, (short) 10);
        final ItemMeta istackMeta5411 = istack3.getItemMeta();
        istackMeta5411.setDisplayName("\u00a78» \u00a79Spieler \u00a78● \u00a7aAN");
        istackMeta5411.setLore(addNonDroppableLore("\u00a77Rechtsklick"));
        istack3.setItemMeta(istackMeta5411);
        if (event.getPlayer().getItemInHand().getType() == Material.INK_SACK) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR && event.getPlayer().getItemInHand().getItemMeta().getDisplayName().equals("\u00a78» \u00a79Spieler \u00a78● \u00a7cAUS")) {
                if (!PlayerHider.main.hasCooldown(p)) {
                    PlayerHider.main.addCooldown(p);
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        p.showPlayer(all);
                    }
                    p.getInventory().setItem(1, null);
                    p.getInventory().setItem(1, istack3);
                    p.updateInventory();
                    return;
                }
                p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a7cWarte kurz!");
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getPlayer().getItemInHand().getItemMeta().getDisplayName().equals("\u00a78» \u00a79Spieler \u00a78● \u00a7cAUS")) {
                if (!PlayerHider.main.hasCooldown(p)) {
                    PlayerHider.main.addCooldown(p);
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        p.showPlayer(all);
                    }
                    p.getInventory().setItem(1, null);
                    p.getInventory().setItem(1, istack3);
                    p.updateInventory();
                    return;
                }
                p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a7cWarte kurz!");
            } else if (event.getAction() == Action.RIGHT_CLICK_AIR && event.getPlayer().getItemInHand().getItemMeta().getDisplayName().equals("\u00a78» \u00a79Spieler \u00a78● \u00a7aAN")) {
                if (!PlayerHider.main.hasCooldown(p)) {
                    PlayerHider.main.addCooldown(p);
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        p.hidePlayer(all);
                    }
                    p.getInventory().setItem(1, null);
                    p.getInventory().setItem(1, istack2);
                    p.updateInventory();
                    return;
                }
                p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a7cWarte kurz!");
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getPlayer().getItemInHand().getItemMeta().getDisplayName().equals("\u00a78» \u00a79Spieler \u00a78● \u00a7aAN")) {
                if (!PlayerHider.main.hasCooldown(p)) {
                    PlayerHider.main.addCooldown(p);
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        p.hidePlayer(all);
                    }
                    p.getInventory().setItem(1, null);
                    p.getInventory().setItem(1, istack2);
                    p.updateInventory();
                    return;
                }
                p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a7cWarte kurz!");
            }
        }
    }
}
