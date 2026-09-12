package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.config.LobbyLocation;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

final class NavigatorMenu extends Menu {

    private static final Map<Integer, LobbyLocation> TARGETS = Map.of(
            2, LobbyLocation.QSG,
            6, LobbyLocation.RUSH,
            10, LobbyLocation.DUELL,
            13, LobbyLocation.SPAWN,
            16, LobbyLocation.SHOP,
            24, LobbyLocation.JUMP);

    private final Menus menus;

    NavigatorMenu(Menus menus) {
        super(3, menus.messages().get("navigator.title"));
        this.menus = menus;
        var messages = menus.messages();
        var lore = messages.get("navigator.lore");
        set(2, Items.of(Material.IRON_SWORD).name(messages.get("navigator.qsg")).lore(lore).build());
        set(6, Items.of(Material.EMERALD).name(messages.get("navigator.rush")).lore(lore).build());
        set(10, Items.of(Material.CHAINMAIL_CHESTPLATE).name(messages.get("navigator.duell")).lore(lore).build());
        set(13, Items.of(Material.ENDER_EYE).name(messages.get("navigator.spawn")).lore(lore).build());
        set(16, Items.of(Material.GOLD_INGOT).name(messages.get("navigator.shop")).lore(lore).build());
        set(20, Items.of(Material.IRON_BARS).name(messages.get("navigator.filler")).build());
        set(24, Items.of(Material.DIAMOND_BOOTS).name(messages.get("navigator.jump")).lore(lore).build());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        player.closeInventory();
        LobbyLocation target = TARGETS.get(slot);
        if (target != null) {
            menus.warp(player, target);
        }
    }
}
