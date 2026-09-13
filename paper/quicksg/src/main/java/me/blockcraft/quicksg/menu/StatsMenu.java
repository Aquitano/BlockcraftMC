package me.blockcraft.quicksg.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.game.Stats;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

final class StatsMenu extends Menu {

    private static final int ROWS = 2;
    private static final int SLOT_GLOBAL = 13;

    private final StatsMenus menus;

    StatsMenu(StatsMenus menus, Map<UUID, Stats> stats) {
        super(ROWS, menus.messages().get("menu.stats.title"));
        this.menus = menus;
        int slot = 0;
        for (var entry : stats.entrySet()) {
            var player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            if (slot == SLOT_GLOBAL) {
                slot++;
            }
            if (slot >= ROWS * 9) {
                break;
            }
            set(slot++, head(player, entry.getValue()));
        }
        set(SLOT_GLOBAL, menus.items().head(menus.globalHead(), "menu.stats.global"));
    }

    private ItemStack head(Player player, Stats stats) {
        return Items.head(player.getUniqueId(), player.getName())
                .name(menus.messages().get("menu.stats.player", Placeholder.unparsed("player", player.getName())))
                .lore(menus.messages().getLines("menu.stats.player-lore", stats.placeholders()))
                .build();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == SLOT_GLOBAL) {
            menus.openGlobal(player);
        }
    }
}
