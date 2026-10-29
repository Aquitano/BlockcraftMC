package me.blockcraft.replay.recording;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.game.RoundPhaseChangeEvent;
import me.blockcraft.replay.data.ReplayEvent;
import me.blockcraft.replay.data.ReplayHeader;
import me.blockcraft.replay.data.ReplayLimits;
import me.blockcraft.replay.storage.ReplayStore;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class MatchRecorder implements Listener, AutoCloseable {

    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final int MAX_EQUIPMENT_BYTES = 65_536;
    private static final int MAX_APPEARANCE_BYTES = 16_384;
    private static final List<BlockFace> NEIGHBORS = List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN);
    private final Plugin plugin;
    private final ReplayStore store;
    private final ReplayLimits limits;
    private final Consumer<ReplayStore.Saved> saved;
    private final Map<Round, List<PublicationGate<ReplayStore.Saved>>> publications = new IdentityHashMap<>();
    private Session session;

    record ChunkKey(UUID world, int x, int z) { }
    private record BlockKey(UUID world, int x, int y, int z) {
        ChunkKey chunk() { return new ChunkKey(world, x >> 4, z >> 4); }
    }
    private record Equipment(List<ItemStack> items, List<byte[]> bytes) { }

    /** An entity seen for the first time; its data is written out on the writer thread. */
    private record Sighting(UUID id, EntitySnapshot snapshot) {
        ReplayEvent.Appearance appearance() {
            String entity = snapshot.getAsString();
            return new ReplayEvent.Appearance(id, entity.getBytes(StandardCharsets.UTF_8).length <= MAX_APPEARANCE_BYTES
                    ? entity : "{id:\"" + snapshot.getEntityType().getKey() + "\"}");
        }
    }

    /** The publication callback can run on the server thread and must submit its work without blocking. */
    public MatchRecorder(Plugin plugin, ReplayStore store, ReplayLimits limits, Consumer<ReplayStore.Saved> saved) {
        this.plugin = plugin;
        this.store = store;
        this.limits = limits;
        this.saved = saved;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPhase(RoundPhaseChangeEvent event) {
        if (event.previous() == GamePhase.LOBBY && event.phase() == GamePhase.INGAME) {
            start(event.round());
        } else if (event.previous() == GamePhase.INGAME) {
            if (session != null && session.round == event.round()) { finish("MATCH_END"); }
            var completed = publications.remove(event.round());
            if (completed != null) { completed.forEach(PublicationGate::ended); }
        }
    }

    private void start(Round round) {
        if (session != null) { finish("ROUND_REPLACED"); }
        var api = BlockcraftApi.get();
        var players = round.players().stream().filter(player -> visible(round, player)).toList();
        if (players.isEmpty() || players.size() > limits.maxParticipants()) {
            plugin.getLogger().warning("Replay skipped: participant count outside recording limits");
            return;
        }
        var header = new ReplayHeader(UUID.randomUUID(), api.server().group(), api.server().name(),
                Bukkit.getMinecraftVersion(), plugin.getPluginMeta().getVersion(), api.map().name(), Instant.now(),
                players.stream().map(MatchRecorder::participant).toList());
        try {
            session = new Session(round, store.begin(header), players.stream().map(Player::getUniqueId).toList());
            publications.computeIfAbsent(round, ignored -> new ArrayList<>()).add(session.publication);
            session.publication.ready().thenAccept(saved).exceptionally(failure -> {
                plugin.getLogger().log(Level.WARNING, "Replay metadata could not be published", failure);
                return null;
            });
            session.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not begin replay", failure);
        }
    }

    private static ReplayHeader.Participant participant(Player player) {
        var texture = player.getPlayerProfile().getProperties().stream()
                .filter(property -> property.getName().equals("textures")).findFirst();
        return new ReplayHeader.Participant(player.getUniqueId(), player.getName(),
                texture.map(property -> property.getValue()).orElse(""),
                texture.map(property -> property.getSignature()).orElse(""));
    }

    /** A dead player waiting to respawn is out of the picture, like a dying mob. */
    private static boolean visible(Round round, Player player) {
        return round.isPlaying(player) && !Round.isWatching(player) && !BlockcraftApi.get().isVanished(player)
                && !player.isDead();
    }

    private void tick() {
        var current = session;
        if (current == null) { return; }
        try {
            if (current.tick >= limits.maxDurationTicks()) { finish("DURATION_LIMIT"); return; }
            if (current.writer.full()) { finish("OBJECT_LIMIT"); return; }
            var players = current.participants.stream().map(Bukkit::getPlayer)
                    .filter(player -> player != null && visible(current.round, player)).toList();
            if (!discover(current, players)) { return; }
            captureChunks(current);
            if (session != current) { return; }
            captureBlocks(current);
            if (session != current) { return; }
            if (current.tick % limits.sampleTicks() == 0) { captureFrame(current, players); }
            current.tick++;
        } catch (RuntimeException failure) {
            abort("Replay capture failed", failure);
        }
    }

    private boolean discover(Session current, List<Player> players) {
        for (var player : players) {
            var location = player.getLocation();
            var world = player.getWorld();
            int centerX = location.getBlockX() >> 4;
            int centerZ = location.getBlockZ() >> 4;
            for (int x = centerX - limits.captureRadius(); x <= centerX + limits.captureRadius(); x++) {
                for (int z = centerZ - limits.captureRadius(); z <= centerZ + limits.captureRadius(); z++) {
                    var key = new ChunkKey(world.getUID(), x, z);
                    if (!world.isChunkLoaded(x, z) || current.known.contains(key)) { continue; }
                    if (current.known.size() >= limits.maxChunks()) { finish("CHUNK_LIMIT"); return false; }
                    current.known.add(key);
                    current.pending.add(key);
                }
            }
        }
        return true;
    }

    private void captureChunks(Session current) {
        for (int count = 0; count < limits.chunksPerTick() && !current.pending.isEmpty(); count++) {
            var key = current.pending.removeFirst();
            var world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.x(), key.z())) {
                current.known.remove(key);
                continue;
            }
            var snapshot = world.getChunkAt(key.x(), key.z()).getChunkSnapshot(false, false, false);
            int tick = current.tick;
            int minY = world.getMinHeight();
            int height = world.getMaxHeight() - minY;
            if (!offer(current, () -> chunk(tick, key, snapshot, minY, height))) { return; }
            current.captured.add(key);
        }
    }

    static ReplayEvent.Chunk chunk(int tick, ChunkKey key, ChunkSnapshot snapshot, int minY, int height) {
        var palette = new LinkedHashMap<String, Integer>();
        var blocks = new int[height * 256];
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    String data = switch (snapshot.getBlockType(x, y + minY, z)) {
                        case AIR, CAVE_AIR, VOID_AIR -> "minecraft:air";
                        default -> snapshot.getBlockData(x, y + minY, z).getAsString();
                    };
                    blocks[(y * 16 + z) * 16 + x] = palette.computeIfAbsent(data, ignored -> palette.size());
                }
            }
        }
        return new ReplayEvent.Chunk(tick, key.world(), key.x(), key.z(), minY, height, List.copyOf(palette.keySet()), blocks);
    }

    private void captureBlocks(Session current) {
        if (current.dirty.isEmpty()) { return; }
        var changes = new ArrayList<ReplayEvent.Block>();
        for (var key : current.dirty) {
            var world = Bukkit.getWorld(key.world());
            if (world != null && world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                changes.add(new ReplayEvent.Block(key.world(), key.x(), key.y(), key.z(),
                        world.getBlockAt(key.x(), key.y(), key.z()).getBlockData().getAsString()));
            }
        }
        current.dirty.clear();
        if (!changes.isEmpty()) {
            var event = new ReplayEvent.Blocks(current.tick, changes);
            offer(current, () -> event);
        }
    }

    private void captureFrame(Session current, List<Player> players) {
        var actors = new ArrayList<ReplayEvent.Actor>();
        for (var player : players) {
            var location = player.getLocation();
            var items = SLOTS.stream().map(slot -> player.getInventory().getItem(slot).clone()).toList();
            var previous = current.equipment.get(player.getUniqueId());
            if (previous == null || !previous.items().equals(items)) {
                var bytes = items.stream().map(VisibleEquipment::serialize).toList();
                if (bytes.stream().mapToInt(item -> item.length).sum() > MAX_EQUIPMENT_BYTES) {
                    finish("EQUIPMENT_LIMIT");
                    return;
                }
                previous = new Equipment(items, bytes);
                current.equipment.put(player.getUniqueId(), previous);
            }
            actors.add(new ReplayEvent.Actor(player.getUniqueId(), player.getWorld().getUID(), location.getX(),
                    location.getY(), location.getZ(), location.getYaw(), location.getPitch(), player.getPose().name(), previous.bytes()));
        }
        var props = new ArrayList<ReplayEvent.Prop>();
        var sightings = new ArrayList<Sighting>();
        captureProps(current, players, props, sightings);
        var animations = List.copyOf(current.animations);
        current.animations.clear();
        if (ActorFrames.same(current.actors, actors) && props.equals(current.props) && sightings.isEmpty()
                && animations.isEmpty()) {
            return;
        }
        current.actors = List.copyOf(actors);
        current.props = List.copyOf(props);
        current.onScreen.clear();
        actors.forEach(actor -> current.onScreen.add(actor.id()));
        props.forEach(prop -> current.onScreen.add(prop.id()));
        int tick = current.tick;
        offer(current, () -> new ReplayEvent.Frame(tick, actors, props,
                sightings.stream().map(Sighting::appearance).toList(), animations));
    }

    /**
     * Mobs, projectiles and primed TNT in the captured chunks. Entities may use half of a recording's object budget;
     * once that is spent, the rest of the round records participants and terrain only.
     */
    private void captureProps(Session current, List<Player> players, List<ReplayEvent.Prop> props, List<Sighting> sightings) {
        if (current.propSamples >= limits.objectBudget() / 2) {
            return;
        }
        for (var world : players.stream().map(Player::getWorld).distinct().toList()) {
            for (var entity : world.getEntities()) {
                if (props.size() >= limits.maxEntities()) {
                    break;
                }
                if (!prop(entity) || current.unrecordable.contains(entity.getUniqueId())) {
                    continue;
                }
                var location = entity.getLocation();
                if (!current.captured.contains(new ChunkKey(world.getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4))) {
                    continue;
                }
                if (!current.seen.contains(entity.getUniqueId())) {
                    var snapshot = snapshot(entity);
                    if (snapshot == null) {
                        current.unrecordable.add(entity.getUniqueId());
                        continue;
                    }
                    current.seen.add(entity.getUniqueId());
                    sightings.add(new Sighting(entity.getUniqueId(), snapshot));
                }
                props.add(new ReplayEvent.Prop(entity.getUniqueId(), world.getUID(), location.getX(), location.getY(),
                        location.getZ(), location.getYaw(), location.getPitch()));
            }
        }
        current.propSamples += props.size();
        if (current.propSamples >= limits.objectBudget() / 2) {
            plugin.getLogger().info("Replay entity budget spent; the rest of the round records players and terrain only");
        }
    }

    /** Paper only serialises persistent entities, and games keep their mobs out of the world save. */
    private static EntitySnapshot snapshot(Entity entity) {
        boolean persistent = entity.isPersistent();
        entity.setPersistent(true);
        try {
            return entity.createSnapshot();
        } finally {
            entity.setPersistent(persistent);
        }
    }

    /**
     * A fishing hook removes itself without its angler, so playback could not hold one still; a vanished player's
     * projectile would give them away.
     */
    private static boolean prop(Entity entity) {
        if (!entity.isValid() || entity.isInsideVehicle()) {
            return false;
        }
        return switch (entity) {
            case Mob ignored -> true;
            case TNTPrimed ignored -> true;
            case FishHook ignored -> false;
            case Projectile projectile -> !(projectile.getShooter() instanceof Player shooter)
                    || !BlockcraftApi.get().isVanished(shooter);
            default -> false;
        };
    }

    /** Only someone the last sample showed can move on screen, which also bounds a sample's animations. */
    private void animate(Entity subject, ReplayEvent.Motion motion) {
        var current = session;
        if (current != null && current.onScreen.contains(subject.getUniqueId())) {
            current.animations.add(new ReplayEvent.Animation(subject.getUniqueId(), motion));
        }
    }

    private boolean offer(Session current, Supplier<ReplayEvent> event) {
        if (current.events >= limits.maxEvents()) { finish("EVENT_LIMIT"); return false; }
        if (!current.writer.offer(event)) { abort("Replay discarded: writer queue full or failed", null); return false; }
        current.events++;
        return true;
    }

    private void finish(String reason) {
        var current = session;
        if (current == null) { return; }
        if (reason.equals("MATCH_END")) {
            captureBlocks(current);
            if (session != current) { return; }
        }
        session = null;
        current.task.cancel();
        current.writer.finish(current.tick, reason).whenComplete((recording, failure) -> {
            if (failure != null) { plugin.getLogger().log(Level.WARNING, "Replay could not be saved", failure); }
            else { current.publication.completed(recording); }
        });
    }

    private void abort(String message, Throwable failure) {
        var current = session;
        session = null;
        if (current != null) { current.task.cancel(); current.writer.abort(); }
        if (failure == null) { plugin.getLogger().warning(message); }
        else { plugin.getLogger().log(Level.WARNING, message, failure); }
    }

    private void changed(Block block) {
        var current = session;
        if (current == null) { return; }
        var key = new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (!current.captured.contains(key.chunk())) { return; }
        if (!current.dirty.contains(key) && current.dirty.size() >= limits.maxBlockChangesPerTick()) {
            finish("BLOCK_CHANGE_LIMIT");
            return;
        }
        current.dirty.add(key);
    }

    private void changedWithNeighbors(Block block) {
        changed(block);
        for (var face : NEIGHBORS) {
            int y = block.getY() + face.getModY();
            if (y >= block.getWorld().getMinHeight() && y < block.getWorld().getMaxHeight()) {
                changed(block.getRelative(face));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { changedWithNeighbors(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) { event.getReplacedBlockStates().forEach(state -> changed(state.getBlock())); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { changedWithNeighbors(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) { changed(event.getBlock()); changed(event.getToBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructure(StructureGrowEvent event) { event.getBlocks().forEach(state -> changed(state.getBlock())); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) { event.getBlocks().forEach(state -> changed(state.getBlock())); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent event) { event.getBlocks().forEach(state -> changed(state.getBlock())); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBlock(EntityChangeBlockEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().forEach(this::changed);
        animate(event.getEntity(), ReplayEvent.Motion.EXPLOSION);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) { changed(event.getBlock()); event.blockList().forEach(this::changed); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        changed(event.getBlock());
        changed(event.getBlock().getRelative(event.getDirection()));
        event.getBlocks().forEach(block -> { changed(block); changed(block.getRelative(event.getDirection())); });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) {
        changed(event.getBlock());
        changed(event.getBlock().getRelative(event.getDirection()));
        changed(event.getBlock().getRelative(event.getDirection().getOppositeFace()));
        event.getBlocks().forEach(block -> {
            changed(block);
            changed(block.getRelative(event.getDirection()));
            changed(block.getRelative(event.getDirection().getOppositeFace()));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerArmSwingEvent event) {
        animate(event.getPlayer(), event.getHand() == EquipmentSlot.OFF_HAND
                ? ReplayEvent.Motion.SWING_OFF_HAND : ReplayEvent.Motion.SWING_MAIN_HAND);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity && event.getFinalDamage() > 0) {
            animate(event.getEntity(), ReplayEvent.Motion.HURT);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Mob) {
            animate(event.getDamager(), ReplayEvent.Motion.SWING_MAIN_HAND);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) { animate(event.getEntity(), ReplayEvent.Motion.DEATH); }

    @Override
    public void close() {
        finish("SHUTDOWN");
        publications.values().forEach(gates -> gates.forEach(PublicationGate::ended));
        publications.clear();
        HandlerList.unregisterAll(this);
    }

    private static final class Session {
        final Round round;
        final ReplayStore.Writer writer;
        final List<UUID> participants;
        final Set<ChunkKey> known = new HashSet<>();
        final Set<ChunkKey> captured = new HashSet<>();
        final ArrayDeque<ChunkKey> pending = new ArrayDeque<>();
        final Set<BlockKey> dirty = new LinkedHashSet<>();
        final Map<UUID, Equipment> equipment = new HashMap<>();
        final Set<UUID> seen = new HashSet<>();
        final Set<UUID> unrecordable = new HashSet<>();
        final Set<UUID> onScreen = new HashSet<>();
        final Set<ReplayEvent.Animation> animations = new LinkedHashSet<>();
        final PublicationGate<ReplayStore.Saved> publication = new PublicationGate<>();
        List<ReplayEvent.Actor> actors = List.of();
        List<ReplayEvent.Prop> props = List.of();
        long propSamples;
        BukkitTask task;
        int tick;
        int events;

        Session(Round round, ReplayStore.Writer writer, List<UUID> participants) {
            this.round = round;
            this.writer = writer;
            this.participants = List.copyOf(participants);
        }
    }
}
