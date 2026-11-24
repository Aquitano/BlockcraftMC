package me.blockcraft.api.game;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

/** A melee hit that knocks back as in vanilla and takes no health, as in Sumo and MLG Rush. */
public final class HarmlessHit {

    private HarmlessHit() {
    }

    /**
     * Lets {@code hit} through with no damage. Vanilla remembers the last hit's damage to ignore weaker ones while the
     * victim is invulnerable; at zero every further click would pass and knock back again, so those are cancelled.
     */
    public static void land(EntityDamageEvent hit, LivingEntity victim) {
        if (victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2) {
            hit.setCancelled(true);
        } else {
            hit.setDamage(0);
        }
    }
}
