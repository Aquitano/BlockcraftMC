package me.blockcraft.quicksg.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.game.Stat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

final class GlobalStatsMenu extends Menu {

    private static final Map<Integer, Stat> SLOTS = Map.of(
            0, Stat.WINS,
            2, Stat.DEATHS,
            4, Stat.KILLS,
            6, Stat.LOSSES,
            8, Stat.POINTS);
    private static final int BACK = 13;

    private final StatsMenus menus;

    GlobalStatsMenu(StatsMenus menus) {
        super(2, menus.messages().get("menu.global-stats.title"));
        this.menus = menus;
        SLOTS.forEach((slot, column) -> set(slot, Items.of(Material.PAPER)
                .name(menus.messages().get("menu.global-stats." + column.name().toLowerCase()))
                .build()));
        backButton(BACK, menus::openPlayers);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        close(player);
        var column = SLOTS.get(slot);
        if (column != null) {
            menus.topList().send(player, column);
        }
    }
}
