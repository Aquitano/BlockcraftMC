package me.blockcraft.api.game;

import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** How a round swings: only the attack cooldown differs, sweep attacks, shields and damage values stay vanilla. */
public enum CombatStyle {

    /** 1.8: the attack speed sits far above any swing rate, so the cooldown is charged on every click. */
    LEGACY(1024.0),
    MODERN(4.0);

    private static final String CONFIG_KEY = "pvp.legacy-combat";

    private final double attackSpeed;

    CombatStyle(double attackSpeed) {
        this.attackSpeed = attackSpeed;
    }

    public void apply(Player player) {
        var attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute != null) {
            attribute.setBaseValue(attackSpeed);
        }
    }

    public static CombatStyle from(ConfigurationSection config) {
        return config.getBoolean(CONFIG_KEY, true) ? LEGACY : MODERN;
    }
}
