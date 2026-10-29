package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayData;
import me.blockcraft.replay.data.ReplayEvent;
import me.blockcraft.replay.data.ReplayHeader;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A loaded recording indexed for playback: terrain per recorded world, the block change log, the motion track and
 * how every recorded entity looked.
 */
public final class PlaybackTimeline {

    private final ReplayHeader header;
    private final int durationTicks;
    private final Map<UUID, RecordedTerrain> terrain;
    private final List<TerrainLog.Change> changes;
    private final Map<String, BlockData> blocks;
    private final MotionTrack motion;
    private final Map<UUID, String> appearances;

    private PlaybackTimeline(ReplayHeader header, int durationTicks, Map<UUID, RecordedTerrain> terrain,
                             List<TerrainLog.Change> changes, Map<String, BlockData> blocks, MotionTrack motion,
                             Map<UUID, String> appearances) {
        this.header = header;
        this.durationTicks = durationTicks;
        this.terrain = terrain;
        this.changes = changes;
        this.blocks = blocks;
        this.motion = motion;
        this.appearances = appearances;
    }

    /** Parses every block state and indexes the recording; meant for a background thread. */
    public static PlaybackTimeline of(ReplayData data) {
        var chunks = new HashMap<UUID, Map<Long, ReplayEvent.Chunk>>();
        var frames = new ArrayList<ReplayEvent.Frame>();
        var appearances = new HashMap<UUID, String>();
        for (var event : data.events()) {
            switch (event) {
                case ReplayEvent.Chunk chunk -> chunks.computeIfAbsent(chunk.world(), ignored -> new HashMap<>())
                        .putIfAbsent(RecordedTerrain.key(chunk.x(), chunk.z()), chunk);
                case ReplayEvent.Frame frame -> {
                    frames.add(frame);
                    frame.appearances().forEach(appearance -> appearances.putIfAbsent(appearance.id(), appearance.entity()));
                }
                case ReplayEvent.Blocks ignored -> { }
            }
        }
        var changes = TerrainLog.changes(data.events());
        var blocks = new HashMap<String, BlockData>();
        chunks.values().forEach(world -> world.values().forEach(chunk ->
                chunk.palette().forEach(state -> blocks.computeIfAbsent(state, Bukkit::createBlockData))));
        changes.forEach(change -> blocks.computeIfAbsent(change.after(), Bukkit::createBlockData));
        var parsed = Map.copyOf(blocks);
        var terrain = new HashMap<UUID, RecordedTerrain>();
        chunks.forEach((world, recorded) -> terrain.put(world, new RecordedTerrain(recorded, parsed)));
        return new PlaybackTimeline(data.header(), data.durationTicks(), Map.copyOf(terrain), changes, parsed,
                new MotionTrack(frames), Map.copyOf(appearances));
    }

    public ReplayHeader header() {
        return header;
    }

    public boolean empty() {
        return terrain.isEmpty();
    }

    int durationTicks() { return durationTicks; }
    Map<UUID, RecordedTerrain> terrain() { return terrain; }
    List<TerrainLog.Change> changes() { return changes; }
    BlockData block(String state) { return blocks.get(state); }
    MotionTrack motion() { return motion; }
    Optional<String> appearance(UUID prop) { return Optional.ofNullable(appearances.get(prop)); }
}
