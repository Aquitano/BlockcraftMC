package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.config.LobbyLocation;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class PremiumMenu extends Menu {

    private static final int TRAILS_SLOT = 1;
    private static final int AREA_SLOT = 6;
    private static final int PETS_SLOT = 7;
    private static final int BACK_SLOT = 8;
    private static final Color TRAILS_ICON_COLOR = Color.fromRGB(19, 79, 218);

    private final Menus menus;

    PremiumMenu(Menus menus) {
        super(1, menus.messages().get("premium-menu.title"));
        this.menus = menus;
        var messages = menus.messages();
        set(TRAILS_SLOT, Items.of(Material.LEATHER_BOOTS).color(TRAILS_ICON_COLOR)
                .name(messages.get("premium-menu.trails")).lore(messages.get("premium-menu.trails-lore")).build());
        set(AREA_SLOT, Items.of(Material.FIREWORK_ROCKET).name(messages.get("premium-menu.area")).lore(messages.get("premium-menu.area-lore")).build());
        set(PETS_SLOT, Items.of(Material.PIG_SPAWN_EGG).name(messages.get("premium-menu.pets")).lore(messages.get("premium-menu.pets-lore")).build());
        backButton(BACK_SLOT, menus::openExtras);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        switch (slot) {
            case TRAILS_SLOT -> menus.openTrails(player);
            case AREA_SLOT -> menus.warp(player, LobbyLocation.PREMIUM);
            case PETS_SLOT -> menus.openPets(player);
            default -> close(player);
        }
    }
}
