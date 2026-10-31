package me.blockcraft.ttt.game;

import me.blockcraft.api.game.LootTable;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.random.RandomGenerator;

/** A town chest holds one thing, picked by weight. */
public final class ChestLoot {

    private ChestLoot() {
    }

    public static Optional<LootTable.Entry> pick(LootTable table, RandomGenerator random) {
        int total = table.entries().stream().mapToInt(LootTable.Entry::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (var entry : table.entries()) {
            roll -= entry.weight();
            if (roll < 0) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public static ItemStack item(LootTable.Entry entry) {
        return ItemStack.of(entry.material(), entry.amount());
    }
}
