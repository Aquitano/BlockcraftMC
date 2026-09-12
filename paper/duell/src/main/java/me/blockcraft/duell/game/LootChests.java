package me.blockcraft.duell.game;

import me.blockcraft.duell.config.DuellConfig;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class LootChests {

    private final DuellConfig.Loot loot;
    private final Random random = new Random();
    private final Map<Location, Inventory> opened = new HashMap<>();
    private final int totalWeight;

    public LootChests(DuellConfig.Loot loot) {
        this.loot = loot;
        this.totalWeight = loot.entries().stream().mapToInt(DuellConfig.LootEntry::weight).sum();
    }

    public boolean isFresh(Location location) {
        return !opened.containsKey(location.toBlockLocation());
    }

    /** Returns the inventory generated for this chest; the first opener rolls the loot. */
    public Inventory open(Location location) {
        return opened.computeIfAbsent(location.toBlockLocation(), ignored -> generate());
    }

    private Inventory generate() {
        var inventory = new LootChest().getInventory();
        int count = random.nextInt(loot.minItems(), loot.maxItems() + 1);
        for (int i = 0; i < count; i++) {
            var entry = roll();
            inventory.setItem(random.nextInt(inventory.getSize()), ItemStack.of(entry.material(), entry.amount()));
        }
        return inventory;
    }

    private DuellConfig.LootEntry roll() {
        int pick = random.nextInt(totalWeight);
        for (var entry : loot.entries()) {
            pick -= entry.weight();
            if (pick < 0) {
                return entry;
            }
        }
        return loot.entries().getLast();
    }
}
