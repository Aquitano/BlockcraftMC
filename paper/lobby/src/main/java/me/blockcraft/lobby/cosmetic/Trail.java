package me.blockcraft.lobby.cosmetic;

import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.Arrays;
import java.util.Optional;

public enum Trail {
    WITCH("witch", Material.BOOK, Particle.WITCH, true),
    NOTE("note", Material.NOTE_BLOCK, Particle.NOTE, true),
    FLAME("flame", Material.FIRE_CHARGE, Particle.FLAME, true),
    FIREWORK("firework", Material.FIREWORK_ROCKET, Particle.FIREWORK, true),
    SNOW("snow", Material.SNOWBALL, Particle.SNOWFLAKE, false);

    public static final String ITEM_PREFIX = "lobby.trail.";

    private final String key;
    private final Material icon;
    private final Particle particle;
    private final boolean premium;

    Trail(String key, Material icon, Particle particle, boolean premium) {
        this.key = key;
        this.icon = icon;
        this.particle = particle;
        this.premium = premium;
    }

    public static Optional<Trail> byKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
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

    /**
     * Premium players wear every premium trail; any other trail belongs to whoever owns its {@link #item()}. An
     * unlock is never taken back, so only a premium trail is checked again when it is worn.
     */
    public boolean premium() {
        return premium;
    }

    public String item() {
        return ITEM_PREFIX + key;
    }
}
