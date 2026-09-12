package me.blockcraft.quicksg.game;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marker holder so the close listener can tell generated loot chests from other inventories. */
public final class LootChest implements InventoryHolder {

    private final Inventory inventory;

    LootChest() {
        this.inventory = Bukkit.createInventory(this, 27, Component.translatable("container.chest"));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
