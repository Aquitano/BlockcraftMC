package me.blockcraft.api.game;

import org.bukkit.Material;
import org.junit.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RoundTrackingTest {

    @Test
    public void lastAttackerGetsCreditOnceWithinTheWindow() {
        var attacks = new RoundTracking.Attacks(Duration.ofSeconds(10));
        var victim = UUID.randomUUID();
        var first = new RoundTracking.Hit(UUID.randomUUID(), Material.FISHING_ROD, 0, 0);
        var last = new RoundTracking.Hit(UUID.randomUUID(), Material.WOODEN_SWORD, 0, 1);
        attacks.record(victim, first);
        attacks.record(victim, last);

        assertEquals(last, attacks.take(victim, Duration.ofSeconds(10).toNanos()).orElseThrow());
        assertTrue(attacks.take(victim, Duration.ofSeconds(10).toNanos()).isEmpty());
        attacks.record(victim, first);
        assertTrue(attacks.take(victim, Duration.ofSeconds(10).toNanos() + 1).isEmpty());
    }

    @Test
    public void forgettingAnAttackerClearsTheirHits() {
        var attacks = new RoundTracking.Attacks(Duration.ofSeconds(10));
        var victim = UUID.randomUUID();
        var attacker = UUID.randomUUID();
        attacks.record(victim, new RoundTracking.Hit(attacker, Material.FISHING_ROD, 0, 0));
        attacks.forget(attacker);

        assertTrue(attacks.take(victim, 1).isEmpty());
    }
}
