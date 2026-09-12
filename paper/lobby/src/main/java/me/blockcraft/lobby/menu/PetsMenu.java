package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.cosmetic.PetType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class PetsMenu extends Menu {

    private static final int CLOSE_SLOT = 8;

    private final Menus menus;

    PetsMenu(Menus menus) {
        super(1, menus.messages().get("pets.title"));
        this.menus = menus;
        var messages = menus.messages();
        for (PetType type : PetType.values()) {
            set(type.ordinal(), Items.of(type.icon())
                    .name(messages.get("pets.name." + type.key()))
                    .lore(messages.get("pets.lore." + type.key()))
                    .build());
        }
        set(CLOSE_SLOT, Items.of(Material.BARRIER).name(messages.get("pets.close")).build());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        close(player);
        if (slot >= PetType.values().length) {
            return;
        }
        PetType type = PetType.values()[slot];
        var messages = menus.messages();
        if (menus.pets().hasPet(player)) {
            player.sendMessage(messages.get("pets.already"));
            return;
        }
        menus.pets().spawn(player, type);
        player.sendMessage(messages.get("pets.chosen." + type.key()));
    }
}
