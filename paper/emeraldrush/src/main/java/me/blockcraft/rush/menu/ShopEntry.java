package me.blockcraft.rush.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

record ShopEntry(int slot, ItemStack icon, int price, Consumer<Player> reward) {
}
