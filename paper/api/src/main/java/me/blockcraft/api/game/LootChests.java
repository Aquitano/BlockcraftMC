package me.blockcraft.api.game;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;

/** Rolls one inventory per chest block the first time it is opened; every later opener sees the same one. */
public final class LootChests {

    private final LootTable loot;
    private final Function<LootTable.Entry, ItemStack> items;
    private final Random random = new Random();
    private final Map<Location, Inventory> opened = new HashMap<>();
    private final int totalWeight;

    public LootChests(LootTable loot) {
        this(loot, entry -> ItemStack.of(entry.material(), entry.amount()));
    }

    public LootChests(LootTable loot, Function<LootTable.Entry, ItemStack> items) {
        this.loot = loot;
        this.items = items;
        this.totalWeight = loot.entries().stream().mapToInt(LootTable.Entry::weight).sum();
    }

    public void clear() {
        opened.values().forEach(inventory -> inventory.getViewers().stream().toList().forEach(org.bukkit.entity.HumanEntity::closeInventory));
        opened.clear();
    }

    public boolean isFresh(Location location) {
        return !opened.containsKey(location.toBlockLocation());
    }

    public Inventory open(Location location) {
        return opened.computeIfAbsent(location.toBlockLocation(), ignored -> generate());
    }

    private Inventory generate() {
        var inventory = new LootChest().getInventory();
        if (totalWeight <= 0) {
            return inventory;
        }
        var slots = new ArrayList<>(IntStream.range(0, inventory.getSize()).boxed().toList());
        Collections.shuffle(slots, random);
        int count = Math.min(random.nextInt(loot.minItems(), loot.maxItems() + 1), slots.size());
        slots.subList(0, count).forEach(slot -> inventory.setItem(slot, items.apply(roll())));
        return inventory;
    }

    private LootTable.Entry roll() {
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
