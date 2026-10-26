package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.activity.LoreNotes;
import me.blockcraft.lobby.cosmetic.Secret;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Found secrets by name, the rest as question marks; once the player has found a few, each of those has a riddle.
 * The lore notes follow, and a found one opens again on a click.
 */
final class SecretsMenu extends Menu {

    private static final int COLUMNS = 9;
    private static final int MAX_ROWS = 6;
    private static final int FIRST_NOTE = Secret.values().length;

    private final Menus menus;
    private final Set<String> found;

    SecretsMenu(Menus menus, Set<String> found) {
        super(rows(menus.loreNotes().keys().size()), menus.messages().get("secrets.title",
                Formatter.number("found", secretsFound(found)), Formatter.number("total", Secret.values().length)));
        var messages = menus.messages();
        boolean riddles = secretsFound(found) >= menus.config().secretRiddlesAfter();
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
        var notes = menus.loreNotes().keys();
        for (int index = 0; index < notes.size(); index++) {
            var note = notes.get(index);
            boolean read = found.contains(LoreNotes.item(note));
            var icon = menus.loreNotes().icon(note, read);
            if (read) {
                set(FIRST_NOTE + index, icon);
            } else {
                info(FIRST_NOTE + index, icon);
            }
        }
        this.found = found;
        this.menus = menus;
        closeButton();
    }

    private static long secretsFound(Set<String> found) {
        return found.stream().filter(item -> item.startsWith(Secret.ITEM_PREFIX)).count();
    }

    /** A slot per secret, then one per lore note, and the last one for the close button. */
    private static int rows(int notes) {
        return Math.min(MAX_ROWS, (Secret.values().length + notes + COLUMNS) / COLUMNS);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var notes = menus.loreNotes().keys();
        int index = slot - FIRST_NOTE;
        if (index >= 0 && index < notes.size() && found.contains(LoreNotes.item(notes.get(index)))) {
            close(player, reader -> menus.loreNotes().read(reader, notes.get(index)));
        }
    }
}
