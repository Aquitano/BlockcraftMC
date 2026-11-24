package me.blockcraft.api.afk;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WakeAttemptsTest {

    @Test
    public void reportsTheMostPersistentPokerOnceAndClearsTheNap() {
        var attempts = new WakeAttempts();
        var sleeper = UUID.randomUUID();
        var poker = UUID.randomUUID();
        attempts.poke(sleeper, poker, "FirstNick");
        attempts.poke(sleeper, poker, "NewNick");
        attempts.poke(sleeper, poker, "NewNick");
        attempts.poke(sleeper, UUID.randomUUID(), "Other");

        assertEquals(new WakeAttempts.Poker(poker, "NewNick", 3), attempts.take(sleeper).orElseThrow());
        assertTrue(attempts.take(sleeper).isEmpty());
        attempts.poke(sleeper, poker, "NewNick");
        attempts.clear(sleeper);
        assertTrue(attempts.take(sleeper).isEmpty());
    }
}
