package me.blockcraft.bedwars.perk;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * The perks, as the original laid them out: the groups down the left with a marker on the open one, its choices
 * in a block of four columns in the middle, and the reset on the right.
 */
public final class PerksMenu extends Menu {

    private static final int ROWS = 6;
    private static final int COLUMNS = 9;
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_FIRST_COLUMN = 3;
    private static final int RESET_SLOT = 44;

    private final Messages messages;
    private final Perks perks;
    private final Player viewer;
    private final Map<Integer, PerkGroup> tabs = new HashMap<>();
    private final Map<Integer, Perk> choices = new HashMap<>();
    private PerkGroup group;

    public PerksMenu(Messages messages, Perks perks, Player viewer) {
        super(ROWS, messages.get("perks.title"));
        this.messages = messages;
        this.perks = perks;
        this.viewer = viewer;
        this.group = PerkGroup.values()[0];
        var groups = PerkGroup.values();
        for (int index = 0; index < groups.length; index++) {
            tabs.put((index + 1) * COLUMNS, groups[index]);
        }
        set(RESET_SLOT, Items.of(Material.BARRIER).name(messages.get("perks.reset")).build());
        closeButton();
        draw();
    }

    private void draw() {
        tabs.forEach((slot, tab) -> {
            var icon = Items.of(tab.icon()).name(messages.get("perks.groups." + tab.key())).hideAttributes();
            set(slot, tab == group ? icon.glow().build() : icon.build());
            info(slot + 1, Items.of(tab == group ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)
                    .name(Component.space()).build());
        });
        choices.keySet().forEach(slot -> set(slot, null));
        choices.clear();
        var offered = group.choices();
        int rows = (offered.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        int firstRow = (int) Math.floor(ROWS / 2.0 - rows / 2.0);
        for (int index = 0; index < offered.size(); index++) {
            var perk = offered.get(index);
            int slot = (firstRow + index / GRID_COLUMNS) * COLUMNS + GRID_FIRST_COLUMN + index % GRID_COLUMNS;
            choices.put(slot, perk);
            boolean selected = perks.isSelected(viewer, group, perk);
            var icon = Items.of(perk.icon())
                    .name(messages.get("perks." + group.key() + "." + perk.key()))
                    .lore(Component.empty(), messages.get(selected ? "perks.selected" : "perks.choose"))
                    .hideAttributes();
            set(slot, selected ? icon.glow().build() : icon.build());
        }
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var tab = tabs.get(slot);
        if (tab != null) {
            group = tab;
            draw();
            return;
        }
        if (slot == RESET_SLOT) {
            perks.reset(player, group);
            player.sendMessage(messages.get("perks.was-reset", Placeholder.component("group", messages.get("perks.groups." + group.key()))));
            close(player);
            return;
        }
        var perk = choices.get(slot);
        if (perk != null) {
            perks.select(player, group, perk);
            player.sendMessage(messages.get("perks.chosen", Placeholder.component("perk", messages.get("perks." + group.key() + "." + perk.key()))));
            close(player);
        }
    }
}
