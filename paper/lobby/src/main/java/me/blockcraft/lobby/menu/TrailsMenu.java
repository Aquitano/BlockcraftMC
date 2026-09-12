package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.cosmetic.Trail;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class TrailsMenu extends Menu {

    private static final int REMOVE_SLOT = 8;

    private final Menus menus;

    TrailsMenu(Menus menus) {
        super(1, menus.messages().get("trails.title"));
        this.menus = menus;
        var messages = menus.messages();
        for (Trail trail : Trail.values()) {
            set(trail.ordinal(), Items.of(trail.icon())
                    .name(messages.get("trails.name." + trail.key()))
                    .lore(messages.get("trails.lore." + trail.key()))
                    .build());
        }
        set(REMOVE_SLOT, Items.of(Material.BARRIER).name(messages.get("trails.remove")).lore(messages.get("trails.remove-lore")).build());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        player.closeInventory();
        var messages = menus.messages();
        if (slot == REMOVE_SLOT) {
            menus.trails().clear(player);
            player.sendMessage(messages.get("trails.removed"));
            return;
        }
        if (slot >= Trail.values().length) {
            return;
        }
        Trail trail = Trail.values()[slot];
        menus.trails().select(player, trail);
        player.sendMessage(messages.get("trails.selected." + trail.key()));
    }
}
