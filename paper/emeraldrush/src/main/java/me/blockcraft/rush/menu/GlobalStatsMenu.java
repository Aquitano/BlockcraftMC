package me.blockcraft.rush.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.storage.StatCategory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

final class GlobalStatsMenu extends Menu {

    private static final Map<Integer, StatCategory> SLOTS = Map.of(
            0, StatCategory.WINS,
            2, StatCategory.DEATHS,
            4, StatCategory.KILLS,
            6, StatCategory.LOSES,
            8, StatCategory.POINTS);

    private final RushMenus menus;

    GlobalStatsMenu(RushMenus menus, Messages messages) {
        super(1, messages.get("stats-menu.global-title"));
        this.menus = menus;
        SLOTS.forEach((slot, category) ->
                set(slot, Items.of(Material.PAPER).name(messages.get("stats-menu." + category.key())).build()));
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        player.closeInventory();
        var category = SLOTS.get(slot);
        if (category != null) {
            menus.showTop(player, category);
        }
    }
}
