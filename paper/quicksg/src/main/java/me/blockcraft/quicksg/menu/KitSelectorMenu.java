package me.blockcraft.quicksg.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.quicksg.kit.Kit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

final class KitSelectorMenu extends Menu {

    private final KitMenus menus;

    KitSelectorMenu(KitMenus menus, Set<Kit> owned) {
        super(1, menus.messages().get("menu.kits.title"));
        this.menus = menus;
        for (var kit : Kit.values()) {
            var icon = Items.of(kit.icon()).name(menus.messages().get("menu.kits.entry", menus.kitName(kit)));
            if (!kit.free() && !owned.contains(kit)) {
                icon.glow();
            }
            set(kit.ordinal(), icon.build());
        }
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        Kit.bySlot(slot).ifPresentOrElse(kit -> menus.openDetail(player, kit), player::closeInventory);
    }
}
