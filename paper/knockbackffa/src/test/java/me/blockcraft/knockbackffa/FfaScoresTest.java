package me.blockcraft.knockbackffa;

import org.junit.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FfaScoresTest {

    @Test
    public void onlyTheLatestUnexpiredHitCreditsOneKill() {
        var scores = scores();
        var first = UUID.randomUUID();
        var last = UUID.randomUUID();
        var victim = UUID.randomUUID();
        scores.join(first);
        scores.join(last);
        scores.join(victim);
        scores.hit(first, victim, 0);
        scores.hit(last, victim, second(2));
        assertEquals(last, scores.death(victim, second(3)).orElseThrow().killer());
        assertTrue(scores.death(victim, second(4)).isEmpty());
        assertEquals(1, scores.score(last).kills());
        assertEquals(0, scores.score(first).kills());
        scores.hit(last, victim, second(5));
        assertTrue(scores.death(victim, second(16)).isEmpty());
    }

    @Test
    public void everyDeathResetsTheVictimsStreakIncludingUncreditedFalls() {
        var scores = scores();
        var attacker = UUID.randomUUID();
        var victim = UUID.randomUUID();
        scores.join(attacker);
        scores.join(victim);
        scores.hit(attacker, victim, 0);
        scores.death(victim, second(1));
        assertEquals(1, scores.score(attacker).streak());
        scores.death(attacker, second(2));
        assertEquals(0, scores.score(attacker).streak());
        assertEquals(1, scores.score(attacker).deaths());
        scores.hit(victim, attacker, second(3));
        scores.death(attacker, second(4));
        scores.hit(attacker, victim, second(5));
        scores.death(victim, second(6));
        assertEquals(0, scores.score(victim).streak());
    }

    @Test
    public void repeatedVictimsDoNotPayAgainAfterRejoiningUntilCooldownExpires() {
        var scores = scores();
        var attacker = UUID.randomUUID();
        var victim = UUID.randomUUID();
        scores.join(attacker);
        scores.join(victim);
        scores.hit(attacker, victim, 0);
        assertTrue(scores.death(victim, second(1)).orElseThrow().rewarded());
        scores.leave(victim);
        scores.join(victim);
        scores.hit(attacker, victim, second(2));
        assertFalse(scores.death(victim, second(3)).orElseThrow().rewarded());
        scores.hit(attacker, victim, second(121));
        assertTrue(scores.death(victim, second(122)).orElseThrow().rewarded());
    }

    @Test
    public void disconnectingRemovesHitsByAndAgainstThePlayer() {
        var scores = scores();
        var attacker = UUID.randomUUID();
        var victim = UUID.randomUUID();
        scores.join(attacker);
        scores.join(victim);
        scores.hit(attacker, victim, 0);
        assertTrue(scores.inCombat(victim, second(1)));
        scores.leave(attacker);
        scores.join(attacker);
        assertFalse(scores.inCombat(victim, second(2)));
        assertTrue(scores.death(victim, second(2)).isEmpty());
    }

    private static FfaScores scores() {
        return new FfaScores(Duration.ofSeconds(10), Duration.ofSeconds(120));
    }

    private static long second(int seconds) {
        return Duration.ofSeconds(seconds).toNanos();
    }
}
