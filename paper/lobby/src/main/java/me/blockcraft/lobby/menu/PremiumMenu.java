package me.blockcraft.lobby.menu;

import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.lobby.config.LobbyLocation;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class PremiumMenu extends Menu {

    private static final int TRAILS_SLOT = 1;
    private static final int AREA_SLOT = 6;
    private static final int PETS_SLOT = 7;

    private final Menus menus;

    PremiumMenu(Menus menus) {
        super(1, menus.messages().get("premium-menu.title"));
        this.menus = menus;
        var messages = menus.messages();
        set(TRAILS_SLOT, MenuIcon.TRAILS.item().name(messages.get("premium-menu.trails")).lore(messages.get("premium-menu.trails-lore")).build());
        set(AREA_SLOT, MenuIcon.PREMIUM_AREA.item().name(messages.get("premium-menu.area")).lore(messages.get("premium-menu.area-lore")).build());
        set(PETS_SLOT, MenuIcon.PETS.item().name(messages.get("premium-menu.pets")).lore(messages.get("premium-menu.pets-lore")).build());
        backButton(menus::openExtras);
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
