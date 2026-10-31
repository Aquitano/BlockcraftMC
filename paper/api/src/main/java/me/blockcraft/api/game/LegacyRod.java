package me.blockcraft.api.game;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * 1.8's rod for players in {@link CombatStyle#LEGACY}: a bobber that hits a player knocks them back for no damage, as a
 * snowball does. Modern Minecraft only hooks them. The hit goes through the damage events, so a game's PvP rules and
 * the victim's invulnerability ticks decide whether it lands.
 */
public final class LegacyRod implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof FishHook hook && hook.getShooter() instanceof Player shooter
                && event.getHitEntity() instanceof Player victim && !victim.equals(shooter)
                && CombatStyle.of(shooter) == CombatStyle.LEGACY) {
            victim.damage(0, DamageSource.builder(DamageType.THROWN).withDirectEntity(hook).withCausingEntity(shooter).build());
        }
    }
}
