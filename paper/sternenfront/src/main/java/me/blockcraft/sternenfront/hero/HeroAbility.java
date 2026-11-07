package me.blockcraft.sternenfront.hero;

import org.bukkit.Material;

import java.util.Locale;

/** What heroes do besides fighting with their weapon; like a class's abilities, F uses the first and Q the second. */
public enum HeroAbility {
    FORCE_PUSH(Material.WIND_CHARGE),
    SABER_THROW(Material.BLAZE_ROD),
    FORCE_GRIP(Material.ENDER_EYE),
    FORCE_LIGHTNING(Material.LIGHTNING_ROD),
    JETPACK(Material.FIREWORK_ROCKET),
    WRIST_ROCKET(Material.FIRE_CHARGE),
    GRAPPLE(Material.LEAD),
    ORBITAL_STRIKE(Material.ECHO_SHARD),
    RALLY(Material.NAUTILUS_SHELL);

    private final Material material;

    HeroAbility(Material material) {
        this.material = material;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The client shows a cooldown on every item of a material, so no two of these, nor a class ability, share one. */
    public Material material() {
        return material;
    }
}
