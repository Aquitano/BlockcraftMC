package me.blockcraft.rush.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.kit.Perk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

final class PerkMenu extends Menu {

    private static final Perk[] PERKS = Perk.values();

    private final RushMenus menus;

    PerkMenu(RushMenus menus, Messages messages, Set<Perk> owned) {
        super(1, messages.get("perk.title"));
        this.menus = menus;
        for (int slot = 0; slot < PERKS.length; slot++) {
            var perk = PERKS[slot];
            var icon = Items.of(perk.icon()).name(messages.get("perk.item", RushMenus.perkName(messages, perk)));
            if (!owned.contains(perk)) {
                icon.glow();
            }
            set(slot, icon.build());
        }
        set(8, Items.of(Material.OAK_SIGN).name(messages.get("perk.explain")).lore(messages.getLines("perk.explain-lore")).build());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot < PERKS.length) {
            menus.openPerk(player, PERKS[slot]);
        } else {
            player.closeInventory();
        }
    }
}
