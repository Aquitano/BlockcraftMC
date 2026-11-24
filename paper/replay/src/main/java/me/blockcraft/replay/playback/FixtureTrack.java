package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

/** Every recorded fixture's history, so that any tick shows the displays and dropped items there were. */
final class FixtureTrack {

    /** A fixture as it shows, with its entity data filled in from its last change of content. */
    record Shown(UUID id, UUID world, double x, double y, double z, float yaw, float pitch, String entity,
                 float[] transformation, int interpolation) { }

    private final Map<UUID, NavigableMap<Integer, Shown>> histories;

    private FixtureTrack(Map<UUID, NavigableMap<Integer, Shown>> histories) {
        this.histories = histories;
    }

    /** A gone fixture keeps a null entry, which hides it from that tick on. */
    static FixtureTrack of(List<ReplayEvent> events) {
        var histories = new HashMap<UUID, NavigableMap<Integer, Shown>>();
        var entities = new HashMap<UUID, String>();
        for (var event : events) {
            switch (event) {
                case ReplayEvent.Fixture fixture -> {
                    var entity = fixture.entity().isEmpty() ? entities.get(fixture.id()) : fixture.entity();
                    entities.put(fixture.id(), entity);
                    histories.computeIfAbsent(fixture.id(), ignored -> new TreeMap<>()).put(fixture.tick(),
                            new Shown(fixture.id(), fixture.world(), fixture.x(), fixture.y(), fixture.z(), fixture.yaw(),
                                    fixture.pitch(), entity, fixture.transformation(), fixture.interpolation()));
                }
                case ReplayEvent.FixtureGone gone -> {
                    var history = histories.get(gone.id());
                    if (history != null) {
                        history.put(gone.tick(), null);
                    }
                }
                default -> { }
            }
        }
        return new FixtureTrack(Map.copyOf(histories));
    }

    List<Shown> at(int tick) {
        var shown = new ArrayList<Shown>();
        for (var history : histories.values()) {
            var entry = history.floorEntry(tick);
            if (entry != null && entry.getValue() != null) {
                shown.add(entry.getValue());
            }
        }
        return shown;
    }
}
