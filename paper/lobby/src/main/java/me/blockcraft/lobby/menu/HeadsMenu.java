package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.cosmetic.Head;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class HeadsMenu extends Menu {

    private final Menus menus;

    HeadsMenu(Menus menus, Player viewer) {
        super(3, menus.messages().get("heads.title"));
        this.menus = menus;
        for (Head head : Head.values()) {
            set(head.ordinal(), item(head, menus.heads().has(viewer.getUniqueId(), head.key())));
        }
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot >= Head.values().length) {
            return;
        }
        Head head = Head.values()[slot];
        var messages = menus.messages();
        int price = menus.config().headPrice();
        close(player);
        if (menus.heads().has(player.getUniqueId(), head.key())) {
            player.getInventory().setHelmet(item(head, true));
            player.sendMessage(messages.get("heads.equipped"));
        } else if (menus.coins().withdraw(player.getUniqueId(), player.getName(), price)) {
            menus.heads().unlock(player.getUniqueId(), head.key());
            player.sendMessage(messages.get("shop.bought"));
            player.setLevel(menus.coins().get(player.getUniqueId()));
        } else {
            player.sendMessage(messages.get("shop.too-expensive", Formatter.number("coins", price)));
        }
    }

    private ItemStack item(Head head, boolean owned) {
        var messages = menus.messages();
        return Items.head(head.owner())
                .name(messages.get("heads.name." + head.key()))
                .lore(owned
                        ? messages.get("shop.owned-lore")
                        : messages.get("heads.price-lore", Formatter.number("coins", menus.config().headPrice())))
                .build();
    }
}
