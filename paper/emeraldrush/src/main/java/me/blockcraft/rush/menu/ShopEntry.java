package me.blockcraft.rush.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.function.Predicate;

record ShopEntry(int slot, ItemStack icon, int price, Predicate<Player> hasCapacity, Consumer<Player> reward) {
}
