package me.blockcraft.sternenfront.hero;

import me.blockcraft.sternenfront.game.AbilityIcon;
import org.bukkit.Material;

import java.util.Locale;

/** What heroes do besides fighting with their weapon; like a class's abilities, F uses the first and Q the second. */
public enum HeroAbility {
    FORCE_PUSH(Material.WIND_CHARGE, AbilityIcon.FORCE_PUSH),
    SABER_THROW(Material.BLAZE_ROD, AbilityIcon.SABER_THROW),
    FORCE_GRIP(Material.ENDER_EYE, AbilityIcon.FORCE_GRIP),
    FORCE_LIGHTNING(Material.LIGHTNING_ROD, AbilityIcon.FORCE_LIGHTNING),
    JETPACK(Material.FIREWORK_ROCKET, AbilityIcon.JETPACK),
    WRIST_ROCKET(Material.FIRE_CHARGE, AbilityIcon.WRIST_ROCKET),
    GRAPPLE(Material.LEAD, AbilityIcon.GRAPPLE),
    ORBITAL_STRIKE(Material.ECHO_SHARD, AbilityIcon.ORBITAL_STRIKE),
    RALLY(Material.NAUTILUS_SHELL, AbilityIcon.RALLY);

    private final Material material;
    private final AbilityIcon icon;

    HeroAbility(Material material, AbilityIcon icon) {
        this.material = material;
        this.icon = icon;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The client shows a cooldown on every item of a material, so no two of these, nor a class ability, share one. */
    public Material material() {
        return material;
    }

    public AbilityIcon icon() {
        return icon;
    }
}
