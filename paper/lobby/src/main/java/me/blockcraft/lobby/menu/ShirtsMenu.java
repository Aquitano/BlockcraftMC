package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.cosmetic.Shirt;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

final class ShirtsMenu extends Menu {

    private static final int BACK_SLOT = 8;

    private final Menus menus;
    private final Set<String> owned;

    ShirtsMenu(Menus menus, Set<String> owned) {
        super(1, menus.messages().get("shirts.title"));
        this.menus = menus;
        this.owned = owned;
        for (Shirt shirt : Shirt.values()) {
            set(shirt.ordinal(), item(shirt, owned.contains(shirt.item())));
        }
        backButton(BACK_SLOT, menus::openShop);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot >= Shirt.values().length) {
            return;
        }
        Shirt shirt = Shirt.values()[slot];
        if (owned.contains(shirt.item())) {
            close(player);
            equip(player, shirt);
        } else {
            menus.purchase(player, item(shirt, false), shirt.item(), menus.config().shirtPrice(), buyer -> equip(buyer, shirt));
        }
    }

    private void equip(Player player, Shirt shirt) {
        player.getInventory().setChestplate(item(shirt, true));
        player.sendMessage(menus.messages().get("shirts.equipped"));
    }

    private ItemStack item(Shirt shirt, boolean owned) {
        var messages = menus.messages();
        return Items.of(Material.LEATHER_CHESTPLATE).color(shirt.color())
                .name(messages.get("shirts.name." + shirt.key()))
                .lore(owned
                        ? messages.get("shop.owned-lore")
                        : messages.get("shop.price-lore", Formatter.number("coins", menus.config().shirtPrice())))
                .build();
    }
}
