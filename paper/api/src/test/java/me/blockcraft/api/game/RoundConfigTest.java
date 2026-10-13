package me.blockcraft.api.game;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RoundConfigTest {

    @Test
    public void nightOwlStartsAtThreeAndEndsBeforeFour() {
        var night = RoundConfig.from(Map.of()).nightOwl();

        assertFalse(night.includes(2));
        assertTrue(night.includes(3));
        assertFalse(night.includes(4));
    }
}
