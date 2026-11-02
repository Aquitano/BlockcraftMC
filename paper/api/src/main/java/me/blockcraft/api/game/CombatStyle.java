package me.blockcraft.api.game;

import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * How a round swings: the attack cooldown differs and, in {@link #LEGACY}, rods and snowballs knock back
 * ({@link LegacyProjectiles}) and
 * weapons deal 1.8's damage ({@link LegacyDamage}); sweep attacks and shields stay vanilla.
 */
public enum CombatStyle {

    /**
     * 1.8: the attack speed sits far above any swing rate, so the cooldown is charged on every click, and the attack
     * damage carries {@link LegacyDamage}'s headroom.
     */
    LEGACY(1024.0, 1.0 + LegacyDamage.HEADROOM),
    MODERN(4.0, 1.0);

    private static final String CONFIG_KEY = "pvp.legacy-combat";

    private final double attackSpeed;
    private final double attackDamage;

    CombatStyle(double attackSpeed, double attackDamage) {
        this.attackSpeed = attackSpeed;
        this.attackDamage = attackDamage;
    }

    public void apply(Player player) {
        var speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null) {
            speed.setBaseValue(attackSpeed);
        }
        var damage = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(attackDamage);
        }
    }

    public static CombatStyle of(Player player) {
        var attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        return attribute != null && attribute.getBaseValue() == LEGACY.attackSpeed ? LEGACY : MODERN;
    }

    public static CombatStyle from(ConfigurationSection config) {
        return config.getBoolean(CONFIG_KEY, true) ? LEGACY : MODERN;
    }
}
