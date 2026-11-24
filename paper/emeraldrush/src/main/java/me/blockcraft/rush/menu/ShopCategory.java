package me.blockcraft.rush.menu;

import org.bukkit.Material;

import java.util.Optional;

enum ShopCategory {
    WEAPONS(0, "weapons", Material.IRON_SWORD),
    CHAIN(2, "chain", Material.CHAINMAIL_CHESTPLATE),
    IRON(3, "iron", Material.IRON_CHESTPLATE),
    POTIONS(5, "potions", Material.POTION),
    SPECIAL(6, "special", Material.FIREWORK_STAR);

    private final int navSlot;
    private final String key;
    private final Material icon;

    ShopCategory(int navSlot, String key, Material icon) {
        this.navSlot = navSlot;
        this.key = key;
        this.icon = icon;
    }

    int navSlot() {
        return navSlot;
    }

    String key() {
        return key;
    }

    Material icon() {
        return icon;
    }

    static Optional<ShopCategory> byNavSlot(int slot) {
        for (var category : values()) {
            if (category.navSlot == slot) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
