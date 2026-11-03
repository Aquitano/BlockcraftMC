package me.blockcraft.sternenfront.bot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BotConfigTest {

    @Test
    public void humansReplaceBotSeatsWithoutExceedingTheTarget() {
        var config = new BotConfig(true, 4, 10, 8, 0.065, 32, 25);
        assertEquals(4, config.needed(0));
        assertEquals(3, config.needed(1));
        assertEquals(0, config.needed(4));
        assertEquals(0, config.needed(6));
        assertEquals(0, new BotConfig(false, 4, 10, 8, 0.065, 32, 25).needed(0));
    }
}
