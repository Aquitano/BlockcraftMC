package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayEvent;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FixtureTrackTest {

    private static final UUID WORLD = UUID.randomUUID();

    @Test
    public void aFixtureShowsItsLatestChangeWithItsLastEntityDataUntilItIsGone() {
        var id = UUID.randomUUID();
        var track = FixtureTrack.of(List.of(
                fixture(10, id, 1, "{text:'Tür'}"),
                fixture(20, id, 2, ""),
                new ReplayEvent.FixtureGone(30, id),
                fixture(40, id, 3, "{text:'Offen'}")));

        assertEquals(List.of(), track.at(9));
        assertEquals(1, track.at(15).getFirst().x(), 0);
        var moved = track.at(25).getFirst();
        assertEquals(2, moved.x(), 0);
        assertEquals("{text:'Tür'}", moved.entity());
        assertEquals(List.of(), track.at(35));
        assertEquals("{text:'Offen'}", track.at(40).getFirst().entity());
    }

    private static ReplayEvent.Fixture fixture(int tick, UUID id, double x, String entity) {
        return new ReplayEvent.Fixture(tick, id, WORLD, x, 64, 0, 0, 0, entity, new float[0], 0);
    }
}
