package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.config.BundledDefaults;
import org.bukkit.configuration.ConfigurationSection;

public record CombatConfig(double guardCapacity, double guardRecovery, int recoveryDelay, int breakTicks,
                           int parryTicks, double guardCost, double meleeRange, double meleeAngle,
                           int swingTicks, int sweepTicks, boolean precisionHitscan, int damageIndicatorTicks) {

    public static CombatConfig from(ConfigurationSection config) {
        var read = BundledDefaults.strict(config);
        return new CombatConfig(Math.max(1, read.decimal("combat.guard.capacity")),
                Math.max(0, read.decimal("combat.guard.recovery-per-tick")),
                Math.max(0, read.integer("combat.guard.recovery-delay-ticks")),
                Math.max(1, read.integer("combat.guard.break-ticks")),
                Math.max(0, read.integer("combat.guard.parry-ticks")),
                Math.max(0.1, read.decimal("combat.guard.cost-per-damage")),
                Math.clamp(read.decimal("combat.melee.range"), 1, 5),
                Math.clamp(read.decimal("combat.melee.angle"), 1, 180),
                Math.max(1, read.integer("combat.melee.swing-ticks")),
                Math.clamp(read.integer("combat.melee.sweep-ticks"), 1,
                        Math.clamp(read.integer("combat.melee.swing-ticks"), 1, 10)),
                read.flag("combat.precision-hitscan"),
                Math.clamp(read.integer("combat.damage-indicator-ticks"), 0, 100));
    }
}
