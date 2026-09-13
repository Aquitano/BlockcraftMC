package me.blockcraft.rush.menu;

import me.blockcraft.api.game.Stat;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
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

    private final RushMenus menus;

    GlobalStatsMenu(RushMenus menus, Messages messages) {
        super(2, messages.get("stats-menu.global-title"));
        this.menus = menus;
        SLOTS.forEach((slot, category) ->
                set(slot, Items.of(Material.PAPER).name(messages.get("stats-menu." + category.key())).build()));
        backButton(BACK, menus::openStats);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        close(player);
        var category = SLOTS.get(slot);
        if (category != null) {
            menus.showTop(player, category);
        }
    }
}
