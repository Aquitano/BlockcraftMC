package me.blockcraft.lobby.cosmetic;

import org.bukkit.Material;
import org.bukkit.Particle;

public enum Trail {
    WITCH("witch", Material.BOOK, Particle.WITCH),
    NOTE("note", Material.NOTE_BLOCK, Particle.NOTE),
    FLAME("flame", Material.FIRE_CHARGE, Particle.FLAME),
    FIREWORK("firework", Material.FIREWORK_ROCKET, Particle.FIREWORK);

    private final String key;
    private final Material icon;
    private final Particle particle;

    Trail(String key, Material icon, Particle particle) {
        this.key = key;
        this.icon = icon;
        this.particle = particle;
    }

    public String key() {
        return key;
    }

    public Material icon() {
        return icon;
    }

    public Particle particle() {
        return particle;
    }
}
