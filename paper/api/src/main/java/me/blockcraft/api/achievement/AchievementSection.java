package me.blockcraft.api.achievement;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Rows a server adds below the catalog of the achievements menu, such as the lobby's secrets. The player's unlocks
 * under {@link #prefixes} load with the menu, and {@link #rows} builds the rows from them on the main thread.
 */
public interface AchievementSection {

    /** {@code heading} goes before the entries, which continue on the next row after eight. */
    record Row(ItemStack heading, List<Entry> entries) {
        public Row {
            entries = List.copyOf(entries);
        }
    }

    record Entry(ItemStack item, Optional<Consumer<Player>> click) {

        public static Entry shown(ItemStack item) {
            return new Entry(item, Optional.empty());
        }
    }

    List<String> prefixes();

    List<Row> rows(Set<String> owned);
}
