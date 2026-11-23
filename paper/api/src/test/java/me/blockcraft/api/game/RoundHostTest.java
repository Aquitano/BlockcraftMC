package me.blockcraft.api.game;

import org.bukkit.NamespacedKey;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RoundHostTest {

    private static final List<NamespacedKey> WORLDS = List.of(NamespacedKey.minecraft("overworld"));

    @Test
    public void aRoundThatLeftNothingUnderThePluginPlaysOn() {
        var first = new RoundHost.Footprint(12, 3, WORLDS);

        assertTrue(new RoundHost.Footprint(12, 3 + RoundHost.Footprint.TASK_SLACK, WORLDS).grownSince(first).isEmpty());
        assertTrue(new RoundHost.Footprint(11, 1, WORLDS).grownSince(first).isEmpty());
    }

    @Test
    public void listenersTasksOrWorldsLeftBehindAreNamed() {
        var first = new RoundHost.Footprint(12, 3, WORLDS);
        var later = new RoundHost.Footprint(13, 9, List.of(NamespacedKey.minecraft("overworld"), new NamespacedKey("skywars", "inseln")));

        assertEquals(List.of("1 listeners", "6 tasks", "worlds [minecraft:overworld, skywars:inseln] instead of [minecraft:overworld]"),
                later.grownSince(first));
    }
}
