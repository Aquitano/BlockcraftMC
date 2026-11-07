package me.blockcraft.sternenfront.game;

import org.bukkit.configuration.ConfigurationSection;

/**
 * How a blaster behaves in the hands: aim opens up on the move and with a hot barrel and tightens crouched, and damage
 * thins out over the far part of a weapon's range. The {@code combat.handling} section of config.yml.
 */
public record WeaponHandling(double moving, double crouched, double hot, double falloffFrom, double falloffMinimum) {

    public static WeaponHandling from(ConfigurationSection config) {
        return new WeaponHandling(Math.max(1, config.getDouble("moving")), Math.clamp(config.getDouble("crouched"), 0, 1),
                Math.max(0, config.getDouble("hot")), Math.clamp(config.getDouble("falloff-from"), 0, 1),
                Math.clamp(config.getDouble("falloff-minimum"), 0, 1));
    }

    public double spread(double base, boolean isMoving, boolean isCrouched, double heat) {
        return base * (isMoving ? moving : 1) * (isCrouched ? crouched : 1) * (1 + Math.clamp(heat, 0, 1) * hot);
    }

    public double damage(double base, double travelled, double range) {
        double start = range * falloffFrom;
        if (travelled <= start || range <= start) {
            return base;
        }
        double share = Math.min(1, (travelled - start) / (range - start));
        return base * (1 - share * (1 - falloffMinimum));
    }
}
