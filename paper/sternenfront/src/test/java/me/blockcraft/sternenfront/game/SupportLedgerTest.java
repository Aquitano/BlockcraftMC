package me.blockcraft.sternenfront.game;

import org.junit.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SupportLedgerTest {

    private static SupportLedger ledger() {
        return new SupportLedger(new SupportLedger.Rules(200, 400, 40, 2, 40));
    }

    @Test
    public void awardsRecentAssistsOnceExcludesTheKillerAndScalesBotRewards() {
        var ledger = ledger();
        var victim = UUID.randomUUID();
        var helper = UUID.randomUUID();
        var killer = UUID.randomUUID();
        ledger.damage(victim, Optional.of(helper), 4, 1);
        ledger.damage(victim, Optional.of(killer), 20, 2);
        assertEquals(Map.of(helper, 10), ledger.killed(victim, Optional.of(killer), 25, 100, 3));
        assertTrue(ledger.killed(victim, Optional.of(killer), 25, 100, 3).isEmpty());
        ledger.damage(victim, Optional.of(helper), 4, 10);
        assertTrue(ledger.killed(victim, Optional.of(killer), 100, 100, 211).isEmpty());
        ledger.damage(victim, Optional.of(helper), 4, 300);
        ledger.forget(helper);
        assertTrue(ledger.killed(victim, Optional.of(killer), 100, 100, 301).isEmpty());
    }

    @Test
    public void removingAVictimDropsItsWoundsWithoutErasingItsContributionToAnotherFight() {
        var ledger = ledger();
        var removed = UUID.randomUUID();
        var other = UUID.randomUUID();
        ledger.damage(removed, Optional.empty(), 5, 1);
        ledger.damage(other, Optional.of(removed), 4, 1);
        ledger.forgetVictim(removed);
        assertEquals(0, ledger.heal(removed, Optional.of(UUID.randomUUID()), 5, 2));
        assertEquals(Map.of(removed, 10), ledger.killed(other, Optional.empty(), 25, 100, 2));
    }

    @Test
    public void onlyRestoredCombatWoundsEarnHealingAndAllHealingConsumesTheBudget() {
        var ledger = ledger();
        var victim = UUID.randomUUID();
        var healer = UUID.randomUUID();
        assertEquals(0, ledger.heal(victim, Optional.of(healer), 5, 1));
        ledger.damage(victim, Optional.empty(), 8, 2);
        assertEquals(0, ledger.heal(victim, Optional.empty(), 3, 3));
        assertEquals(10, ledger.heal(victim, Optional.of(healer), 20, 4));
        assertEquals(0, ledger.heal(victim, Optional.of(healer), 20, 5));
        ledger.damage(victim, Optional.empty(), 30, 6);
        assertEquals(30, ledger.heal(victim, Optional.of(healer), 30, 7));
        ledger.damage(victim, Optional.empty(), 10, 8);
        assertEquals(0, ledger.heal(victim, Optional.of(healer), 10, 9));
        ledger.killed(victim, Optional.empty(), 25, 100, 10);
        ledger.damage(victim, Optional.empty(), 10, 11);
        assertEquals(20, ledger.heal(victim, Optional.of(healer), 10, 12));
        ledger.damage(victim, Optional.empty(), 4, 20);
        assertEquals(0, ledger.heal(victim, Optional.of(victim), 4, 21));
        assertEquals(0, ledger.heal(victim, Optional.of(healer), 4, 22));
        ledger.damage(victim, Optional.empty(), 4, 30);
        assertEquals(0, ledger.heal(victim, Optional.of(healer), 4, 431));
    }
}
