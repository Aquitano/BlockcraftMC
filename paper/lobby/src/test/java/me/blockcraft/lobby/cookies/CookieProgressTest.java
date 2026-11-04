package me.blockcraft.lobby.cookies;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CookieProgressTest {

    @Test
    public void clicksRespectTheRateAndBalanceLimit() {
        var progress = new CookieProgress(new CookieProgress.Rules(3, 100, 600, 5, List.of()),
                new CookieProgress.Snapshot(0, 0));
        assertTrue(progress.click(0));
        assertFalse(progress.click(99));
        assertTrue(progress.click(100));
        assertTrue(progress.click(200));
        assertFalse(progress.click(300));
        assertEquals(new CookieProgress.Snapshot(3, 0), progress.snapshot());
    }

    @Test
    public void upgradesSpendCookiesOnceAndIncreaseOnlyClickEarnings() {
        var rules = new CookieProgress.Rules(100, 100, 600, 5,
                List.of(new CookieProgress.Upgrade(2, 5)));
        var progress = new CookieProgress(rules, new CookieProgress.Snapshot(1, 0));
        assertFalse(progress.upgrade());
        assertTrue(progress.click(0));
        assertTrue(progress.upgrade());
        assertEquals(new CookieProgress.Snapshot(0, 1), progress.snapshot());
        assertFalse(progress.upgrade());
        assertTrue(progress.click(100));
        assertEquals(5, progress.snapshot().cookies());
        assertEquals(progress.snapshot(), new CookieProgress(rules, progress.snapshot()).snapshot());
    }

    @Test
    public void configurationAndStoredProgressStayBounded() {
        var rules = CookieProgress.Rules.fromYaml(Map.of("max-cookies", 10, "clicks-per-second", 1000,
                "save-seconds", 0, "shutdown-save-seconds", 1000,
                "upgrades", List.of(Map.of("cost", -1, "per-click", Long.MAX_VALUE))));
        assertEquals(50_000_000, rules.clickNanos());
        assertEquals(100, rules.saveTicks());
        assertEquals(30, rules.shutdownSaveSeconds());
        assertEquals(new CookieProgress.Upgrade(1, 10), rules.upgrades().getFirst());
        var progress = new CookieProgress(rules, new CookieProgress.Snapshot(9, 100));
        assertTrue(progress.click(0));
        assertEquals(new CookieProgress.Snapshot(10, 1), progress.snapshot());
    }
}
