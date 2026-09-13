package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.cosmetic.Head;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

final class HeadsMenu extends Menu {

    private static final int BACK_SLOT = 26;

    private final Menus menus;
    private final Set<String> owned;

    HeadsMenu(Menus menus, Set<String> owned) {
        super(3, menus.messages().get("heads.title"));
        this.menus = menus;
        this.owned = owned;
        for (Head head : Head.values()) {
            set(head.ordinal(), item(head, owned.contains(head.item())));
        }
        backButton(BACK_SLOT, menus::openShop);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot >= Head.values().length) {
            return;
        }
        Head head = Head.values()[slot];
        if (owned.contains(head.item())) {
            close(player);
            equip(player, head);
        } else {
            menus.purchase(player, item(head, false), head.item(), menus.config().headPrice(), buyer -> equip(buyer, head));
        }
    }

    private void equip(Player player, Head head) {
        player.getInventory().setHelmet(item(head, true));
        player.sendMessage(menus.messages().get("heads.equipped"));
    }

    private ItemStack item(Head head, boolean owned) {
        var messages = menus.messages();
        return Items.head(head.owner())
                .name(messages.get("heads.name." + head.key()))
                .lore(owned
                        ? messages.get("shop.owned-lore")
                        : messages.get("shop.price-lore", Formatter.number("coins", menus.config().headPrice())))
                .build();
    }
}
