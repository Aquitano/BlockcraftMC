package me.blockcraft.sternenfront.combat;

import org.bukkit.configuration.ConfigurationSection;

public record CombatConfig(double guardCapacity, double guardRecovery, int recoveryDelay, int breakTicks,
                           int parryTicks, double guardCost, double meleeRange, double meleeAngle,
                           int swingTicks, int targets, boolean precisionHitscan) {

    public static CombatConfig from(ConfigurationSection config) {
        return new CombatConfig(Math.max(1, config.getDouble("combat.guard.capacity", 100)),
                Math.max(0, config.getDouble("combat.guard.recovery-per-tick", 1)),
                Math.max(0, config.getInt("combat.guard.recovery-delay-ticks", 20)),
                Math.max(1, config.getInt("combat.guard.break-ticks", 30)),
                Math.max(0, config.getInt("combat.guard.parry-ticks", 5)),
                Math.max(0.1, config.getDouble("combat.guard.cost-per-damage", 2)),
                Math.clamp(config.getDouble("combat.melee.range", 3.5), 1, 5),
                Math.clamp(config.getDouble("combat.melee.angle", 85), 1, 180),
                Math.max(1, config.getInt("combat.melee.swing-ticks", 12)),
                Math.clamp(config.getInt("combat.melee.targets", 2), 1, 4),
                config.getBoolean("combat.precision-hitscan", true));
    }
}
