package me.blockcraft.sternenfront.combat;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GuardTest {

    @Test
    public void openingParryBecomesAnOrdinaryBlockAndRecoversOnlyAfterRelease() {
        var guard = new Guard(CombatConfig.from(new MemoryConfiguration()));
        guard.tick(1, true);
        assertEquals(Guard.Result.PARRY, guard.hit(1, 10));
        guard.tick(8, true);
        assertEquals(Guard.Result.BLOCK, guard.hit(8, 10));
        assertEquals(0.7, guard.level(), 0.001);
        guard.tick(40, true);
        assertEquals(0.7, guard.level(), 0.001);
        guard.tick(41, false);
        assertEquals(0.71, guard.level(), 0.001);
        assertEquals(Guard.Result.NONE, guard.hit(41, 10));
    }

    @Test
    public void exhaustedGuardCannotBlockDuringItsBreakWindow() {
        var guard = new Guard(CombatConfig.from(new MemoryConfiguration()));
        guard.tick(1, true);
        assertEquals(Guard.Result.BROKEN, guard.hit(1, 100));
        assertTrue(guard.broken(10));
        guard.tick(10, true);
        assertEquals(Guard.Result.NONE, guard.hit(10, 1));
        guard.tick(31, false);
        assertFalse(guard.broken(31));
        assertTrue(guard.level() > 0);
    }
}
