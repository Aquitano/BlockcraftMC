package me.blockcraft.lobby;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatTalkTest {

    @Test
    public void eachFigureTalksToEachPlayerOncePerCooldown() {
        var now = new Instant[]{Instant.EPOCH};
        var talk = new ChatTalk(null, Duration.ofSeconds(2), () -> now[0]);
        var player = UUID.randomUUID();
        assertTrue(talk.ready(player, "courier"));
        assertFalse(talk.ready(player, "courier"));
        assertTrue(talk.ready(player, "board"));
        assertTrue(talk.ready(UUID.randomUUID(), "courier"));
        now[0] = now[0].plusSeconds(2);
        assertTrue(talk.ready(player, "courier"));
    }
}
