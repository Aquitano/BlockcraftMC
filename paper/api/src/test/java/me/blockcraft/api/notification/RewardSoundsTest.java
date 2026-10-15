package me.blockcraft.api.notification;

import me.blockcraft.api.sound.Sounds;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RewardSoundsTest {
    @Test
    public void competingRewardsPlayOnceAndImportantTitlesDiscardStaleSounds() {
        var sounds = new RewardSounds(4, 30, 40);
        sounds.offer(Sounds.COINS, 0);
        sounds.offer(Sounds.LEVEL_UP, 1);
        sounds.offer(Sounds.ACHIEVEMENT, 2);
        assertNull(sounds.take(3));
        assertEquals(Sounds.ACHIEVEMENT, sounds.take(4));
        sounds.offer(Sounds.PURCHASE, 5);
        assertNull(sounds.take(9));
        assertEquals(Sounds.PURCHASE, sounds.take(34));

        sounds.blockUntil(140);
        sounds.offer(Sounds.COINS, 100);
        assertNull(sounds.take(139));
        assertNull(sounds.take(140));
        assertTrue(sounds.isIdle(140));
        sounds.offer(Sounds.LEVEL_UP, 141);
        assertEquals(Sounds.LEVEL_UP, sounds.take(145));
    }
}
