package me.blockcraft.api.ignore;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IgnoreCacheTest {

    @Test
    public void changesHeardWhileLoadingWinOverTheStoredList() {
        var player = UUID.randomUUID();
        var unignored = UUID.randomUUID();
        var added = UUID.randomUUID();
        var kept = UUID.randomUUID();
        var cache = new IgnoreCache();
        cache.set(player, unignored, false);
        cache.set(player, added, true);
        cache.load(player, List.of(unignored, kept));
        assertFalse(cache.ignores(player, unignored));
        assertTrue(cache.ignores(player, added));
        assertTrue(cache.ignores(player, kept));
        cache.forget(player);
        assertFalse(cache.ignores(player, kept));
    }
}
