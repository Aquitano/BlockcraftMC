package me.blockcraft.lobby.listeners;

import me.blockcraft.lobby.Lobby;
import me.blockcraft.lobby.secrets.Secrets;
import me.blockcraft.lobby.utils.Inventar;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class CompassClickEvent implements Listener {
    public static Lobby main;

    public CompassClickEvent(final Lobby main) {
        CompassClickEvent.main = main;
    }

    @EventHandler
    public void onPlayerInteractInLobby(final PlayerInteractEvent event) {
        final Player p = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getPlayer().getItemInHand().getType() == Material.COMPASS) {
            CompassClickEvent.main.openCompInv(p);
        } else if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getPlayer().getItemInHand().getType() != Material.GOLDEN_CARROT) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR && event.getPlayer().getItemInHand().getType() == Material.COMPASS) {
                CompassClickEvent.main.openCompInv(p);
            } else if (event.getAction() == Action.RIGHT_CLICK_AIR && event.getPlayer().getItemInHand().getType() == Material.COMPASS) {
                CompassClickEvent.main.openCompInv(p);
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getPlayer().getItemInHand().getType() == Material.SPECKLED_MELON) {
                if (!p.hasPermission("bs.premium")) {
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dafür benötigst du \u00a76Premium\u00a77!");
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Mehr erfahren \u00a7e/Premium");
                } else {
                    Inventar.setupPremiumInv(p);
                }
            } else if (event.getAction() == Action.RIGHT_CLICK_AIR && event.getPlayer().getItemInHand().getType() == Material.SPECKLED_MELON) {
                if (!p.hasPermission("bs.premium")) {
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dafür benötigst du \u00a76Premium\u00a77!");
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Mehr erfahren \u00a7e/Premium");
                } else {
                    Inventar.setupPremiumInv(p);
                }
            } else if (event.getAction() == Action.RIGHT_CLICK_AIR && event.getPlayer().getItemInHand().getType() == Material.EMERALD) {
                final Inventory inv = Bukkit.createInventory(null, 9, CompassClickEvent.main.z);
                final ItemStack x = CompassClickEvent.main.Stack("\u00a7c???", Material.INK_SACK, "\u00a78» \u00a77Noch nicht gefunden!", 1, (short) 8);
                if (Secrets.File.contains(p.getName() + ".Zimmer")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aDas Zimmer", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(0, a);
                } else {
                    inv.setItem(0, x);
                }
                if (Secrets.File.contains(p.getName() + ".Wagen")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aAuf dem Wagen", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(1, a);
                } else {
                    inv.setItem(1, x);
                }
                if (Secrets.File.contains(p.getName() + ".Nacht")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aGute Nacht!", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(2, a);
                } else {
                    inv.setItem(2, x);
                }
                if (Secrets.File.contains(p.getName() + ".Abwasser")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aAbwasser?", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(3, a);
                } else {
                    inv.setItem(3, x);
                }
                if (Secrets.File.contains(p.getName() + ".Abfall")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aAbfall", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(4, a);
                } else {
                    inv.setItem(4, x);
                }
                if (Secrets.File.contains(p.getName() + ".Hinrichtung")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aHinrichtung", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(5, a);
                } else {
                    inv.setItem(5, x);
                }
                if (Secrets.File.contains(p.getName() + ".Statue")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aStatue", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(6, a);
                } else {
                    inv.setItem(6, x);
                }
                if (Secrets.File.contains(p.getName() + ".Ruhe")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aRuh dich aus!", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(7, a);
                } else {
                    inv.setItem(7, x);
                }
                if (Secrets.File.contains(p.getName() + ".Abgelegen")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aAbgelegen", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(8, a);
                } else {
                    inv.setItem(8, x);
                }
                p.openInventory(inv);
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getPlayer().getItemInHand().getType() == Material.EMERALD) {
                final Inventory inv = Bukkit.createInventory(null, 9, CompassClickEvent.main.z);
                final ItemStack x = CompassClickEvent.main.Stack("\u00a7c???", Material.INK_SACK, "\u00a78» \u00a77Noch nicht gefunden!", 1, (short) 8);
                if (Secrets.File.contains(p.getName() + ".Zimmer")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aDas Zimmer", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(0, a);
                } else {
                    inv.setItem(0, x);
                }
                if (Secrets.File.contains(p.getName() + ".Wagen")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aAuf dem Wagen", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(1, a);
                } else {
                    inv.setItem(1, x);
                }
                if (Secrets.File.contains(p.getName() + ".Nacht")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aGute Nacht!", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(2, a);
                } else {
                    inv.setItem(2, x);
                }
                if (Secrets.File.contains(p.getName() + ".Abwasser")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aAbwasser?", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(3, a);
                } else {
                    inv.setItem(3, x);
                }
                if (Secrets.File.contains(p.getName() + ".Abfall")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aAbfall", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(4, a);
                } else {
                    inv.setItem(4, x);
                }
                if (Secrets.File.contains(p.getName() + ".Hinrichtung")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aHinrichtung", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(5, a);
                } else {
                    inv.setItem(5, x);
                }
                if (Secrets.File.contains(p.getName() + ".Statue")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aStatue", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(6, a);
                } else {
                    inv.setItem(6, x);
                }
                if (Secrets.File.contains(p.getName() + ".Ruhe")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aRuh dich aus!", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(7, a);
                } else {
                    inv.setItem(7, x);
                }
                if (Secrets.File.contains(p.getName() + ".Abgelegen")) {
                    final ItemStack a = CompassClickEvent.main.Stack("\u00a7aAbgelegen", Material.INK_SACK, "\u00a78» \u00a77Gefunden!", 1, (short) 10);
                    inv.setItem(8, a);
                } else {
                    inv.setItem(8, x);
                }
                p.openInventory(inv);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInvClick(final InventoryClickEvent e) {
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
        if (!e.getInventory().getName().equals(CompassClickEvent.main.x)) {
            if (e.getInventory().getName().equals(CompassClickEvent.main.y)) {
                e.setCancelled(true);
                if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a77Spieler \u00a7aAN")) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        p.showPlayer(all);
                    }
                    e.getView().close();
                    p.playSound(p.getLocation(), Sound.SUCCESSFUL_HIT, 5.0f, 10.0f);
                    p.playEffect(p.getLocation(), Effect.ENDER_SIGNAL, 50);
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a77Spieler \u00a7cAUS")) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        p.hidePlayer(all);
                    }
                    e.getView().close();
                    p.playSound(p.getLocation(), Sound.ANVIL_BREAK, 5.0f, 10.0f);
                    p.playEffect(p.getLocation(), Effect.MOBSPAWNER_FLAMES, 3);
                }
            }
            if (e.getInventory().getName().equals(CompassClickEvent.main.z)) {
                e.setCancelled(true);
                e.getView().close();
            }
            return;
        }
        e.setCancelled(true);
        if (e.getCurrentItem().getType() == Material.GOLD_INGOT) {
            p.teleport(CompassClickEvent.main.getShop());
            p.playSound(p.getLocation(), Sound.ENDERMAN_TELEPORT, 4.0f, 4.0f);
            e.getView().close();
            p.playEffect(p.getLocation(), Effect.MOBSPAWNER_FLAMES, 5);
            return;
        }
        if (e.getCurrentItem().getType() == Material.EYE_OF_ENDER) {
            p.teleport(CompassClickEvent.main.getSpawn());
            p.playSound(p.getLocation(), Sound.ENDERMAN_TELEPORT, 4.0f, 4.0f);
            e.getView().close();
            p.playEffect(p.getLocation(), Effect.MOBSPAWNER_FLAMES, 5);
            return;
        }
        if (e.getCurrentItem().getType() == Material.DIAMOND_BOOTS) {
            p.teleport(CompassClickEvent.main.getJump());
            p.playSound(p.getLocation(), Sound.ENDERMAN_TELEPORT, 4.0f, 4.0f);
            e.getView().close();
            p.playEffect(p.getLocation(), Effect.MOBSPAWNER_FLAMES, 5);
            return;
        }
        if (e.getCurrentItem().getType() == Material.EMERALD) {
            p.teleport(CompassClickEvent.main.getTW());
            p.playSound(p.getLocation(), Sound.ENDERMAN_TELEPORT, 4.0f, 4.0f);
            e.getView().close();
            p.playEffect(p.getLocation(), Effect.MOBSPAWNER_FLAMES, 5);
            return;
        }
        if (e.getCurrentItem().getType() == Material.IRON_SWORD) {
            p.teleport(CompassClickEvent.main.getSlimebrawl());
            p.playSound(p.getLocation(), Sound.ENDERMAN_TELEPORT, 4.0f, 4.0f);
            e.getView().close();
            p.playEffect(p.getLocation(), Effect.MOBSPAWNER_FLAMES, 5);
            return;
        }
        if (e.getCurrentItem().getType() == Material.CHAINMAIL_CHESTPLATE) {
            p.teleport(CompassClickEvent.main.getDuell());
            p.playSound(p.getLocation(), Sound.ENDERMAN_TELEPORT, 4.0f, 4.0f);
            e.getView().close();
            p.playEffect(p.getLocation(), Effect.MOBSPAWNER_FLAMES, 5);
            return;
        }
        e.getView().close();
    }
}
