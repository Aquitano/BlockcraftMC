package me.blockcraft.replay.recording;

import me.blockcraft.api.replay.ReplayRecording;
import org.junit.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecordingPositionsTest {

    @Test
    public void queuedSignalsKeepTheirRecordingAndTickAfterTheRoundEndsOrChanges() throws Exception {
        var positions = new RecordingPositions();
        var player = UUID.randomUUID();
        var replay = UUID.randomUUID();
        positions.update(replay, 40, List.of(player));
        try (var worker = Executors.newSingleThreadExecutor()) {
            var captured = worker.submit(() -> positions.position(player).orElseThrow()).get();
            positions.update(replay, 80, List.of(player));
            positions.clear();
            assertTrue(positions.position(player).isEmpty());
            positions.update(UUID.randomUUID(), 0, List.of(player));
            assertEquals(new ReplayRecording.Position(replay, 40), captured);
            assertTrue(positions.position(UUID.randomUUID()).isEmpty());
        }
    }
}
