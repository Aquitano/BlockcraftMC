package me.blockcraft.api.scene;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

public record SceneTimeline(World world, CameraPath camera, List<Cue> cues, SkipMode skipMode, Frame frame) {
    public enum SkipMode { COMPLETE, HOLD }
    public record Cue(int tick, Runnable action) { }
    @FunctionalInterface
    public interface Frame {
        void show(Player player, int tick, int duration, boolean skipped);
    }

    public SceneTimeline {
        cues = cues.stream().sorted(Comparator.comparingInt(Cue::tick)).toList();
        if (cues.stream().anyMatch(cue -> cue.tick() < 0 || cue.tick() > camera.duration())) {
            throw new IllegalArgumentException("Scene cue lies outside camera duration");
        }
    }
}
