package me.blockcraft.api.game;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * 1.8's rods, snowballs, eggs and pearls for shooters in {@link CombatStyle#LEGACY}: a hit on a player knocks them back
 * and flashes red for no damage, and starts the invulnerability that lets a melee hit right after it land as 1.8's
 * rod combo did. Modern Minecraft only hooks with a rod, and Paper drops a player hit of exactly zero damage before
 * any of that, so the hit carries a sliver of damage no heart shows. Every hit goes through the damage events, so a
 * game's PvP rules still decide whether it lands.
 */
public final class LegacyProjectiles implements Listener {

    private static final double SLIVER = 0.0001;

    @EventHandler(ignoreCancelled = true)
    public void onRodHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof FishHook hook && hook.getShooter() instanceof Player shooter
                && event.getHitEntity() instanceof Player victim && !victim.equals(shooter)
                && CombatStyle.of(shooter) == CombatStyle.LEGACY) {
            victim.damage(0, DamageSource.builder(DamageType.THROWN).withDirectEntity(hook).withCausingEntity(shooter).build());
        }
    }

    /** After the games' rules, which may have zeroed the hit themselves, as a harmless hit does. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarmlessProjectile(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter && !victim.equals(shooter)
                && CombatStyle.of(shooter) == CombatStyle.LEGACY && event.getDamage() == 0) {
            event.setDamage(SLIVER);
        }
    }
}
