package me.blockcraft.replay.data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReplayHeader(UUID id, String game, String server, String minecraft, String build,
                           String map, Instant startedAt, List<Participant> participants) {

    public ReplayHeader {
        participants = List.copyOf(participants);
    }

    public record Participant(UUID id, String name, String texture, String signature) { }
}
