package me.blockcraft.quicksg.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.quicksg.storage.StatColumn;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

final class GlobalStatsMenu extends Menu {

    private static final Map<Integer, StatColumn> SLOTS = Map.of(
            0, StatColumn.WINS,
            2, StatColumn.DEATHS,
            4, StatColumn.KILLS,
            6, StatColumn.LOSSES,
            8, StatColumn.POINTS);

    private final StatsMenus menus;

    GlobalStatsMenu(StatsMenus menus) {
        super(1, menus.messages().get("menu.global-stats.title"));
        this.menus = menus;
        SLOTS.forEach((slot, column) -> set(slot, Items.of(Material.PAPER)
                .name(menus.messages().get("menu.global-stats." + column.name().toLowerCase()))
                .build()));
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        player.closeInventory();
        var column = SLOTS.get(slot);
        if (column != null) {
            menus.topList().send(player, column);
        }
    }
}
