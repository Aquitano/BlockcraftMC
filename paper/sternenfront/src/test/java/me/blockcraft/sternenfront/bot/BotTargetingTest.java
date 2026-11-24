package me.blockcraft.sternenfront.bot;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BotTargetingTest {

    @Test
    public void keepsItsTargetWhenNearbyEnemiesTradePlacesButSwitchesToAMuchCloserThreat() {
        var current = UUID.randomUUID();
        var other = UUID.randomUUID();
        assertEquals(current, BotTargeting.choose(List.of(new BotTargeting.Target(current, 100, false),
                new BotTargeting.Target(other, 81, false)), current).orElseThrow());
        assertEquals(other, BotTargeting.choose(List.of(new BotTargeting.Target(current, 100, false),
                new BotTargeting.Target(other, 9, false)), current).orElseThrow());
        assertEquals(other, BotTargeting.choose(List.of(new BotTargeting.Target(other, 81, false)), current).orElseThrow());
        assertTrue(BotTargeting.choose(List.of(), current).isEmpty());
    }

    @Test
    public void anAttackerCountsAsHalfItsDistanceAway() {
        var current = UUID.randomUUID();
        var attacker = UUID.randomUUID();
        assertEquals(attacker, BotTargeting.choose(List.of(new BotTargeting.Target(current, 100, false),
                new BotTargeting.Target(attacker, 196, true)), current).orElseThrow());
        assertEquals(current, BotTargeting.choose(List.of(new BotTargeting.Target(current, 100, false),
                new BotTargeting.Target(attacker, 324, true)), current).orElseThrow());
        assertEquals(attacker, BotTargeting.choose(List.of(new BotTargeting.Target(current, 100, false),
                new BotTargeting.Target(attacker, 324, true)), null).orElseThrow());
    }
}
