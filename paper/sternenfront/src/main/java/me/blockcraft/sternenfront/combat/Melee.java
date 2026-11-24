package me.blockcraft.sternenfront.combat;

import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Whether a hit is a player's own swing. The damage cause cannot tell: Paper reports a bolt whose direct entity is its
 * shooter as {@code ENTITY_ATTACK} too.
 */
public final class Melee {

    private Melee() { }

    public static boolean swing(EntityDamageEvent event) {
        return event.getDamageSource().getDamageType() == DamageType.PLAYER_ATTACK;
    }
}
