package me.blockcraft.replay.playback;

import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.replay.data.ReplayEvent;
import me.blockcraft.replay.data.ReplayHeader;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

final class PlaybackSession {

    private static final double CAMERA_DISTANCE = 3;
    private static final double CAMERA_HEIGHT = 2.5;
    private static final double EYE_HEIGHT = 1.62;
    private static final int BORDER_MARGIN = 16;

    private final Plugin plugin;
    private final Player viewer;
    private final PlaybackTimeline timeline;
    private final ViewerState original;
    private final PlaybackClock clock;
    private final PlaybackHud hud;
    private final TerrainLog terrain;
    private final UUID session = UUID.randomUUID();
    private final Map<UUID, World> worlds = new LinkedHashMap<>();
    private final PlaybackCast cast;
    private final Map<TerrainLog.Position, String> pending = new LinkedHashMap<>();
    private int loadingChunks;
    private int shownTick;
    private boolean arrived;
    private boolean jumped;
    private boolean leaving;
    private boolean closed;

    PlaybackSession(Plugin plugin, Player viewer, PlaybackTimeline timeline, Messages messages) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.timeline = timeline;
        original = ViewerState.capture(viewer);
        clock = new PlaybackClock(timeline.durationTicks());
        hud = new PlaybackHud(viewer, messages);
        terrain = new TerrainLog(timeline.changes());
        cast = new PlaybackCast(viewer, timeline, worlds);
    }

    void begin() {
        viewer.getInventory().clear();
        viewer.getInventory().setHeldItemSlot(PlaybackControl.home());
        viewer.setGameMode(GameMode.ADVENTURE);
        viewer.setAllowFlight(true);
        viewer.setFlying(true);
        viewer.setCollidable(false);
        viewer.setAffectsSpawning(false);
        viewer.setInvulnerable(true);
        viewer.setFallDistance(0);
        hud.show();
        hud.update(state());
        timeline.terrain().forEach((source, recorded) -> {
            var world = Worlds.createArena(WorldCreator.ofKey(new NamespacedKey("replay", session + "_" + source))
                    .generator(recorded));
            worlds.put(source, world);
            world.setAutoSave(false);
            world.setGameRule(GameRules.BLOCK_DROPS, false);
            world.setGameRule(GameRules.ENTITY_DROPS, false);
            world.setGameRule(GameRules.MOB_DROPS, false);
            world.setGameRule(GameRules.SPAWNER_BLOCKS_WORK, false);
            world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
            border(world, recorded.chunks());
            recorded.chunks().forEach(chunk -> load(world, chunk));
        });
    }

    /** Generates the chunk off the main thread, then keeps it loaded so recorded block changes never load it synchronously. */
    private void load(World world, ReplayEvent.Chunk chunk) {
        loadingChunks++;
        world.getChunkAtAsync(chunk.x(), chunk.z()).whenComplete((loaded, failure) -> {
            loadingChunks--;
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Could not generate replay chunk " + chunk.x() + "," + chunk.z(), failure);
            } else if (!closed && plugin.isEnabled()) {
                world.addPluginChunkTicket(chunk.x(), chunk.z(), plugin);
            }
        });
    }

    private static void border(World world, Collection<ReplayEvent.Chunk> chunks) {
        int minX = chunks.stream().mapToInt(ReplayEvent.Chunk::x).min().orElse(0) * 16;
        int maxX = (chunks.stream().mapToInt(ReplayEvent.Chunk::x).max().orElse(0) + 1) * 16;
        int minZ = chunks.stream().mapToInt(ReplayEvent.Chunk::z).min().orElse(0) * 16;
        int maxZ = (chunks.stream().mapToInt(ReplayEvent.Chunk::z).max().orElse(0) + 1) * 16;
        var border = world.getWorldBorder();
        border.setCenter((minX + maxX) / 2.0, (minZ + maxZ) / 2.0);
        border.setSize(Math.max(maxX - minX, maxZ - minZ) + 2.0 * BORDER_MARGIN);
    }

    Player viewer() { return viewer; }
    List<World> worlds() { return List.copyOf(worlds.values()); }
    boolean owns(World world) { return worlds.containsValue(world); }

    PlaybackManager.State state() {
        return new PlaybackManager.State(timeline.header().id(), clock.tick(), timeline.durationTicks(),
                clock.paused(), clock.speed(), loadingChunks > 0 || !pending.isEmpty());
    }

    void toggle() { clock.toggle(); }
    boolean speed(double speed) { return clock.speed(speed); }

    boolean seek(int tick) {
        if (!arrived) {
            return false;
        }
        clock.seek(tick);
        jumped = true;
        return true;
    }

    void pulse(int writes) {
        if (leaving) {
            return;
        }
        if (loadingChunks > 0) {
            hud.update(state());
            return;
        }
        if (arrived && pending.isEmpty()) {
            clock.advance();
        }
        pending.putAll(terrain.seek(clock.tick()));
        write(writes);
        var motion = timeline.motion();
        if (!jumped) {
            cast.perform(motion.animations(shownTick, clock.tick()));
        }
        jumped = false;
        shownTick = clock.tick();
        double time = clock.time();
        var actors = motion.actors(time);
        boolean advancing = !clock.paused() && !clock.finished() && pending.isEmpty();
        cast.show(actors, motion.props(time), motion.props(advancing ? time + clock.speed() : time));
        if (!arrived) {
            arrive(actors);
        }
        hud.update(state());
    }

    private void write(int budget) {
        var writes = pending.entrySet().iterator();
        for (int written = 0; written < budget && writes.hasNext(); written++) {
            var write = writes.next();
            writes.remove();
            var position = write.getKey();
            var world = worlds.get(position.world());
            if (world != null) {
                world.getBlockAt(position.x(), position.y(), position.z()).setBlockData(timeline.block(write.getValue()), false);
            }
        }
    }

    /** Starts behind the viewer's own figure if they played, otherwise behind the first one recorded. */
    private void arrive(List<ReplayEvent.Actor> actors) {
        var start = actors.stream()
                .sorted(Comparator.comparing(actor -> !actor.id().equals(viewer.getUniqueId())))
                .flatMap(actor -> cast.location(actor.id()).stream()).findFirst()
                .map(PlaybackSession::camera).orElseGet(this::overview);
        if (!viewer.teleport(start)) {
            throw new IllegalStateException("Could not enter replay world");
        }
        viewer.setFlying(true);
        arrived = true;
    }

    private Location overview() {
        var source = timeline.terrain().entrySet().iterator().next();
        var chunk = source.getValue().chunks().iterator().next();
        var world = worlds.get(source.getKey());
        int x = chunk.x() * 16 + 8;
        int z = chunk.z() * 16 + 8;
        return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + CAMERA_HEIGHT, z + 0.5);
    }

    /** Over the shoulder of whoever stands at {@code target}, looking at them, or just above them in a tight spot. */
    private static Location camera(Location target) {
        double yaw = Math.toRadians(target.getYaw());
        var camera = target.clone().add(Math.sin(yaw) * CAMERA_DISTANCE, CAMERA_HEIGHT, -Math.cos(yaw) * CAMERA_DISTANCE);
        if (!camera.getBlock().isPassable() || !camera.clone().add(0, 1, 0).getBlock().isPassable()) {
            return target.clone().add(0, CAMERA_HEIGHT, 0);
        }
        return camera.setDirection(target.clone().add(0, EYE_HEIGHT, 0).toVector().subtract(camera.toVector()));
    }

    List<ReplayHeader.Participant> present() {
        return cast.present();
    }

    boolean focus(UUID actor) {
        var target = cast.location(actor);
        if (leaving || target.isEmpty() || !viewer.teleport(camera(target.get()))) {
            return false;
        }
        viewer.setFlying(true);
        return true;
    }

    /** Holds the last picture while the proxy moves the viewer back where they came from. */
    void leave() {
        leaving = true;
        clock.pause();
        cast.hold();
        viewer.getInventory().clear();
        hud.hide();
    }

    /** Returns everything the viewer had and reports whether they are back where they started. */
    boolean restore() {
        closed = true;
        cast.clear();
        hud.hide();
        return original.restore(viewer);
    }
}
