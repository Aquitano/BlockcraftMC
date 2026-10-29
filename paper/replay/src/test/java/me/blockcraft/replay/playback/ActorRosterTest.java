package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayEvent;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActorRosterTest {

    @Test
    public void stationaryActorsReturnWhenTheirChunkArrivesAfterTheSnapshot() {
        var roster = new ActorRoster();
        var actor = new ReplayEvent.Actor(UUID.randomUUID(), UUID.randomUUID(), 1, 64, 1, 0, 0,
                "STANDING", List.of());
        var initial = new ReplayEvent.Actors(0, List.of(actor));
        roster.update(initial);
        assertEquals(initial, roster.takeUpdate().orElseThrow());
        assertTrue(roster.takeUpdate().isEmpty());

        roster.terrainReady();
        assertEquals(initial, roster.takeUpdate().orElseThrow());
        assertTrue(roster.takeUpdate().isEmpty());

        var hidden = new ReplayEvent.Actors(8, List.of());
        roster.update(hidden);
        roster.terrainReady();
        assertEquals(hidden, roster.takeUpdate().orElseThrow());
        roster.reset();
        roster.terrainReady();
        assertFalse(roster.takeUpdate().isPresent());
    }
}
