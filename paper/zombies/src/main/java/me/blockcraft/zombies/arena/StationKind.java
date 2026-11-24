package me.blockcraft.zombies.arena;

import org.bukkit.Material;

import java.util.Locale;

public enum StationKind {
    ARMORY(Material.SMITHING_TABLE),
    ARMOR(Material.BLAST_FURNACE),
    PERKS(Material.BREWING_STAND),
    POWER(Material.LODESTONE),
    UPGRADE(Material.ENCHANTING_TABLE),
    MYSTERY(Material.ENDER_CHEST),
    TEAM_REVIVE(Material.BEACON);

    private final Material material;

    StationKind(Material material) {
        this.material = material;
    }

    public Material material() { return material; }
    public String key() { return name().toLowerCase(Locale.ROOT); }
}
