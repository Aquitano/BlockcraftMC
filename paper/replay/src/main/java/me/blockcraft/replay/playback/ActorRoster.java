package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayEvent;

import java.util.Optional;

final class ActorRoster {

    private ReplayEvent.Actors latest;
    private boolean changed;

    void update(ReplayEvent.Actors snapshot) {
        latest = snapshot;
        changed = true;
    }

    void terrainReady() {
        changed = true;
    }

    Optional<ReplayEvent.Actors> takeUpdate() {
        if (!changed || latest == null) {
            return Optional.empty();
        }
        changed = false;
        return Optional.of(latest);
    }

    void reset() {
        latest = null;
        changed = false;
    }
}
