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
        assertEquals(current, BotTargeting.choose(List.of(new BotTargeting.Target(current, 100),
                new BotTargeting.Target(other, 81)), current).orElseThrow());
        assertEquals(other, BotTargeting.choose(List.of(new BotTargeting.Target(current, 100),
                new BotTargeting.Target(other, 9)), current).orElseThrow());
        assertEquals(other, BotTargeting.choose(List.of(new BotTargeting.Target(other, 81)), current).orElseThrow());
        assertTrue(BotTargeting.choose(List.of(), current).isEmpty());
    }
}
