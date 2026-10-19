package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.cosmetic.Secret;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** Found secrets by name, the rest as question marks; once the player has found a few, each of those has a riddle. */
final class SecretsMenu extends Menu {

    private static final int COLUMNS = 9;

    SecretsMenu(Menus menus, Set<String> found) {
        super(rows(), menus.messages().get("secrets.title",
                Formatter.number("found", found.size()), Formatter.number("total", Secret.values().length)));
        var messages = menus.messages();
        boolean riddles = found.size() >= menus.config().secretRiddlesAfter();
        for (Secret secret : Secret.values()) {
            info(secret.ordinal(), found.contains(secret.item())
                    ? Items.of(Material.LIME_DYE)
                            .name(messages.get("secrets.found-item", Placeholder.component("name", messages.get(secret.messageKey()))))
                            .lore(messages.get("secrets.found-lore"))
                            .build()
                    : Items.of(Material.GRAY_DYE)
                            .name(messages.get("secrets.unknown"))
                            .lore(riddles
                                    ? messages.get("secrets.riddle-lore", Placeholder.component("riddle", messages.get(secret.riddleKey())))
                                    : messages.get("secrets.unknown-lore"))
                            .build());
        }
        closeButton();
    }

    /** A slot per secret and the last one for the close button. */
    private static int rows() {
        return (Secret.values().length + COLUMNS) / COLUMNS;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
    }
}
