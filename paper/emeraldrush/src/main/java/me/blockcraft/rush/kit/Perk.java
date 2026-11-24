package me.blockcraft.rush.kit;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Perks are unlocked once with global coins and then buyable in the round shop with emeralds. */
public enum Perk {
    ANGEL("angel", Material.FISHING_ROD),
    LUCKY_POTION("lucky-potion", Material.POTION),
    STRENGTH("strength", Material.REDSTONE);

    public static final String ITEM_PREFIX = "rush.perk.";
    private final String key;
    private final Material icon;

    Perk(String key, Material icon) {
        this.key = key;
        this.icon = icon;
    }

    public String item() {
        return ITEM_PREFIX + key;
    }

    public static Set<Perk> ownedAmong(Set<String> items) {
        return Arrays.stream(values()).filter(perk -> items.contains(perk.item()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Perk.class)));
    }

    public String key() {
        return key;
    }

    public Material icon() {
        return icon;
    }
}
