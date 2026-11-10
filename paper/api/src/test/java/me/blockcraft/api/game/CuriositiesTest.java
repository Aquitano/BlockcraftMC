package me.blockcraft.api.game;

import org.junit.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CuriositiesTest {

    @Test
    public void picksOneNotableFactByItsThresholdAndIgnoresIneligiblePlayers() {
        var facts = new Curiosities(Curiosities.minimums(Map.of()));
        var jumper = UUID.randomUUID();
        var archer = UUID.randomUUID();
        facts.record(jumper, Curiosities.Kind.JUMPS, 214);
        facts.record(archer, Curiosities.Kind.BOW, 47);
        facts.record(archer, Curiosities.Kind.BOW, 12);

        assertEquals(new Curiosities.Fact(jumper, Curiosities.Kind.JUMPS, 214), facts.select(id -> true).orElseThrow());
        assertEquals(new Curiosities.Fact(archer, Curiosities.Kind.BOW, 47), facts.select(archer::equals).orElseThrow());
        assertTrue(facts.select(id -> false).isEmpty());
    }

    @Test
    public void halfHeartTimeAccumulatesOnlyAcrossWoundedIntervals() {
        var facts = new Curiosities(Map.of(Curiosities.Kind.HALF_HEART, 10));
        var player = UUID.randomUUID();
        facts.health(player, true, 0);
        facts.health(player, false, Duration.ofSeconds(4).toNanos());
        facts.health(player, true, Duration.ofSeconds(20).toNanos());
        facts.finish(Duration.ofSeconds(27).toNanos());

        assertEquals(new Curiosities.Fact(player, Curiosities.Kind.HALF_HEART, 11), facts.select(id -> true).orElseThrow());
        facts.finish(Duration.ofSeconds(40).toNanos());
        assertEquals(11, facts.select(id -> true).orElseThrow().value());
    }
}
