package me.blockcraft.api.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A chest GUI identified by its holder instead of its title. Clicks inside it are cancelled and routed
 * to {@link #onClick} by {@link MenuListener}; clicks in the player's own inventory are cancelled too.
 * <p>
 * {@link #open} and {@link #close} run on the next tick because Bukkit forbids opening or closing an
 * inventory from inside {@link InventoryClickEvent}; the client would otherwise receive the cancel resync
 * for the old window after the new one opened and show ghost items.
 */
public abstract class Menu implements InventoryHolder {

    private static final Plugin API = JavaPlugin.getProvidingPlugin(Menu.class);

    private final Inventory inventory;

    protected Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    protected abstract void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event);

    protected void onClose(Player player, InventoryCloseEvent event) {
    }

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected void fill(ItemStack item) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, item);
            }
        }
    }

    public void open(Player player) {
        Bukkit.getScheduler().runTask(API, () -> {
            if (player.isOnline()) {
                player.openInventory(inventory);
            }
        });
    }

    public static void close(Player player) {
        Bukkit.getScheduler().runTask(API, () -> player.closeInventory());
    }

    @Override
    public final Inventory getInventory() {
        return inventory;
    }
}
