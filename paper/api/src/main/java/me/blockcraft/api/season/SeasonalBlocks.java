package me.blockcraft.api.season;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.season.SeasonalJournal.Change;
import me.blockcraft.api.season.SeasonalLayout.Point;
import me.blockcraft.api.world.MapSaveEvent;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.Snowable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class SeasonalBlocks implements Listener, AutoCloseable {

    private static final float UNDER_ICE = 0.85f;
    private static final Display.Brightness FROZEN_PAD_LIGHT = new Display.Brightness(12, 15);
    private static final float PAD_VIEW_RANGE = 0.5f;
    private static final double DRIFT_SPAN = 7;

    private static final int NO_SURFACE = Integer.MIN_VALUE;

    private record Candidate(Change change, Point neighbour, String expectedNeighbour, int surfaceY) { }

    private record Preparation(SeasonalJournal.Chunk key, CompletableFuture<List<Candidate>> result) { }

    private final Plugin plugin;
    private final World world;
    private final NetworkCalendar calendar;
    private final SeasonalLayout layout;
    private final SeasonalJournal journal;
    private final Optional<Snowfall> snowfall;
    private final Map<SeasonalJournal.Chunk, List<Change>> changed = new HashMap<>();
    private final Map<Point, Change> protectedBlocks = new HashMap<>();
    private final Map<Point, BlockDisplay> frozenPads = new HashMap<>();
    private final Set<Occasion> shown;
    private final int minHeight;
    private final int maxHeight;
    private final Set<SeasonalJournal.Chunk> queued = new LinkedHashSet<>();
    private Preparation preparing;
    private BukkitTask workTask;
    private Set<Occasion> occasions = Set.of();
    private BukkitTask task;
    private boolean suspended = true;
    private boolean closed;

    private SeasonalBlocks(Plugin plugin, World world, NetworkCalendar calendar, Path settings) {
        this.plugin = plugin;
        this.world = world;
        minHeight = world.getMinHeight();
        maxHeight = world.getMaxHeight();
        this.calendar = calendar;
        this.layout = SeasonalLayout.load(settings);
        var shown = EnumSet.of(Occasion.WINTER);
        layout.decorations().forEach(decoration -> shown.addAll(decoration.during()));
        this.shown = Set.copyOf(shown);
        this.journal = new SeasonalJournal(world.getWorldFolder().toPath().resolve("data/blockcraft-seasons"));
        // Before the snowfall starts its sweep, so a journal that cannot be restored leaves nothing running.
        restoreAll(true);
        this.snowfall = layout.snowfallRadius() > 0 ? Optional.of(new Snowfall(plugin, () -> Optional.of(world.getSpawnLocation()))) : Optional.empty();
    }

    public static SeasonalBlocks start(Plugin plugin, World world, NetworkCalendar calendar, Path settings) {
        var blocks = new SeasonalBlocks(plugin, world, calendar, settings);
        Bukkit.getPluginManager().registerEvents(blocks, plugin);
        blocks.snowfall.ifPresent(snow -> Bukkit.getPluginManager().registerEvents(snow, plugin));
        blocks.suspended = false;
        blocks.refresh();
        blocks.workTask = Bukkit.getScheduler().runTaskTimer(plugin, blocks::work, 1, 1);
        blocks.task = Bukkit.getScheduler().runTaskTimer(plugin, blocks::refresh, 100L, 100L);
        return blocks;
    }

    private void refresh() {
        if (closed || suspended) {
            return;
        }
        var today = EnumSet.noneOf(Occasion.class);
        calendar.occasions().stream().filter(shown::contains).forEach(today::add);
        if (today.equals(occasions)) {
            return;
        }
        suspended = true;
        try {
            restoreAll();
        } catch (RuntimeException failure) {
            occasions = Set.copyOf(today);
            plugin.getSLF4JLogger().error("Could not take back the seasonal blocks in {}; trying again at the next occasion", world.getName(), failure);
            return;
        } finally {
            suspended = false;
        }
        occasions = Set.copyOf(today);
        snowfall.ifPresent(snow -> snow.set(occasions.contains(Occasion.WINTER) ? layout.snowfallRadius() : 0));
        applyLoaded();
    }

    private void applyLoaded() {
        if (!closed && !suspended) {
            for (var chunk : world.getLoadedChunks()) {
                enqueue(chunk);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!closed && !suspended && event.getWorld().equals(world)) {
            var applied = changed.get(new SeasonalJournal.Chunk(event.getChunk().getX(), event.getChunk().getZ()));
            if (applied == null) {
                enqueue(event.getChunk());
            } else {
                applied.forEach(this::freezeIn);
            }
        }
    }

    private void enqueue(Chunk chunk) {
        var key = new SeasonalJournal.Chunk(chunk.getX(), chunk.getZ());
        if (changed.containsKey(key) || preparing != null && preparing.key().equals(key)) return;
        boolean winter = occasions.contains(Occasion.WINTER);
        if (winter && (layout.snow().stream().anyMatch(box -> box.intersects(key.x(), key.z()))
                || layout.freeze().stream().anyMatch(box -> box.intersects(key.x(), key.z())))
                || layout.decorations().stream().anyMatch(decoration -> inChunk(decoration.at(), key)
                        && decoration.during().stream().anyMatch(occasions::contains))) {
            queued.add(key);
        }
    }

    private static boolean inChunk(Point at, SeasonalJournal.Chunk key) {
        return at.x() >> 4 == key.x() && at.z() >> 4 == key.z();
    }

    private void work() {
        if (closed || suspended) return;
        if (preparing != null) {
            if (!preparing.result().isDone()) return;
            var finished = preparing;
            preparing = null;
            try {
                apply(finished.key(), finished.result().getNow(List.of()));
            } catch (CompletionException failure) {
                plugin.getSLF4JLogger().error("Could not prepare seasonal blocks in {}", world.getName(), failure.getCause());
            }
        }
        var keys = queued.iterator();
        if (!keys.hasNext()) return;
        var key = keys.next();
        keys.remove();
        if (!world.isChunkLoaded(key.x(), key.z())) return;
        var snapshot = world.getChunkAt(key.x(), key.z()).getChunkSnapshot(false, false, false, false);
        var active = occasions;
        var surface = active.contains(Occasion.WINTER) && layout.snow().stream().anyMatch(box -> box.intersects(key.x(), key.z()))
                ? surface(key) : new int[0];
        long revision = journal.revision();
        var result = CompletableFuture.supplyAsync(() -> prepare(snapshot, key, active, surface, revision),
                task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
        preparing = new Preparation(key, result);
    }

    // The snapshot's heightmap is MOTION_BLOCKING, which misses the plants the snow layer replaces.
    private int[] surface(SeasonalJournal.Chunk key) {
        var heights = new int[16 * 16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                heights[x * 16 + z] = world.getHighestBlockYAt((key.x() << 4) + x, (key.z() << 4) + z, HeightMap.WORLD_SURFACE);
            }
        }
        return heights;
    }

    private List<Candidate> prepare(ChunkSnapshot snapshot, SeasonalJournal.Chunk key, Set<Occasion> active, int[] surface, long revision) {
        var changes = new LinkedHashMap<Point, Candidate>();
        if (active.contains(Occasion.WINTER)) {
            if (surface.length > 0) snow(snapshot, surface, changes);
            freeze(snapshot, changes);
        }
        for (var decoration : layout.decorations()) {
            var at = decoration.at();
            if (at.y() >= minHeight && at.y() < maxHeight && inChunk(at, key)
                    && decoration.during().stream().anyMatch(active::contains)
                    && decoration.from().matches(data(snapshot, at))) {
                prepare(snapshot, changes, at, decoration.to(), at, NO_SURFACE);
            }
        }
        var written = List.copyOf(changes.values());
        if (!written.isEmpty()) {
            try {
                journal.write(key, written.stream().map(Candidate::change).toList(), revision);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        return written;
    }

    private void apply(SeasonalJournal.Chunk key, List<Candidate> candidates) {
        if (!world.isChunkLoaded(key.x(), key.z())) return;
        var valid = candidates.stream().filter(this::matches).map(Candidate::change).toList();
        changed.put(key, valid);
        for (var change : valid) {
            protectedBlocks.put(change.at(), change);
            block(change.at()).setBlockData(Bukkit.createBlockData(change.replacement()), false);
            freezeIn(change);
        }
    }

    private boolean matches(Candidate candidate) {
        var at = candidate.change().at();
        return matches(at, candidate.change().original())
                && (at.equals(candidate.neighbour()) || matches(candidate.neighbour(), candidate.expectedNeighbour()))
                && (candidate.surfaceY() == NO_SURFACE
                    || world.getHighestBlockYAt(at.x(), at.z(), HeightMap.WORLD_SURFACE) == candidate.surfaceY());
    }

    private boolean matches(Point point, String expected) {
        var current = block(point);
        return current.getBlockData().getAsString().equals(expected) && !(current.getState(false) instanceof TileState);
    }

    private static BlockData data(ChunkSnapshot snapshot, Point at) {
        return snapshot.getBlockData(at.x() & 15, at.y(), at.z() & 15);
    }

    /**
     * A lily pad the ice took in a {@code frozen-pads} area stays visible inside the ice, with its own light, because
     * inside the ice it would turn black at night.
     */
    private void freezeIn(Change change) {
        var pad = change.at();
        if (!change.original().equals("minecraft:lily_pad") || layout.frozenPads().stream().noneMatch(box -> box.contains(pad))) {
            return;
        }
        var shown = frozenPads.get(pad);
        if (shown != null && shown.isValid()) {
            return;
        }
        var turn = new Quaternionf().rotationY((float) (Math.PI / 2 * Math.floorMod(pad.hashCode(), 4)));
        var shift = new Vector3f(0.5f, 0, 0.5f).sub(turn.transform(new Vector3f(0.5f, 0, 0.5f))).add(0, UNDER_ICE, 0);
        frozenPads.put(pad, world.spawn(new Location(world, pad.x(), pad.y() - 1, pad.z()), BlockDisplay.class, display -> {
            display.setBlock(Material.LILY_PAD.createBlockData());
            display.setTransformation(new Transformation(shift, turn, new Vector3f(1), new Quaternionf()));
            display.setBrightness(FROZEN_PAD_LIGHT);
            display.setDisplayWidth(1);
            display.setDisplayHeight(1);
            display.setViewRange(PAD_VIEW_RANGE);
            display.setPersistent(false);
        }));
    }

    private void snow(ChunkSnapshot chunk, int[] surface, Map<Point, Candidate> changes) {
        var zones = layout.snow().stream().filter(box -> box.intersects(chunk.getX(), chunk.getZ())).toList();
        if (zones.isEmpty()) return;
        for (int x = chunk.getX() << 4; x < (chunk.getX() << 4) + 16; x++) {
            for (int z = chunk.getZ() << 4; z < (chunk.getZ() << 4) + 16; z++) {
                int y = surface[(x & 15) * 16 + (z & 15)];
                if (y < minHeight || y + 1 >= maxHeight) continue;
                var top = chunk.getBlockType(x & 15, y, z & 15);
                boolean plant = top == Material.SHORT_GRASS || top == Material.FERN;
                int groundY = plant ? y - 1 : y;
                if (groundY < minHeight) continue;
                var ground = new Point(x, groundY, z);
                var groundType = chunk.getBlockType(x & 15, groundY, z & 15);
                var at = new Point(x, plant ? y : y + 1, z);
                if (zones.stream().noneMatch(box -> box.contains(at)) || layout.clear(ground)
                        || !groundType.isOccluding() && !Tag.LEAVES.isTagged(groundType)
                        || !plant && !chunk.getBlockType(x & 15, y + 1, z & 15).isAir()) {
                    continue;
                }
                prepare(chunk, changes, at, snowLayer(x, z), ground, y);
                if (changes.containsKey(at) && data(chunk, ground) instanceof Snowable snowy) {
                    snowy.setSnowy(true);
                    prepare(chunk, changes, ground, snowy, at, y);
                }
            }
        }
    }

    /** Drifts: the depth wanders between one layer and {@code snow-layers} across the ground, the same at every load. */
    private Snow snowLayer(int x, int z) {
        double drift = (SimplexNoiseGenerator.getNoise(x / DRIFT_SPAN, z / DRIFT_SPAN) + 1) / 2;
        var layer = (Snow) Material.SNOW.createBlockData();
        layer.setLayers(Math.min(layout.snowLayers(), 1 + (int) (drift * layout.snowLayers())));
        return layer;
    }

    private void freeze(ChunkSnapshot chunk, Map<Point, Candidate> changes) {
        for (var box : layout.freeze()) {
            if (!box.intersects(chunk.getX(), chunk.getZ())) continue;
            for (int x = Math.max(box.min().x(), chunk.getX() << 4); x <= Math.min(box.max().x(), (chunk.getX() << 4) + 15); x++) {
                for (int z = Math.max(box.min().z(), chunk.getZ() << 4); z <= Math.min(box.max().z(), (chunk.getZ() << 4) + 15); z++) {
                    for (int y = Math.max(box.min().y(), minHeight); y <= Math.min(box.max().y(), maxHeight - 2); y++) {
                        if (chunk.getBlockType(x & 15, y, z & 15) != Material.WATER) continue;
                        var at = new Point(x, y, z);
                        if (!(data(chunk, at) instanceof Levelled water) || water.getLevel() != 0) continue;
                        var above = chunk.getBlockType(x & 15, y + 1, z & 15);
                        var neighbour = new Point(x, y + 1, z);
                        boolean pad = above == Material.LILY_PAD;
                        if (above.isAir() || pad) {
                            prepare(chunk, changes, at, Material.ICE.createBlockData(), neighbour, NO_SURFACE);
                            if (pad && changes.containsKey(at)) {
                                prepare(chunk, changes, neighbour, Material.AIR.createBlockData(), at, NO_SURFACE);
                            }
                        }
                    }
                }
            }
        }
    }

    private void prepare(ChunkSnapshot snapshot, Map<Point, Candidate> changes, Point at, BlockData replacement, Point neighbour, int surfaceY) {
        if (layout.clear(at)) return;
        String original = data(snapshot, at).getAsString();
        String next = replacement.getAsString();
        if (!original.equals(next)) {
            changes.put(at, new Candidate(new Change(at, original, next), neighbour,
                    at.equals(neighbour) ? original : data(snapshot, neighbour).getAsString(), surfaceY));
        }
    }

    private void restoreAll() {
        restoreAll(false);
    }

    private void restoreAll(boolean recovering) {
        journal.invalidate();
        queued.clear();
        preparing = null;
        frozenPads.values().forEach(BlockDisplay::remove);
        frozenPads.clear();
        try {
            var pending = journal.read();
            var applied = recovering ? pending : changed;
            for (var entry : applied.entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                world.getChunkAt(entry.getKey().x(), entry.getKey().z());
                for (var change : entry.getValue()) {
                    var block = block(change.at());
                    if (change.restores(block.getBlockData().getAsString())) {
                        block.setBlockData(Bukkit.createBlockData(change.original()), false);
                    }
                }
            }
            if (applied.values().stream().anyMatch(changes -> !changes.isEmpty())) world.save(true);
            for (var chunk : pending.keySet()) {
                journal.remove(chunk);
            }
            changed.clear();
            protectedBlocks.clear();
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not restore seasonal blocks in " + world.getName(), failure);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMapSave(MapSaveEvent event) {
        if (closed || !event.world().equals(world)) {
            return;
        }
        suspended = true;
        event.suspend(this::restoreAll, () -> {
            suspended = false;
            applyLoaded();
        });
    }

    private Block block(Point point) {
        return world.getBlockAt(point.x(), point.y(), point.z());
    }

    private boolean temporary(Block block) {
        if (suspended || !block.getWorld().equals(world)) {
            return false;
        }
        var change = protectedBlocks.get(new Point(block.getX(), block.getY(), block.getZ()));
        return change != null && change.restores(block.getBlockData().getAsString());
    }

    @EventHandler(ignoreCancelled = true)
    public void onNaturalCover(BlockFormEvent event) {
        if (!suspended && occasions.contains(Occasion.WINTER) && event.getBlock().getWorld().equals(world)
                && (event.getNewState().getType() == Material.ICE || event.getNewState().getType() == Material.SNOW)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMelt(BlockFadeEvent event) {
        if (temporary(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (temporary(event.getBlock()) || temporary(event.getToBlock()) || frozenWater(event.getBlock()) || frozenWater(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluidLevel(FluidLevelChangeEvent event) {
        if (frozenWater(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * All water in a freeze box stands still while it is frozen: the ice took the sources that fed streams and falls,
     * which would otherwise drain away unrecorded, and come back only after the map was saved without them.
     */
    private boolean frozenWater(Block block) {
        if (suspended || !occasions.contains(Occasion.WINTER) || !block.getWorld().equals(world) || block.getType() != Material.WATER) {
            return false;
        }
        var at = new Point(block.getX(), block.getY(), block.getZ());
        return layout.freeze().stream().anyMatch(box -> box.contains(at));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (temporary(event.getBlock()) && !BlockcraftApi.get().buildMode().isEnabled(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::temporary)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::temporary)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void close() {
        closed = true;
        suspended = true;
        if (task != null) {
            task.cancel();
        }
        if (workTask != null) workTask.cancel();
        HandlerList.unregisterAll(this);
        snowfall.ifPresent(Snowfall::close);
        restoreAll();
    }
}
