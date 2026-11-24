package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayData;
import me.blockcraft.replay.data.ReplayEvent;
import me.blockcraft.replay.data.ReplayHeader;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class PlaybackTimelineTest {

    private static final UUID WORLD = UUID.randomUUID();

    @Test
    public void daytimeHoldsTheLatestRecordAndStartsWithTheFirst() {
        var header = new ReplayHeader(UUID.randomUUID(), "zombies", "test", "26.3", "test", "arena", Instant.EPOCH, List.of());
        var timeline = PlaybackTimeline.of(new ReplayData(header, List.of(
                new ReplayEvent.Daytime(5, WORLD, 13_000), new ReplayEvent.Daytime(400, WORLD, 13_400)), 800, "MATCH_END"));

        assertEquals(OptionalLong.of(13_000), timeline.daytime(WORLD, 0));
        assertEquals(OptionalLong.of(13_000), timeline.daytime(WORLD, 399));
        assertEquals(OptionalLong.of(13_400), timeline.daytime(WORLD, 800));
        assertEquals(OptionalLong.empty(), timeline.daytime(UUID.randomUUID(), 0));
    }
}
