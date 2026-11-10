package me.blockcraft.replay.data;

import java.util.List;

public record ReplayData(ReplayHeader header, List<ReplayEvent> events, int durationTicks, String endReason) {

    public ReplayData {
        events = List.copyOf(events);
    }
}
