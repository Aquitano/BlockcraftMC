package me.blockcraft.core.staff;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;

/** Another player's inventory opened by /invsee is a window, not a chest, unless the viewer may edit it. */
public final class InvseeGuard implements Listener {

    public static final String EDIT_PERMISSION = "blockcraft.core.invsee.edit";

    public static boolean isReadOnly(Player viewer, InventoryView view) {
        return view.getTopInventory() instanceof PlayerInventory inventory
                && !viewer.equals(inventory.getHolder())
                && !viewer.hasPermission(EDIT_PERMISSION);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player viewer && isReadOnly(viewer, event.getView())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player viewer && isReadOnly(viewer, event.getView())) {
            event.setCancelled(true);
        }
    }
}
