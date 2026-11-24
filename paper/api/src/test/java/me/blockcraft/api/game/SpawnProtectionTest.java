package me.blockcraft.api.game;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpawnProtectionTest {

    private final SpawnProtection protection = new SpawnProtection();
    private final Player attacker = player();
    private final Player victim = player();

    @Test
    public void anotherPlayersHitOnAProtectedPlayerIsCancelled() {
        protection.protect(victim, Duration.ofMinutes(1));
        var hit = hit(attacker, victim);
        protection.onHit(hit);
        assertTrue(hit.isCancelled());

        var free = hit(victim, attacker);
        protection.onHit(free);
        assertFalse(free.isCancelled());
    }

    @Test
    public void aLandedHitEndsTheAttackersProtection() {
        protection.protect(attacker, Duration.ofMinutes(1));
        protection.protect(victim, Duration.ofMinutes(1));
        protection.onLandedHit(hit(attacker, victim));
        assertFalse(protection.isProtected(attacker));
        assertTrue(protection.isProtected(victim));
    }

    @SuppressWarnings("removal")
    private static EntityDamageByEntityEvent hit(Player attacker, Player victim) {
        var source = fake(DamageSource.class, (proxy, method, args) -> switch (method.getName()) {
            case "getCausingEntity", "getDirectEntity" -> attacker;
            default -> throw new AssertionError(method.getName());
        });
        return new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 1);
    }

    private static Player player() {
        var id = UUID.randomUUID();
        return fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "equals" -> proxy == args[0];
            case "hashCode" -> id.hashCode();
            default -> throw new AssertionError(method.getName());
        });
    }

    private static <T> T fake(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
