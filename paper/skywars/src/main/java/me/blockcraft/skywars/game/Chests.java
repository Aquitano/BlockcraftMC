package me.blockcraft.skywars.game;

import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.LootTable;
import me.blockcraft.skywars.config.SkyWarsConfig;
import me.blockcraft.skywars.map.ChestKind;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The map's chests, filled when the round starts and once more at the refill, each from the loot of its kind. A
 * refill replaces what is left in a chest; a chest that was broken stays gone.
 */
final class Chests {

    private final Map<ChestKind, LootChests> loot = new EnumMap<>(ChestKind.class);
    private final Map<ChestKind, List<Block>> blocks;

    Chests(SkyWarsConfig.Loot config, Map<ChestKind, List<Block>> blocks) {
        this.blocks = blocks;
        loot.put(ChestKind.ARMOUR, new LootChests(config.armour(), Chests::item));
        loot.put(ChestKind.WEAPONS, new LootChests(config.weapons(), Chests::item));
        loot.put(ChestKind.UTILITY, new LootChests(config.utility(), Chests::item));
        loot.put(ChestKind.SEMI_MIDDLE, new LootChests(config.semiMiddle(), Chests::item));
        loot.put(ChestKind.MIDDLE, new LootChests(config.middle(), Chests::item));
    }

    void fill() {
        blocks.forEach((kind, chests) -> {
            var table = loot.get(kind);
            table.clear();
            for (var block : chests) {
                if (block.getType() == Material.CHEST && block.getState(false) instanceof Chest chest) {
                    chest.getBlockInventory().setContents(table.open(block.getLocation()).getContents());
                }
            }
        });
    }

    /** Potions in the chests heal, drunk or thrown; everything else is the plain item. */
    private static ItemStack item(LootTable.Entry entry) {
        var item = ItemStack.of(entry.material(), entry.amount());
        if (item.getItemMeta() instanceof PotionMeta potion) {
            potion.setBasePotionType(PotionType.HEALING);
            item.setItemMeta(potion);
        }
        return item;
    }
}
