package me.blockcraft.api.season;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.settings.PlayerSettingsLoadedEvent;
import me.blockcraft.api.world.MapSaveEvent;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WeatherType;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Real Minecraft snowfall over the lobby: every chunk within the radius of the spawn turns into a snowy biome as it
 * loads, and each player who keeps effects on sees downfall, which a cold biome draws as snow. The world itself
 * stays clear, so nothing piles up. Each chunk keeps its own biomes on the side; they come back when the season
 * ends and around a map save, including chunks that unloaded during winter.
 */
public final class Snowfall implements Listener, AutoCloseable {

    private static final int CELL = 4;
    private static final int CELLS_ACROSS = 16 / CELL;
    private static final int BELOW_SPAWN = 64;
    private static final int ABOVE_SPAWN = 128;
    private static final long SWEEP_TICKS = 100L;

    private record Painted(int minY, Biome[] cells) { }

    private interface Cell {
        void at(int x, int y, int z, int index);
    }

    private final Plugin plugin;
    private final Supplier<Optional<Location>> center;
    private final BukkitTask sweep;
    private final Map<Long, Painted> painted = new HashMap<>();
    private final Set<UUID> snowedOn = new HashSet<>();
    private Location spawn;
    private int radius;
    private boolean suspended;
    private boolean closed;

    public Snowfall(Plugin plugin, Supplier<Optional<Location>> center) {
        this.plugin = plugin;
        this.center = center;
        this.sweep = Bukkit.getScheduler().runTaskTimer(plugin, this::paintLoaded, SWEEP_TICKS, SWEEP_TICKS);
    }

    /** Lets it snow within {@code radius} blocks of the spawn, or stops it with zero. */
    public void set(int radius) {
        if (closed || suspended) {
            return;
        }
        var next = radius > 0 ? center.get() : Optional.<Location>empty();
        int nextRadius = next.isPresent() ? Math.clamp(radius, 0, 512) : 0;
        if (this.radius == nextRadius && (nextRadius == 0 || next.orElseThrow().equals(spawn))) {
            return;
        }
        suspended = true;
        try {
            restoreAll();
            this.radius = nextRadius;
            spawn = next.map(Location::clone).orElse(null);
        } finally {
            suspended = false;
        }
        paintLoaded();
        Bukkit.getOnlinePlayers().forEach(this::snowOn);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!closed && !suspended && spawn != null && event.getWorld().equals(spawn.getWorld())) {
            var chunk = event.getChunk();
            Bukkit.getScheduler().runTask(plugin, () -> paintIfDue(chunk));
        }
    }

    private void paintLoaded() {
        if (closed || suspended || spawn == null || radius == 0) {
            return;
        }
        for (var chunk : spawn.getWorld().getLoadedChunks()) {
            paintIfDue(chunk);
        }
    }

    private void paintIfDue(Chunk chunk) {
        if (!closed && !suspended && radius > 0 && chunk.isLoaded() && !painted.containsKey(chunk.getChunkKey()) && covers(chunk)
                && paint(chunk)) {
            chunk.getWorld().refreshChunk(chunk.getX(), chunk.getZ());
        }
    }

    private boolean covers(Chunk chunk) {
        return spawn != null && spawn.getWorld().equals(chunk.getWorld())
                && Math.abs((chunk.getX() << 4) + 8 - spawn.getBlockX()) <= radius
                && Math.abs((chunk.getZ() << 4) + 8 - spawn.getBlockZ()) <= radius;
    }

    /**
     * False when the world dropped the change, which it does for a chunk it does not count as fully loaded yet, such
     * as one still loading during its load event; the sweep tries it again.
     */
    private boolean paint(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = Math.floorDiv(Math.max(world.getMinHeight(), spawn.getBlockY() - BELOW_SPAWN), CELL) * CELL;
        int layers = (Math.min(world.getMaxHeight() - 1, spawn.getBlockY() + ABOVE_SPAWN) - minY) / CELL + 1;
        var cells = new Biome[CELLS_ACROSS * CELLS_ACROSS * layers];
        forEachCell(chunk, minY, layers, (x, y, z, index) -> {
            cells[index] = world.getBiome(x, y, z);
            world.setBiome(x, y, z, Biome.SNOWY_PLAINS);
        });
        if (!took(chunk, minY, cells)) {
            return false;
        }
        painted.put(chunk.getChunkKey(), new Painted(minY, cells));
        return true;
    }

    /** Whether the first cell that was not snowy before reads snowy now, in the order {@link #forEachCell} walks. */
    private static boolean took(Chunk chunk, int minY, Biome[] cells) {
        int layers = cells.length / (CELLS_ACROSS * CELLS_ACROSS);
        for (int index = 0; index < cells.length; index++) {
            if (!cells[index].equals(Biome.SNOWY_PLAINS)) {
                int column = index / layers;
                return chunk.getWorld().getBiome((chunk.getX() << 4) + column / CELLS_ACROSS * CELL, minY + index % layers * CELL,
                        (chunk.getZ() << 4) + column % CELLS_ACROSS * CELL).equals(Biome.SNOWY_PLAINS);
            }
        }
        return true;
    }

    private void restore(Chunk chunk) {
        var original = painted.get(chunk.getChunkKey());
        var world = chunk.getWorld();
        int layers = original.cells().length / (CELLS_ACROSS * CELLS_ACROSS);
        forEachCell(chunk, original.minY(), layers, (x, y, z, index) -> {
            if (world.getBiome(x, y, z).equals(Biome.SNOWY_PLAINS)) {
                world.setBiome(x, y, z, original.cells()[index]);
            }
        });
        painted.remove(chunk.getChunkKey());
    }

    private void restoreAll() {
        if (spawn == null) {
            return;
        }
        var world = spawn.getWorld();
        for (long key : List.copyOf(painted.keySet())) {
            var chunk = world.getChunkAt((int) key, (int) (key >> 32));
            restore(chunk);
            world.refreshChunk(chunk.getX(), chunk.getZ());
        }
    }

    private static void forEachCell(Chunk chunk, int minY, int layers, Cell cell) {
        int index = 0;
        for (int i = 0; i < CELLS_ACROSS; i++) {
            for (int k = 0; k < CELLS_ACROSS; k++) {
                for (int j = 0; j < layers; j++, index++) {
                    cell.at((chunk.getX() << 4) + i * CELL, minY + j * CELL, (chunk.getZ() << 4) + k * CELL, index);
                }
            }
        }
    }

    /** Players who chose their own weather keep it. */
    private void snowOn(Player player) {
        boolean applies = !closed && radius > 0 && spawn != null && spawn.getWorld().equals(player.getWorld())
                && BlockcraftApi.get().settings().enabled(player, PlayerSetting.EFFECTS);
        if (!applies) {
            if (snowedOn.remove(player.getUniqueId()) && player.getPlayerWeather() == WeatherType.DOWNFALL) {
                player.resetPlayerWeather();
            }
        } else if (player.getPlayerWeather() == null) {
            player.setPlayerWeather(WeatherType.DOWNFALL);
            snowedOn.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        snowOn(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        snowOn(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSettingsLoaded(PlayerSettingsLoadedEvent event) {
        snowOn(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        snowedOn.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMapSave(MapSaveEvent event) {
        if (spawn == null || closed || !event.world().equals(spawn.getWorld())) {
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
            paintLoaded();
        });
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        sweep.cancel();
        set(0);
        closed = true;
        HandlerList.unregisterAll(this);
    }
}
