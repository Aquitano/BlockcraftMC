package me.blockcraft.rush.menu;

import me.blockcraft.api.game.Stats;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

final class StatsMenu extends Menu {

    private static final int GLOBAL = 13;
    private static final int SIZE = 18;

    record Entry(Player player, Stats stats) {
    }

    private final RushMenus menus;

    StatsMenu(RushMenus menus, Messages messages, List<Entry> entries) {
        super(2, messages.get("stats-menu.title"));
        this.menus = menus;
        int slot = 0;
        for (var entry : entries) {
            if (slot == GLOBAL) {
                slot++;
            }
            if (slot >= SIZE) {
                break;
            }
            var placeholders = TagResolver.resolver(Placeholder.unparsed("player", entry.player().getName()),
                    RushMenus.statsPlaceholders(entry.stats()));
            set(slot++, Items.head(entry.player().getUniqueId(), entry.player().getName())
                    .name(messages.get("stats-menu.entry", placeholders))
                    .lore(messages.getLines("stats-menu.entry-lore", placeholders))
                    .build());
        }
        set(GLOBAL, Items.head("Kevos").name(messages.get("stats-menu.global")).build());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == GLOBAL) {
            menus.openGlobalStats(player);
        }
    }
}
