package me.blockcraft.lobby.menu;

import me.blockcraft.api.achievement.AchievementSection;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.activity.LoreNotes;
import me.blockcraft.lobby.cosmetic.Secret;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The lobby's rows of the achievements menu. Found secrets by name, the rest as question marks; once the player has
 * found a few, each of those has a riddle. The lore notes follow, and a found one opens again on a click.
 */
public final class SecretsSection implements AchievementSection {

    private final Messages messages;
    private final LoreNotes loreNotes;
    private final int riddlesAfter;

    public SecretsSection(Messages messages, LoreNotes loreNotes, int riddlesAfter) {
        this.messages = messages;
        this.loreNotes = loreNotes;
        this.riddlesAfter = riddlesAfter;
    }

    @Override
    public List<String> prefixes() {
        return List.of(Secret.ITEM_PREFIX, LoreNotes.ITEM_PREFIX);
    }

    @Override
    public List<Row> rows(Set<String> owned) {
        return List.of(secrets(owned), notes(owned));
    }

    private Row secrets(Set<String> owned) {
        long found = Arrays.stream(Secret.values()).filter(secret -> owned.contains(secret.item())).count();
        boolean riddles = found >= riddlesAfter;
        var heading = heading(Material.EMERALD, "secrets.heading", found, Secret.values().length);
        return new Row(heading, Arrays.stream(Secret.values())
                .map(secret -> Entry.shown(owned.contains(secret.item()) ? foundSecret(secret) : missingSecret(secret, riddles)))
                .toList());
    }

    private Row notes(Set<String> owned) {
        var notes = loreNotes.keys();
        long found = notes.stream().filter(note -> owned.contains(LoreNotes.item(note))).count();
        return new Row(heading(Material.BOOK, "lore.heading", found, notes.size()), notes.stream().map(note -> {
            boolean read = owned.contains(LoreNotes.item(note));
            var icon = loreNotes.icon(note, read);
            return read
                    ? new Entry(icon, Optional.of(player -> Menu.close(player, reader -> loreNotes.read(reader, note))))
                    : Entry.shown(icon);
        }).toList());
    }

    private ItemStack heading(Material icon, String key, long found, int total) {
        return Items.of(icon).name(messages.get(key))
                .lore(messages.get("secrets.heading-lore", Formatter.number("found", found), Formatter.number("total", total)))
                .build();
    }

    private ItemStack foundSecret(Secret secret) {
        return Items.of(Material.LIME_DYE)
                .name(messages.get("secrets.found-item", Placeholder.component("name", messages.get(secret.messageKey()))))
                .lore(messages.get("secrets.found-lore"))
                .build();
    }

    private ItemStack missingSecret(Secret secret, boolean riddles) {
        return Items.of(Material.GRAY_DYE)
                .name(messages.get("secrets.unknown"))
                .lore(riddles
                        ? messages.get("secrets.riddle-lore", Placeholder.component("riddle", messages.get(secret.riddleKey())))
                        : messages.get("secrets.unknown-lore"))
                .build();
    }
}
