package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.cosmetic.Shirt;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class ShirtsMenu extends Menu {

    private static final int BACK_SLOT = 8;

    private final Menus menus;

    ShirtsMenu(Menus menus, Player viewer) {
        super(1, menus.messages().get("shirts.title"));
        this.menus = menus;
        for (Shirt shirt : Shirt.values()) {
            set(shirt.ordinal(), item(shirt, menus.shirts().has(viewer.getUniqueId(), shirt.key())));
        }
        set(BACK_SLOT, Items.of(Material.BARRIER).name(menus.messages().get("shop.back")).build());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == BACK_SLOT) {
            menus.openShop(player);
            return;
        }
        if (slot >= Shirt.values().length) {
            return;
        }
        Shirt shirt = Shirt.values()[slot];
        close(player);
        if (menus.shirts().has(player.getUniqueId(), shirt.key())) {
            player.getInventory().setChestplate(item(shirt, true));
            player.sendMessage(menus.messages().get("shirts.equipped"));
        } else {
            menus.purchase(player, menus.config().shirtPrice(), () -> menus.shirts().unlock(player.getUniqueId(), shirt.key()));
        }
    }

    private ItemStack item(Shirt shirt, boolean owned) {
        var messages = menus.messages();
        return Items.of(Material.LEATHER_CHESTPLATE).color(shirt.color())
                .name(messages.get("shirts.name." + shirt.key()))
                .lore(owned
                        ? messages.get("shop.owned-lore")
                        : messages.get("shirts.price-lore", Formatter.number("coins", menus.config().shirtPrice())))
                .build();
    }
}
