package me.blockcraft.lobby;

import org.bukkit.Chunk;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ChunkTickets {

    private final Plugin plugin;
    private final Map<Chunk, Integer> owners = new HashMap<>();

    public ChunkTickets(Plugin plugin) {
        this.plugin = plugin;
    }

    public Owner owner() {
        return new Owner();
    }

    public final class Owner {
        private final Set<Chunk> held = new HashSet<>();

        public void add(Chunk chunk) {
            if (held.add(chunk) && owners.merge(chunk, 1, Integer::sum) == 1) {
                chunk.addPluginChunkTicket(plugin);
            }
        }

        public void clear() {
            for (var chunk : held) {
                if (owners.get(chunk) == 1) {
                    owners.remove(chunk);
                    chunk.removePluginChunkTicket(plugin);
                } else {
                    owners.computeIfPresent(chunk, (ignored, count) -> count - 1);
                }
            }
            held.clear();
        }
    }
}
