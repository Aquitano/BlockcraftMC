package me.blockcraft.replay.playback;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.replay.data.ReplayData;
import me.blockcraft.replay.data.ReplayEvent;
import me.blockcraft.replay.data.ReplayHeader;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PlaybackSession {

    private static final List<EquipmentSlot> EQUIPMENT = List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private final Player viewer;
    private final ReplayData data;
    private final ViewerState original;
    private final PlaybackClock clock;
    private final UUID session = UUID.randomUUID();
    private final Map<UUID, World> worlds = new LinkedHashMap<>();
    private final Map<UUID, Mannequin> actors = new HashMap<>();
    private final Map<UUID, ReplayEvent.Actor> actorStates = new HashMap<>();
    private final Map<UUID, ReplayHeader.Participant> participants = new HashMap<>();
    private final Map<ChunkKey, PlaybackChunk> chunks = new LinkedHashMap<>();
    private final ArrayDeque<PlaybackChunk> clearing = new ArrayDeque<>();
    private final ActorRoster roster = new ActorRoster();
    private int eventIndex;
    private int blockIndex;
    private int clearIndex;
    private boolean arrived;
    private boolean preparing = true;

    record ChunkKey(UUID world, int x, int z) { }

    PlaybackSession(Player viewer, ReplayData data) {
        this.viewer = viewer;
        this.data = data;
        original = ViewerState.capture(viewer);
        clock = new PlaybackClock(data.durationTicks());
        data.header().participants().forEach(participant -> participants.put(participant.id(), participant));
    }

    void begin() {
        viewer.getInventory().clear();
        viewer.setGameMode(GameMode.ADVENTURE);
        viewer.setAllowFlight(true);
        viewer.setFlying(true);
        viewer.setCollidable(false);
        viewer.setAffectsSpawning(false);
        viewer.setInvulnerable(true);
        viewer.setFallDistance(0);
    }

    Player viewer() { return viewer; }
    List<World> worlds() { return List.copyOf(worlds.values()); }
    boolean owns(World world) { return worlds.containsValue(world); }

    boolean permittedPosition(Location location) {
        if (preparing || location.getY() < location.getWorld().getMinHeight()
                || location.getY() > location.getWorld().getMaxHeight() + 32) {
            return false;
        }
        return worlds.entrySet().stream().anyMatch(entry -> entry.getValue().equals(location.getWorld())
                && readyChunk(new ChunkKey(entry.getKey(), location.getBlockX() >> 4, location.getBlockZ() >> 4)));
    }

    private boolean readyChunk(ChunkKey key) {
        var chunk = chunks.get(key);
        return chunk != null && chunk.ready();
    }

    PlaybackManager.State state() {
        return new PlaybackManager.State(data.header().id(), clock.tick(), data.durationTicks(),
                clock.paused(), clock.speed(), preparing);
    }

    void pause() { clock.pause(); }
    boolean speed(double speed) { return clock.speed(speed); }

    boolean seek(int tick) {
        if (preparing || !clock.seek(tick)) {
            return false;
        }
        actors.values().forEach(Mannequin::remove);
        actors.clear();
        actorStates.clear();
        chunks.values().forEach(PlaybackChunk::rebuilding);
        clearing.addAll(chunks.values());
        eventIndex = 0;
        blockIndex = 0;
        clearIndex = 0;
        roster.reset();
        arrived = false;
        preparing = true;
        return true;
    }

    void pulse(int writesRemaining, int scansRemaining) {
        if (!preparing) {
            clock.advance();
        }
        preparing = true;
        var air = Material.AIR.createBlockData();
        while (writesRemaining > 0 && scansRemaining > 0 && !clearing.isEmpty()) {
            var chunk = clearing.getFirst();
            int occupied = chunk.nextOccupied(clearIndex);
            scansRemaining--;
            if (occupied < 0) {
                clearing.removeFirst();
                clearIndex = 0;
            } else {
                setChunkBlock(chunk, occupied, air);
                clearIndex = occupied + 1;
                writesRemaining--;
            }
        }
        var palette = new HashMap<String, BlockData>();
        while (scansRemaining > 0 && clearing.isEmpty() && eventIndex < data.events().size()) {
            var event = data.events().get(eventIndex);
            if (event.tick() > clock.tick()) {
                break;
            }
            switch (event) {
                case ReplayEvent.Chunk chunk -> {
                    world(chunk.world());
                    var terrain = chunks.computeIfAbsent(new ChunkKey(chunk.world(), chunk.x(), chunk.z()),
                            ignored -> new PlaybackChunk(chunk));
                    while (scansRemaining > 0 && blockIndex < chunk.blocks().length) {
                        var encoded = chunk.palette().get(chunk.blocks()[blockIndex]);
                        if (terrain.needsWrite(blockIndex, encoded)) {
                            if (writesRemaining == 0) {
                                return;
                            }
                            var block = palette.computeIfAbsent(encoded, Bukkit::createBlockData);
                            setChunkBlock(terrain, blockIndex, block);
                            writesRemaining--;
                        }
                        blockIndex++;
                        scansRemaining--;
                    }
                    if (blockIndex < chunk.blocks().length) {
                        return;
                    }
                    terrain.complete();
                    roster.terrainReady();
                }
                case ReplayEvent.Blocks blocks -> {
                    while (scansRemaining > 0 && blockIndex < blocks.blocks().size()) {
                        var block = blocks.blocks().get(blockIndex);
                        var key = new ChunkKey(block.world(), Math.floorDiv(block.x(), 16), Math.floorDiv(block.z(), 16));
                        var terrain = chunks.get(key);
                        int index = terrain == null || !terrain.ready() ? -1 : terrain.index(block.x(), block.y(), block.z());
                        if (index >= 0 && terrain.needsWrite(index, block.data())) {
                            if (writesRemaining == 0) {
                                return;
                            }
                            setChunkBlock(terrain, index, palette.computeIfAbsent(block.data(), Bukkit::createBlockData));
                            writesRemaining--;
                        }
                        blockIndex++;
                        scansRemaining--;
                    }
                    if (blockIndex < blocks.blocks().size()) {
                        return;
                    }
                }
                case ReplayEvent.Actors snapshot -> {
                    roster.update(snapshot);
                }
            }
            eventIndex++;
            blockIndex = 0;
            scansRemaining--;
        }
        preparing = !clearing.isEmpty() || (eventIndex < data.events().size()
                && data.events().get(eventIndex).tick() <= clock.tick());
        if (!preparing) {
            roster.takeUpdate().ifPresent(this::syncActors);
        }
    }

    private World world(UUID source) {
        return worlds.computeIfAbsent(source, id -> {
            var key = new NamespacedKey("replay", session + "_" + id);
            var world = Worlds.createArena(key);
            world.setAutoSave(false);
            world.setGameRule(GameRules.BLOCK_DROPS, false);
            world.setGameRule(GameRules.ENTITY_DROPS, false);
            world.setGameRule(GameRules.MOB_DROPS, false);
            world.setGameRule(GameRules.SPAWNER_BLOCKS_WORK, false);
            world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
            return world;
        });
    }

    private void setChunkBlock(PlaybackChunk terrain, int index, BlockData block) {
        var chunk = terrain.baseline();
        var world = world(chunk.world());
        int x = chunk.x() * 16 + index % 16;
        int z = chunk.z() * 16 + (index / 16) % 16;
        int y = chunk.minY() + index / 256;
        world.getBlockAt(x, y, z).setBlockData(block, false);
        terrain.written(index, !block.getMaterial().isAir());
    }

    private void syncActors(ReplayEvent.Actors snapshot) {
        var present = new HashSet<UUID>();
        for (var actor : snapshot.actors()) {
            var participant = participants.get(actor.id());
            if (participant == null) {
                continue;
            }
            var world = worlds.get(actor.world());
            if (world == null || !readyChunk(new ChunkKey(actor.world(), (int) Math.floor(actor.x()) >> 4,
                    (int) Math.floor(actor.z()) >> 4))) {
                continue;
            }
            present.add(actor.id());
            var location = new Location(world, actor.x(), actor.y(), actor.z(), actor.yaw(), actor.pitch());
            var figure = actors.get(actor.id());
            if (figure != null && !figure.getWorld().equals(world)) {
                figure.remove();
                actors.remove(actor.id());
                actorStates.remove(actor.id());
                figure = null;
            }
            if (figure == null) {
                figure = world.spawn(location, Mannequin.class, mannequin -> {
                    var profile = Bukkit.createProfileExact(UUID.randomUUID(), participant.name());
                    if (participant.texture() != null && !participant.texture().isBlank()) {
                        profile.setProperty(new ProfileProperty("textures", participant.texture(), participant.signature()));
                    }
                    mannequin.setProfile(ResolvableProfile.resolvableProfile(profile));
                    mannequin.setDescription(null);
                    mannequin.setImmovable(true);
                    mannequin.setInvulnerable(true);
                    mannequin.setAI(false);
                    mannequin.setSilent(true);
                    mannequin.setPersistent(false);
                    mannequin.setRemoveWhenFarAway(false);
                    mannequin.setCollidable(false);
                    mannequin.customName(Component.text(participant.name()));
                    mannequin.setCustomNameVisible(true);
                });
                actors.put(actor.id(), figure);
            }
            figure.teleport(location);
            var previous = actorStates.put(actor.id(), actor);
            var pose = Pose.valueOf(actor.pose());
            if (Mannequin.validPoses().contains(pose)) {
                figure.setPose(pose, true);
            }
            var equipment = figure.getEquipment();
            for (int i = 0; i < EQUIPMENT.size(); i++) {
                var bytes = actor.equipment().get(i);
                if (previous == null || !Arrays.equals(previous.equipment().get(i), bytes)) {
                    equipment.setItem(EQUIPMENT.get(i), bytes.length == 0 ? null : ItemStack.deserializeBytes(bytes));
                }
            }
            if (!arrived) {
                if (!viewer.teleport(location.clone().add(0, 2, 0))) {
                    throw new IllegalStateException("Could not enter replay world");
                }
                arrived = true;
                viewer.setFlying(true);
            }
        }
        actors.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().remove();
            actorStates.remove(entry.getKey());
            return true;
        });
    }

    void restore() {
        actors.values().forEach(Mannequin::remove);
        actors.clear();
        actorStates.clear();
        original.restore(viewer);
    }
}
