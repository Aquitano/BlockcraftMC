package me.blockcraft.api.player;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;

public final class InventoryInspection {

    public static final String PERMISSION = "blockcraft.core.invsee";
    public static final String EDIT_PERMISSION = "blockcraft.core.invsee.edit";

    private InventoryInspection() {
    }

    public static boolean permitted(Player viewer, Inventory inventory) {
        return inventory instanceof PlayerInventory target && target.getHolder() != null
                && !viewer.equals(target.getHolder()) && viewer.hasPermission(PERMISSION);
    }
}
