package me.blockcraft.replay.recording;

import me.blockcraft.api.replay.ReplayRecording;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

final class RecordingPositions implements ReplayRecording {

    private volatile Map<UUID, Position> positions = Map.of();

    void update(UUID replay, int tick, Collection<UUID> players) {
        var position = new Position(replay, tick);
        positions = players.stream().collect(Collectors.toUnmodifiableMap(player -> player, player -> position));
    }

    void clear() {
        positions = Map.of();
    }

    @Override
    public Optional<Position> position(UUID player) {
        return Optional.ofNullable(positions.get(player));
    }
}
