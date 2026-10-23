package me.blockcraft.rush.kit;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Perks are unlocked once with global coins and then buyable in the round shop with emeralds. */
public enum Perk {
    ANGEL("KIT1", "angel", Material.FISHING_ROD),
    LUCKY_POTION("KIT3", "lucky-potion", Material.POTION),
    STRENGTH("KIT4", "strength", Material.REDSTONE);

    public static final String ITEM_PREFIX = "rush.perk.";
    /** The flag columns of the legacy em_kits table, for the one-time import into bc_unlocks. */
    public static final Map<String, String> LEGACY_COLUMNS = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(perk -> perk.legacyColumn, Perk::item));

    private final String legacyColumn;
    private final String key;
    private final Material icon;

    Perk(String legacyColumn, String key, Material icon) {
        this.legacyColumn = legacyColumn;
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
