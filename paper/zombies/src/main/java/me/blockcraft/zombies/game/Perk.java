package me.blockcraft.zombies.game;

import org.bukkit.Material;

import java.util.Locale;

public enum Perk {
    EXTRA_HEALTH(Material.GOLDEN_APPLE),
    QUICK_RELOAD(Material.CLOCK),
    QUICK_REVIVE(Material.TOTEM_OF_UNDYING),
    FROZEN_BULLETS(Material.BLUE_ICE);

    private final Material material;

    Perk(Material material) {
        this.material = material;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Material material() {
        return material;
    }
}
