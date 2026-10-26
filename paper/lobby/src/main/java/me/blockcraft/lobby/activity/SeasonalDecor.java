package me.blockcraft.lobby.activity;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.math.Position;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import me.blockcraft.lobby.config.SeasonTheme;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Snowable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dresses the lobby for the season on the players' screens only: every chunk a player receives gets its blocks
 * swapped and covered as the active {@link SeasonTheme}s say, so the map, and whatever {@code /map save} keeps of it,
 * never changes.
 */
public final class SeasonalDecor implements Listener {

    private record ChunkId(UUID world, long key) { }

    private final NetworkCalendar calendar;
    private final Map<Occasion, SeasonTheme> themes;
    private final Map<ChunkId, Map<Position, BlockData>> decorated = new HashMap<>();
    private Set<Occasion> occasions = Set.of();
    private List<SeasonTheme> active = List.of();

    public SeasonalDecor(NetworkCalendar calendar, Map<Occasion, SeasonTheme> themes) {
        this.calendar = calendar;
        this.themes = themes;
    }

    /** Redresses every chunk players already see when the day brings other occasions. */
    public void refresh() {
        var today = Set.copyOf(themes.keySet().stream().filter(calendar::is).toList());
        if (today.equals(occasions)) {
            return;
        }
        var previous = Map.copyOf(decorated);
        decorated.clear();
        occasions = today;
        active = themes.entrySet().stream().filter(theme -> today.contains(theme.getKey())).map(Map.Entry::getValue).toList();
        for (var player : Bukkit.getOnlinePlayers()) {
            for (var chunk : player.getSentChunks()) {
                var changes = new HashMap<Position, BlockData>();
                var world = chunk.getWorld();
                previous.getOrDefault(id(chunk), Map.of()).keySet().forEach(position ->
                        changes.put(position, world.getBlockData(position.blockX(), position.blockY(), position.blockZ())));
                changes.putAll(decorations(chunk));
                send(player, changes);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunk(PlayerChunkLoadEvent event) {
        if (!active.isEmpty()) {
            send(event.getPlayer(), decorations(event.getChunk()));
        }
    }

    private static void send(Player player, Map<Position, BlockData> changes) {
        if (!changes.isEmpty()) {
            player.sendMultiBlockChange(changes);
        }
    }

    private static ChunkId id(Chunk chunk) {
        return new ChunkId(chunk.getWorld().getUID(), chunk.getChunkKey());
    }

    private Map<Position, BlockData> decorations(Chunk chunk) {
        return decorated.computeIfAbsent(id(chunk), id -> active.isEmpty() ? Map.of() : scan(chunk));
    }

    private Map<Position, BlockData> scan(Chunk chunk) {
        World world = chunk.getWorld();
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(true, false, false);
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        var changes = new HashMap<Position, BlockData>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int top = snapshot.getHighestBlockYAt(x, z);
                for (int y = world.getMinHeight(); y <= top; y++) {
                    var replacement = swap(snapshot.getBlockType(x, y, z), baseX + x, y, baseZ + z);
                    if (replacement.isEmpty()) {
                        continue;
                    }
                    var original = snapshot.getBlockData(x, y, z);
                    var swapped = replacement.orElseThrow().getMaterial().createBlockData();
                    original.copyTo(swapped);
                    swapped = swapped.merge(replacement.orElseThrow());
                    if (!swapped.equals(original)) {
                        changes.put(Position.block(baseX + x, y, baseZ + z), swapped);
                    }
                }
                if (top >= world.getMinHeight() && top + 1 < world.getMaxHeight()) {
                    cover(snapshot, x, top, z, baseX + x, baseZ + z, changes);
                }
            }
        }
        return Map.copyOf(changes);
    }

    private Optional<BlockData> swap(Material material, int x, int y, int z) {
        for (var theme : active) {
            var replacements = theme.blocks().get(material);
            if (replacements != null) {
                return Optional.of(replacements.get(Math.floorMod(hash(x, y, z) >>> 24, replacements.size())));
            }
        }
        return Optional.empty();
    }

    private void cover(ChunkSnapshot snapshot, int x, int top, int z, int worldX, int worldZ, Map<Position, BlockData> changes) {
        var ground = snapshot.getBlockType(x, top, z);
        for (var theme : active) {
            boolean covers = theme.coverOn().isEmpty() ? ground.isOccluding() || Tag.LEAVES.isTagged(ground) : theme.coverOn().contains(ground);
            if (!covers || theme.cover().isEmpty() || (hash(worldX, top, worldZ) & 0xFFFF) / 65536.0 >= theme.coverChance()) {
                continue;
            }
            var layer = theme.cover().get(Math.floorMod(hash(worldX, top + 1, worldZ) >>> 24, theme.cover().size()));
            changes.put(Position.block(worldX, top + 1, worldZ), layer);
            var below = changes.getOrDefault(Position.block(worldX, top, worldZ), snapshot.getBlockData(x, top, z)).clone();
            if (layer.getMaterial() == Material.SNOW && below instanceof Snowable snowable) {
                snowable.setSnowy(true);
                changes.put(Position.block(worldX, top, worldZ), below);
            }
            return;
        }
    }

    /** The same block always draws the same lot, so a decoration stays where it was when its chunk comes back. */
    private static long hash(int x, int y, int z) {
        long seed = x * 3129871L ^ z * 116129781L ^ y;
        seed = seed * seed * 42317861L + seed * 11L;
        return seed >>> 16;
    }
}
