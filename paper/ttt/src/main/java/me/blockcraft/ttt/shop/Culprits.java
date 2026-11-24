package me.blockcraft.ttt.shop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who is to blame when the shop's goods hurt someone on their own: explosions, traps and poison count as their
 * owner's kill, even once the owner is dead.
 */
final class Culprits {

    private record Hurt(UUID owner, int tick) {
    }

    private final Map<UUID, UUID> explosives = new HashMap<>();
    private final Map<UUID, Hurt> hurt = new HashMap<>();

    void own(Entity explosive, UUID owner) {
        explosives.put(explosive.getUniqueId(), owner);
    }

    /**
     * Through primed TNT rather than with the owner as the source: a dead owner is a spectator, whose damage
     * SpectatorGuard cancels, and the blast must still go off.
     */
    void explode(Location location, UUID owner, float power) {
        var tnt = location.getWorld().spawn(location, TNTPrimed.class, primed -> {
            primed.setFuseTicks(0);
            primed.setYield(power);
            primed.setSource(Bukkit.getPlayer(owner));
        });
        own(tnt, owner);
    }

    /** Magic damage with no entity behind it, for the same reason; the owner is remembered for the tick. */
    void hurt(Player victim, double damage, UUID owner) {
        hurt.put(victim.getUniqueId(), new Hurt(owner, Bukkit.getCurrentTick()));
        victim.damage(damage, DamageSource.builder(DamageType.MAGIC).build());
    }

    Optional<UUID> of(Player victim, DamageSource source) {
        var direct = source.getDirectEntity();
        if (direct != null && explosives.containsKey(direct.getUniqueId())) {
            return Optional.of(explosives.get(direct.getUniqueId()));
        }
        var recent = hurt.get(victim.getUniqueId());
        if (direct == null && recent != null && Bukkit.getCurrentTick() - recent.tick() <= 1) {
            return Optional.of(recent.owner());
        }
        return Optional.empty();
    }

    void clear() {
        explosives.clear();
        hurt.clear();
    }
}
