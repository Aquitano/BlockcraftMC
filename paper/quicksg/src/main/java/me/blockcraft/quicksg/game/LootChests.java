package me.blockcraft.quicksg.game;

import me.blockcraft.quicksg.config.QuickSGConfig;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class LootChests {

    private final QuickSGConfig.Loot loot;
    private final GameItems items;
    private final Random random = new Random();
    private final Map<Location, Inventory> opened = new HashMap<>();
    private final int totalWeight;

    public LootChests(QuickSGConfig.Loot loot, GameItems items) {
        this.loot = loot;
        this.items = items;
        this.totalWeight = loot.entries().stream().mapToInt(QuickSGConfig.LootEntry::weight).sum();
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
            inventory.setItem(random.nextInt(inventory.getSize()), items.loot(entry.material(), entry.amount()));
        }
        return inventory;
    }

    private QuickSGConfig.LootEntry roll() {
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
