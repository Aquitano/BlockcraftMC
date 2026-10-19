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
    private final boolean owned;

    PerkBuyMenu(RushMenus menus, Messages messages, Perk perk, boolean owned) {
        super(1, messages.get("perk.buy-title", RushMenus.perkName(messages, perk)));
        this.menus = menus;
        this.perk = perk;
        this.owned = owned;
        info(0, Items.of(perk.icon()).name(messages.get("perk.item", RushMenus.perkName(messages, perk))).build());
        info(1, Items.of(Material.NAME_TAG).name(messages.get("perk.price"))
                .lore(messages.get("perk.price-lore", Formatter.number("price", perk.price()))).build());
        if (owned) {
            info(BUY, Items.of(Material.ORANGE_STAINED_GLASS_PANE).name(messages.get("perk.owned")).build());
        } else {
            set(BUY, Items.of(Material.GREEN_STAINED_GLASS_PANE).name(messages.get("perk.buy")).build());
        }
        backButton(menus::openPerks);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        switch (slot) {
            case BUY -> {
                if (!owned) {
                    menus.buyPerk(player, perk);
                }
            }
            default -> {
            }
        }
    }
}
