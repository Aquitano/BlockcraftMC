package me.blockcraft.api.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.BitSet;
import java.util.function.Consumer;

/**
 * A chest GUI identified by its holder instead of its title. Clicks inside it are cancelled and routed
 * to {@link #onClick} by {@link MenuListener}; clicks in the player's own inventory are cancelled too.
 * <p>
 * Empty slots are filled with a pane when the menu opens and clicks on those are swallowed;
 * {@link #backButton} and {@link #closeButton} place the two buttons every menu shares.
 * <p>
 * {@link #open} and {@link #close} run on the next tick because Bukkit forbids opening or closing an
 * inventory from inside {@link InventoryClickEvent}; the client would otherwise receive the cancel resync
 * for the old window after the new one opened and show ghost items.
 */
public abstract class Menu implements InventoryHolder {

    private static final Plugin API = JavaPlugin.getProvidingPlugin(Menu.class);

    private final Inventory inventory;
    private final BitSet fillerSlots = new BitSet();
    private boolean filled;
    private int backSlot = -1;
    private int closeSlot = -1;
    private Consumer<Player> backAction;

    protected Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    protected abstract void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event);

    protected void onClose(Player player, InventoryCloseEvent event) {
    }

    /** Menus whose layout lives off the gaps between items turn the filler off. */
    protected boolean fillEmptySlots() {
        return true;
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

    /** The way back to the menu that opened this one; the parent is reopened lazily because it may load first. */
    protected void backButton(int slot, Consumer<Player> previous) {
        backSlot = slot;
        backAction = previous;
        set(slot, button(Material.ARROW, "menu.back"));
    }

    protected void closeButton(int slot) {
        closeSlot = slot;
        set(slot, button(Material.BARRIER, "menu.close"));
    }

    public void open(Player player) {
        Bukkit.getScheduler().runTask(API, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (fillEmptySlots() && !filled) {
                fillEmpty();
                filled = true;
            }
            player.openInventory(inventory);
        });
    }

    public static void close(Player player) {
        Bukkit.getScheduler().runTask(API, () -> player.closeInventory());
    }

    @Override
    public final Inventory getInventory() {
        return inventory;
    }

    void handleClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (fillerSlots.get(slot)) {
            return;
        }
        Sounds.MENU_CLICK.play(player);
        if (slot == backSlot) {
            backAction.accept(player);
        } else if (slot == closeSlot) {
            close(player);
        } else {
            onClick(player, slot, clicked, event);
        }
    }

    void handleClose(Player player, InventoryCloseEvent event) {
        onClose(player, event);
    }

    private void fillEmpty() {
        var pane = button(Material.GRAY_STAINED_GLASS_PANE, "menu.filler");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, pane);
                fillerSlots.set(slot);
            }
        }
    }

    private static ItemStack button(Material material, String messageKey) {
        return Items.of(material).name(BlockcraftApi.get().messages().get(messageKey)).build();
    }
}
