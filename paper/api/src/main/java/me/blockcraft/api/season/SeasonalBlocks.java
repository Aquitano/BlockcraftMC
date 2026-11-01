package me.blockcraft.api.season;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.season.SeasonalJournal.Change;
import me.blockcraft.api.season.SeasonalLayout.Point;
import me.blockcraft.api.world.MapSaveEvent;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SeasonalBlocks implements Listener, AutoCloseable {

    private static final float UNDER_ICE = 0.85f;
    private static final Display.Brightness FROZEN_PAD_LIGHT = new Display.Brightness(12, 15);
    private static final float PAD_VIEW_RANGE = 0.5f;

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
    private Set<Occasion> occasions = Set.of();
    private BukkitTask task;
    private boolean suspended = true;
    private boolean closed;

    private SeasonalBlocks(Plugin plugin, World world, NetworkCalendar calendar, Path settings) {
        this.plugin = plugin;
        this.world = world;
        this.calendar = calendar;
        this.layout = SeasonalLayout.load(settings);
        var shown = EnumSet.of(Occasion.WINTER);
        layout.decorations().forEach(decoration -> shown.addAll(decoration.during()));
        this.shown = Set.copyOf(shown);
        this.journal = new SeasonalJournal(world.getWorldFolder().toPath().resolve("data/blockcraft-seasons"));
        // Before the snowfall starts its sweep, so a journal that cannot be restored leaves nothing running.
        restoreAll();
        this.snowfall = layout.snowfallRadius() > 0 ? Optional.of(new Snowfall(plugin, () -> Optional.of(world.getSpawnLocation()))) : Optional.empty();
    }

    public static SeasonalBlocks start(Plugin plugin, World world, NetworkCalendar calendar, Path settings) {
        var blocks = new SeasonalBlocks(plugin, world, calendar, settings);
        Bukkit.getPluginManager().registerEvents(blocks, plugin);
        blocks.snowfall.ifPresent(snow -> Bukkit.getPluginManager().registerEvents(snow, plugin));
        blocks.suspended = false;
        blocks.refresh();
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
                apply(chunk);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!closed && !suspended && event.getWorld().equals(world)) {
            var applied = changed.get(new SeasonalJournal.Chunk(event.getChunk().getX(), event.getChunk().getZ()));
            if (applied == null) {
                apply(event.getChunk());
            } else {
                applied.forEach(this::freezeIn);
            }
        }
    }

    private void apply(Chunk chunk) {
        var key = new SeasonalJournal.Chunk(chunk.getX(), chunk.getZ());
        if (changed.containsKey(key)) {
            return;
        }
        var changes = new LinkedHashMap<Point, Change>();
        if (occasions.contains(Occasion.WINTER)) {
            snow(chunk, changes);
            freeze(chunk, changes);
        }
        for (var decoration : layout.decorations()) {
            var at = decoration.at();
            if (at.x() >> 4 == chunk.getX() && at.z() >> 4 == chunk.getZ()
                    && decoration.during().stream().anyMatch(occasions::contains)) {
                var block = block(at);
                if (decoration.from().matches(block.getBlockData())) {
                    prepare(changes, at, decoration.to());
                }
            }
        }
        if (changes.isEmpty()) {
            changed.put(key, List.of());
            return;
        }
        var written = List.copyOf(changes.values());
        try {
            journal.write(key, written);
        } catch (IOException failure) {
            plugin.getSLF4JLogger().error("Could not journal seasonal blocks in {}: {}", world.getName(), failure.getMessage());
            return;
        }
        changed.put(key, written);
        for (var change : written) {
            protectedBlocks.put(change.at(), change);
            block(change.at()).setBlockData(Bukkit.createBlockData(change.replacement()), false);
            freezeIn(change);
        }
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

    private void snow(Chunk chunk, Map<Point, Change> changes) {
        var zones = layout.snow().stream().filter(box -> box.intersects(chunk.getX(), chunk.getZ())).toList();
        if (zones.isEmpty()) {
            return;
        }
        var layer = (Snow) Material.SNOW.createBlockData();
        layer.setLayers(layout.snowLayers());
        for (int x = chunk.getX() << 4; x < (chunk.getX() << 4) + 16; x++) {
            for (int z = chunk.getZ() << 4; z < (chunk.getZ() << 4) + 16; z++) {
                int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                if (y < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
                    continue;
                }
                var top = world.getBlockAt(x, y, z);
                boolean plant = top.getType() == Material.SHORT_GRASS || top.getType() == Material.FERN;
                var ground = plant ? top.getRelative(0, -1, 0) : top;
                var at = new Point(x, plant ? y : y + 1, z);
                if (zones.stream().noneMatch(box -> box.contains(at)) || layout.clear(new Point(x, ground.getY(), z))
                        || (!ground.getType().isOccluding() && !Tag.LEAVES.isTagged(ground.getType()))
                        || ground.getState() instanceof TileState
                        || (!plant && !block(at).getType().isAir())) {
                    continue;
                }
                prepare(changes, at, layer);
                if (changes.containsKey(at) && ground.getBlockData() instanceof Snowable snowy) {
                    snowy.setSnowy(true);
                    prepare(changes, new Point(x, ground.getY(), z), snowy);
                }
            }
        }
    }

    private void freeze(Chunk chunk, Map<Point, Change> changes) {
        for (var box : layout.freeze()) {
            if (!box.intersects(chunk.getX(), chunk.getZ())) {
                continue;
            }
            for (int x = Math.max(box.min().x(), chunk.getX() << 4); x <= Math.min(box.max().x(), (chunk.getX() << 4) + 15); x++) {
                for (int z = Math.max(box.min().z(), chunk.getZ() << 4); z <= Math.min(box.max().z(), (chunk.getZ() << 4) + 15); z++) {
                    for (int y = Math.max(box.min().y(), world.getMinHeight()); y <= Math.min(box.max().y(), world.getMaxHeight() - 2); y++) {
                        var block = world.getBlockAt(x, y, z);
                        if (block.getType() != Material.WATER || !(block.getBlockData() instanceof Levelled water) || water.getLevel() != 0) {
                            continue;
                        }
                        var above = block.getRelative(0, 1, 0).getType();
                        boolean pad = above == Material.LILY_PAD;
                        if (above.isAir() || pad) {
                            var at = new Point(x, y, z);
                            prepare(changes, at, Material.ICE.createBlockData());
                            if (pad && changes.containsKey(at)) {
                                prepare(changes, new Point(x, y + 1, z), Material.AIR.createBlockData());
                            }
                        }
                    }
                }
            }
        }
    }

    private void prepare(Map<Point, Change> changes, Point at, BlockData replacement) {
        if (layout.clear(at) || at.y() < world.getMinHeight() || at.y() >= world.getMaxHeight()) {
            return;
        }
        var block = block(at);
        if (block.getState(false) instanceof TileState) {
            return;
        }
        String original = block.getBlockData().getAsString();
        String next = replacement.getAsString();
        if (!original.equals(next)) {
            changes.put(at, new Change(at, original, next));
        }
    }

    private void restoreAll() {
        frozenPads.values().forEach(BlockDisplay::remove);
        frozenPads.clear();
        try {
            var pending = journal.read();
            if (pending.isEmpty()) {
                changed.clear();
                protectedBlocks.clear();
                return;
            }
            for (var entry : pending.entrySet()) {
                world.getChunkAt(entry.getKey().x(), entry.getKey().z());
                for (var change : entry.getValue()) {
                    var block = block(change.at());
                    if (change.restores(block.getBlockData().getAsString())) {
                        block.setBlockData(Bukkit.createBlockData(change.original()), false);
                    }
                }
            }
            world.save(true);
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
        try {
            restoreAll();
        } catch (RuntimeException failure) {
            event.abort(failure);
        }
        event.afterSave(() -> {
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
        HandlerList.unregisterAll(this);
        snowfall.ifPresent(Snowfall::close);
        restoreAll();
    }
}
