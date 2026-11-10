package me.blockcraft.sternenfront.bot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SuppressionTest {

    private static final BotConfig.Shaken RULES = new BotConfig.Shaken(0.5, 0.35, 0.75, 30, 1.5);

    @Test
    public void closePassesPileUpToTheCeilingAndFadeBetween() {
        var suppression = new Suppression();
        assertEquals(1, suppression.spread(0, RULES), 1e-9);
        suppression.passed(1, 0, RULES);
        suppression.passed(0.5, 0, RULES);
        assertEquals(0.525, suppression.level(0, RULES), 1e-9);
        suppression.passed(1, 0, RULES);
        assertEquals(0.75, suppression.level(0, RULES), 1e-9);
        assertEquals(1 + 0.75 * 1.5, suppression.spread(0, RULES), 1e-9);
        assertEquals(0.25, suppression.level(15, RULES), 1e-9);
        assertEquals(0, suppression.level(30, RULES), 1e-9);
    }
}
