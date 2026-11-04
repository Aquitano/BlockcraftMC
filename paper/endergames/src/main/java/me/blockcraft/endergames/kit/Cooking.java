package me.blockcraft.endergames.kit;

import org.bukkit.Material;

/** What a Jäger's kill drops instead of raw meat. */
final class Cooking {

    private Cooking() {
    }

    static Material cooked(Material raw) {
        return switch (raw) {
            case BEEF -> Material.COOKED_BEEF;
            case PORKCHOP -> Material.COOKED_PORKCHOP;
            case CHICKEN -> Material.COOKED_CHICKEN;
            case MUTTON -> Material.COOKED_MUTTON;
            case RABBIT -> Material.COOKED_RABBIT;
            case COD -> Material.COOKED_COD;
            case SALMON -> Material.COOKED_SALMON;
            default -> raw;
        };
    }
}
