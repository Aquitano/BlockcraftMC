package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.cosmetic.Secret;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

final class SecretsMenu extends Menu {

    SecretsMenu(Menus menus, Set<String> found) {
        super(1, menus.messages().get("secrets.title"));
        var messages = menus.messages();
        var unknown = Items.of(Material.GRAY_DYE)
                .name(messages.get("secrets.unknown"))
                .lore(messages.get("secrets.unknown-lore"))
                .build();
        for (Secret secret : Secret.values()) {
            set(secret.ordinal(), found.contains(secret.item())
                    ? Items.of(Material.LIME_DYE)
                            .name(messages.get("secrets.found-item", Placeholder.component("name", messages.get(secret.messageKey()))))
                            .lore(messages.get("secrets.found-lore"))
                            .build()
                    : unknown);
        }
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        close(player);
    }
}
