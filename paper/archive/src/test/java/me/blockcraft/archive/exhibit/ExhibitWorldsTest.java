package me.blockcraft.archive.exhibit;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.task.PlayerTasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExhibitWorldsTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final List<Runnable> scheduled = new ArrayList<>();
    private final List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
    private final List<Location> teleports = new ArrayList<>();
    private final List<Component> received = new ArrayList<>();
    private Field serverField;
    private Field apiField;
    private Object previousServer;
    private Object previousApi;
    private Player player;
    private PlayerTasks tasks;
    private ExhibitWorlds worlds;

    @Before
    public void setUp() throws Exception {
        var task = fake(BukkitTask.class, (proxy, method, args) -> null);
        var scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> {
            assertEquals("runTask", method.getName());
            scheduled.add((Runnable) args[1]);
            return task;
        });
        var manager = fake(PluginManager.class, (proxy, method, args) -> null);
        var world = fake(World.class, (proxy, method, args) -> {
            assertEquals("getChunkAtAsync", method.getName());
            var chunk = new CompletableFuture<Chunk>();
            chunks.add(chunk);
            return chunk;
        });
        var server = fake(Server.class, (proxy, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "getPluginManager" -> manager;
            case "getWorld" -> world;
            default -> throw new AssertionError(method.getName());
        });
        var plugin = fake(Plugin.class, (proxy, method, args) -> switch (method.getName()) {
            case "getServer" -> server;
            case "isEnabled" -> true;
            default -> throw new AssertionError(method.getName());
        });
        var id = UUID.randomUUID();
        var view = fake(InventoryView.class, (proxy, method, args) -> null);
        player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getOpenInventory" -> view;
            case "isOnline" -> true;
            case "teleport" -> { teleports.add((Location) args[0]); yield true; }
            case "sendMessage" -> { received.add((Component) args[0]); yield null; }
            case "playSound" -> null;
            default -> throw new AssertionError(method.getName());
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, server);
        var messages = Messages.load(folder.newFolder().toPath(), () -> new ByteArrayInputStream(
                "travel:\n  failed: 'failed'\n".getBytes(StandardCharsets.UTF_8)));
        tasks = new PlayerTasks(plugin, messages);
        var values = Arrays.stream(BlockcraftApi.class.getRecordComponents()).map(component -> switch (component.getName()) {
            case "playerTasks" -> tasks;
            case "settings" -> new PlayerSettings(plugin, null, messages);
            case "resourcePack" -> new ResourcePacks(messages);
            default -> null;
        }).toArray();
        apiField = BlockcraftApi.class.getDeclaredField("instance");
        apiField.setAccessible(true);
        previousApi = apiField.get(null);
        apiField.set(null, BlockcraftApi.class.getDeclaredConstructors()[0].newInstance(values));
        worlds = new ExhibitWorlds(plugin, null, null, messages, Duration.ofMinutes(5));
    }

    @After
    public void restoreGlobals() throws Exception {
        if (serverField != null) {
            serverField.set(null, previousServer);
        }
        if (apiField != null) {
            apiField.set(null, previousApi);
        }
    }

    @Test
    public void navigationDuringChunkLoadingCancelsTheOldTripAndAllowsTheNewOne() {
        var exhibit = new Exhibit("test", "Test", "Test", OptionalInt.empty(), Optional.empty(), "", Material.PAPER,
                Optional.empty(), List.of());
        worlds.travel(player, exhibit, world -> new Location(world, 0, 64, 0), Component.text("old"));
        tasks.invalidate(player);
        worlds.travel(player, exhibit, world -> new Location(world, 32, 64, 0), Component.text("new"));
        assertTrue(teleports.isEmpty());

        chunks.getFirst().complete(null);
        scheduled.removeFirst().run();
        assertTrue(teleports.isEmpty());
        assertTrue(received.isEmpty());

        chunks.getLast().complete(null);
        scheduled.removeFirst().run();
        assertEquals(1, teleports.size());
        assertEquals(32, teleports.getFirst().getBlockX());
        assertEquals(List.of(Component.text("new")), received);
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
