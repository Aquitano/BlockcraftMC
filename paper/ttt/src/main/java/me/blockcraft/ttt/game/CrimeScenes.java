package me.blockcraft.ttt.game;

import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What the crime camera shows: everyone's last few seconds are kept, and when someone dies, the moments around it
 * near the body become a scene. Played back at the corpse, the scene shows its players as faceless figures only the
 * viewer sees, the dead one glowing: where the killer came from and where they went, but not who it was.
 */
public final class CrimeScenes {

    private static final long STEP = 2L;
    private static final double NEAR = 24.0;

    private record Sample(Location at, boolean sneaking) {
    }

    private static final class Scene {
        private final UUID victim;
        private final List<UUID> cast;
        private final List<Map<UUID, Sample>> frames;
        private int toRecord;

        private Scene(UUID victim, List<UUID> cast, List<Map<UUID, Sample>> frames, int toRecord) {
            this.victim = victim;
            this.cast = cast;
            this.frames = frames;
            this.toRecord = toRecord;
        }
    }

    private final Plugin plugin;
    private final Supplier<List<Player>> alive;
    private final int framesBefore;
    private final int framesAfter;
    private final Deque<Map<UUID, Sample>> recent = new ArrayDeque<>();
    private final Map<UUID, Scene> scenes = new HashMap<>();
    private final List<BukkitTask> playing = new ArrayList<>();
    private final List<Mannequin> figures = new ArrayList<>();
    private BukkitTask recorder;

    public CrimeScenes(Plugin plugin, Supplier<List<Player>> alive, int seconds) {
        this.plugin = plugin;
        this.alive = alive;
        this.framesBefore = (int) (seconds * 20 / STEP);
        this.framesAfter = framesBefore / 3;
    }

    public void start() {
        recorder = plugin.getServer().getScheduler().runTaskTimer(plugin, this::record, STEP, STEP);
    }

    /** Freezes the moments before {@code victim}'s death and goes on recording the ones after. */
    public void died(Player victim) {
        var at = victim.getLocation();
        var frames = new ArrayList<>(recent);
        var cast = new ArrayList<UUID>();
        cast.add(victim.getUniqueId());
        for (var frame : frames) {
            frame.forEach((id, sample) -> {
                if (!cast.contains(id) && sample.at().getWorld().equals(at.getWorld()) && sample.at().distance(at) <= NEAR) {
                    cast.add(id);
                }
            });
        }
        scenes.put(victim.getUniqueId(), new Scene(victim.getUniqueId(), cast, frames, framesAfter));
    }

    /** Plays the scene of {@code victim}'s death to {@code viewer}; false if there is none. */
    public boolean play(Player viewer, UUID victim) {
        var scene = scenes.get(victim);
        if (scene == null || scene.frames.isEmpty()) {
            return false;
        }
        var cast = new HashMap<UUID, Mannequin>();
        for (var id : scene.cast) {
            scene.frames.stream().map(frame -> frame.get(id)).filter(Objects::nonNull).findFirst()
                    .ifPresent(start -> cast.put(id, figure(viewer, start.at(), id.equals(scene.victim))));
        }
        var frames = List.copyOf(scene.frames);
        var fallen = Mannequin.validPoses().contains(Pose.SLEEPING) ? Pose.SLEEPING : Pose.SWIMMING;
        playing.add(new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                if (index >= frames.size() || !viewer.isOnline()) {
                    cast.values().forEach(figure -> {
                        figures.remove(figure);
                        figure.remove();
                    });
                    playing.removeIf(task -> task.getTaskId() == getTaskId());
                    cancel();
                    return;
                }
                var frame = frames.get(index++);
                cast.forEach((id, figure) -> {
                    var sample = frame.get(id);
                    if (sample != null) {
                        figure.teleport(sample.at());
                        figure.setPose(sample.sneaking() ? Pose.SNEAKING : Pose.STANDING, true);
                    } else if (id.equals(scene.victim)) {
                        figure.setPose(fallen, true);
                    }
                });
            }
        }.runTaskTimer(plugin, STEP, STEP));
        return true;
    }

    public void stop() {
        if (recorder != null) {
            recorder.cancel();
            recorder = null;
        }
        playing.forEach(BukkitTask::cancel);
        playing.clear();
        figures.forEach(Mannequin::remove);
        figures.clear();
        recent.clear();
        scenes.clear();
    }

    private Mannequin figure(Player viewer, Location at, boolean victim) {
        var figure = at.getWorld().spawn(at, Mannequin.class, mannequin -> {
            mannequin.setVisibleByDefault(false);
            mannequin.setProfile(Mannequin.defaultProfile());
            mannequin.setDescription(null);
            mannequin.setImmovable(true);
            mannequin.setInvulnerable(true);
            mannequin.setAI(false);
            mannequin.setSilent(true);
            mannequin.setPersistent(false);
            mannequin.setGlowing(victim);
        });
        viewer.showEntity(plugin, figure);
        figures.add(figure);
        return figure;
    }

    private void record() {
        var frame = new HashMap<UUID, Sample>();
        alive.get().forEach(player -> frame.put(player.getUniqueId(), new Sample(player.getLocation(), player.isSneaking())));
        recent.addLast(frame);
        while (recent.size() > framesBefore) {
            recent.removeFirst();
        }
        for (var scene : scenes.values()) {
            if (scene.toRecord > 0) {
                scene.toRecord--;
                var near = new HashMap<UUID, Sample>();
                frame.forEach((id, sample) -> {
                    if (scene.cast.contains(id)) {
                        near.put(id, sample);
                    }
                });
                scene.frames.add(near);
            }
        }
    }
}
