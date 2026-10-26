package me.blockcraft.lobby.activity;

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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.ArrayList;
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
 * ends, for a chunk that is not loaded then as soon as it loads again, and for the moment {@code /map save} takes.
 */
public final class Snowfall implements Listener {

    private static final int CELL = 4;
    private static final int CELLS_ACROSS = 16 / CELL;
    private static final int BELOW_SPAWN = 64;
    private static final int ABOVE_SPAWN = 128;

    /** The biome of each 4x4x4 cell of a chunk from {@code minY} up, as an index into the palette. */
    private record Painted(int minY, byte[] cells) { }

    private interface Cell {
        void at(int x, int y, int z, int index);
    }

    private final Supplier<Optional<Location>> center;
    private final List<Biome> palette = new ArrayList<>();
    private final Map<Long, Painted> painted = new HashMap<>();
    private final Set<UUID> snowedOn = new HashSet<>();
    private Location spawn;
    private int radius;

    public Snowfall(Supplier<Optional<Location>> center) {
        this.center = center;
    }

    /** Lets it snow within {@code radius} blocks of the spawn, or stops it with zero. */
    void set(int radius) {
        if (radius > 0 && this.radius == 0) {
            center.get().ifPresent(at -> {
                spawn = at;
                this.radius = radius;
                var world = at.getWorld();
                for (var chunk : world.getLoadedChunks()) {
                    if (covers(chunk) && !painted.containsKey(chunk.getChunkKey())) {
                        paint(chunk);
                        world.refreshChunk(chunk.getX(), chunk.getZ());
                    }
                }
                Bukkit.getOnlinePlayers().forEach(this::snowOn);
            });
        } else if (radius == 0 && this.radius > 0) {
            this.radius = 0;
            var world = spawn.getWorld();
            for (long key : List.copyOf(painted.keySet())) {
                int chunkX = (int) key;
                int chunkZ = (int) (key >> 32);
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    restore(world.getChunkAt(chunkX, chunkZ));
                    world.refreshChunk(chunkX, chunkZ);
                }
            }
            for (var id : snowedOn) {
                var player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.resetPlayerWeather();
                }
            }
            snowedOn.clear();
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        var chunk = event.getChunk();
        boolean known = painted.containsKey(chunk.getChunkKey());
        if (radius > 0 && !known && covers(chunk)) {
            paint(chunk);
        } else if (radius == 0 && known) {
            restore(chunk);
        }
    }

    private boolean covers(Chunk chunk) {
        return spawn != null && spawn.getWorld().equals(chunk.getWorld())
                && Math.abs((chunk.getX() << 4) + 8 - spawn.getBlockX()) <= radius
                && Math.abs((chunk.getZ() << 4) + 8 - spawn.getBlockZ()) <= radius;
    }

    private void paint(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = Math.floorDiv(Math.max(world.getMinHeight(), spawn.getBlockY() - BELOW_SPAWN), CELL) * CELL;
        int layers = (Math.min(world.getMaxHeight() - 1, spawn.getBlockY() + ABOVE_SPAWN) - minY) / CELL + 1;
        var cells = new byte[CELLS_ACROSS * CELLS_ACROSS * layers];
        forEachCell(chunk, minY, layers, (x, y, z, index) -> {
            var biome = world.getBiome(x, y, z);
            int slot = palette.indexOf(biome);
            if (slot < 0) {
                palette.add(biome);
                slot = palette.size() - 1;
            }
            cells[index] = (byte) slot;
            world.setBiome(x, y, z, Biome.SNOWY_PLAINS);
        });
        painted.put(chunk.getChunkKey(), new Painted(minY, cells));
    }

    private void restore(Chunk chunk) {
        var original = painted.remove(chunk.getChunkKey());
        var world = chunk.getWorld();
        int layers = original.cells().length / (CELLS_ACROSS * CELLS_ACROSS);
        forEachCell(chunk, original.minY(), layers, (x, y, z, index) -> world.setBiome(x, y, z, palette.get(original.cells()[index])));
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
        if (radius > 0 && player.getPlayerWeather() == null
                && BlockcraftApi.get().settings().enabled(player, PlayerSetting.EFFECTS)) {
            player.setPlayerWeather(WeatherType.DOWNFALL);
            snowedOn.add(player.getUniqueId());
        }
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
        if (spawn == null) {
            return;
        }
        var world = spawn.getWorld();
        var loaded = painted.keySet().stream().filter(key -> world.isChunkLoaded((int) (long) key, (int) (key >> 32)))
                .map(key -> world.getChunkAt((int) (long) key, (int) (key >> 32))).toList();
        loaded.forEach(this::restore);
        event.afterSave(() -> loaded.stream().filter(chunk -> radius > 0 && chunk.isLoaded()).forEach(this::paint));
    }
}
