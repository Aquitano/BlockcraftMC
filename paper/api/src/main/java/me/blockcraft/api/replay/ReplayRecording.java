package me.blockcraft.api.replay;

import java.util.Optional;
import java.util.UUID;

public interface ReplayRecording {

    record Position(UUID replay, int tick) { }

    /** Thread-safe snapshot of the latest recorded tick; empty without an active recording for this player. */
    Optional<Position> position(UUID player);
}
