package me.blockcraft.rush.kit;

import org.bukkit.Material;

/** Perks are unlocked once with global coins and then buyable in the round shop with emeralds. */
public enum Perk {
    ANGEL("KIT1", "angel", 200, Material.FISHING_ROD),
    LUCKY_POTION("KIT3", "lucky-potion", 300, Material.POTION),
    STRENGTH("KIT4", "strength", 500, Material.REDSTONE);

    private final String column;
    private final String key;
    private final int price;
    private final Material icon;

    Perk(String column, String key, int price, Material icon) {
        this.column = column;
        this.key = key;
        this.price = price;
        this.icon = icon;
    }

    public String column() {
        return column;
    }

    public String key() {
        return key;
    }

    public int price() {
        return price;
    }

    public Material icon() {
        return icon;
    }
}
