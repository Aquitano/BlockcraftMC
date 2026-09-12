package me.blockcraft.rush.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.kit.Perk;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class PerkBuyMenu extends Menu {

    private static final int BUY = 6;

    private final RushMenus menus;
    private final Perk perk;

    PerkBuyMenu(RushMenus menus, Messages messages, Perk perk, boolean owned) {
        super(1, messages.get("perk.buy-title", RushMenus.perkName(messages, perk)));
        this.menus = menus;
        this.perk = perk;
        set(0, Items.of(perk.icon()).name(messages.get("perk.item", RushMenus.perkName(messages, perk))).build());
        set(1, Items.of(Material.NAME_TAG).name(messages.get("perk.price"))
                .lore(messages.get("perk.price-lore", Formatter.number("price", perk.price()))).build());
        set(BUY, owned
                ? Items.of(Material.ORANGE_STAINED_GLASS_PANE).name(messages.get("perk.owned")).build()
                : Items.of(Material.GREEN_STAINED_GLASS_PANE).name(messages.get("perk.buy")).build());
        var back = Items.of(Material.RED_STAINED_GLASS_PANE).name(messages.get("perk.back")).build();
        set(7, back);
        set(8, back);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        switch (slot) {
            case BUY -> menus.buyPerk(player, perk);
            case 7, 8 -> menus.openPerks(player);
            default -> {
            }
        }
    }
}
