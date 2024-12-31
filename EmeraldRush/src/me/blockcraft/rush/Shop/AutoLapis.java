package me.blockcraft.rush.Shop;

import me.blockcraft.rush.Main;
import org.bukkit.DyeColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Dye;

public class AutoLapis implements Listener {
    private final ItemStack lapis;
    @SuppressWarnings("unused")
    private Main plugin;

    public AutoLapis(final Main plugin) {
        final Dye d = new Dye();
        d.setColor(DyeColor.BLUE);
        (this.lapis = d.toItemStack()).setAmount(24);
    }

    @EventHandler
    public void openInventoryEvent(final InventoryOpenEvent e) {
        if (e.getInventory() instanceof EnchantingInventory) {
            e.getInventory().setItem(1, this.lapis);
            Main.getInstance().inventories.add((EnchantingInventory) e.getInventory());
        }
    }

    @EventHandler
    public void inventoryClickEvent(final InventoryClickEvent e) {
        if (e.getClickedInventory() instanceof EnchantingInventory && Main.getInstance().inventories.contains(e.getInventory()) && e.getSlot() == 1) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void enchantItemEvent(final EnchantItemEvent e) {
        if (Main.getInstance().inventories.contains(e.getInventory())) {
            e.getInventory().setItem(1, this.lapis);
        }
    }
}
