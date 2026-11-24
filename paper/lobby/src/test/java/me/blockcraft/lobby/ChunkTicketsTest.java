package me.blockcraft.lobby;

import org.bukkit.Chunk;
import org.bukkit.plugin.Plugin;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChunkTicketsTest {

    @Test
    public void seasonalCleanupKeepsThePrisonersTicketUntilBothOwnersReleaseIt() {
        var plugins = new HashSet<Plugin>();
        var calls = new int[2];
        var plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError(method.getName());
                });
        var chunk = (Chunk) Proxy.newProxyInstance(Chunk.class.getClassLoader(), new Class<?>[]{Chunk.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "addPluginChunkTicket" -> { calls[0]++; yield plugins.add((Plugin) args[0]); }
                    case "removePluginChunkTicket" -> { calls[1]++; yield plugins.remove((Plugin) args[0]); }
                    default -> throw new AssertionError(method.getName());
                });
        var tickets = new ChunkTickets(plugin);
        var prisoner = tickets.owner();
        var seasonal = tickets.owner();
        prisoner.add(chunk);
        seasonal.add(chunk);
        seasonal.add(chunk);
        assertEquals(1, calls[0]);

        seasonal.clear();
        seasonal.clear();
        assertTrue(plugins.contains(plugin));
        assertEquals(0, calls[1]);

        seasonal.add(chunk);
        prisoner.clear();
        assertTrue(plugins.contains(plugin));
        seasonal.clear();
        assertFalse(plugins.contains(plugin));
        assertEquals(1, calls[1]);
    }
}
