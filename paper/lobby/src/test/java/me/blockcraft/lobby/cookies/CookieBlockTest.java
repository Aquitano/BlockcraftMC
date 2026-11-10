package me.blockcraft.lobby.cookies;

import com.zaxxer.hikari.HikariDataSource;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.task.PlayerTasks;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.ChunkTickets;
import me.blockcraft.lobby.SpotEntities;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class CookieBlockTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void firstPunchLoadsProgressOnceWithoutOpeningAnyInventory() throws Exception {
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        var apiField = BlockcraftApi.class.getDeclaredField("instance");
        apiField.setAccessible(true);
        var previousServer = serverField.get(null);
        var previousApi = apiField.get(null);
        var fixture = new Fixture();
        try {
            serverField.set(null, fixture.server);
            var messages = Messages.load(folder.newFolder().toPath(), () -> new ByteArrayInputStream(new byte[0]));
            var constructor = Database.class.getDeclaredConstructor(HikariDataSource.class);
            constructor.setAccessible(true);
            var db = constructor.newInstance(fixture.source);
            var tasks = new PlayerTasks(fixture.plugin, messages);
            var values = Arrays.stream(BlockcraftApi.class.getRecordComponents()).map(component -> switch (component.getName()) {
                case "playerTasks" -> tasks;
                case "settings" -> new PlayerSettings(fixture.plugin, null, messages);
                case "resourcePack" -> new ResourcePacks(messages);
                default -> null;
            }).toArray();
            apiField.set(null, BlockcraftApi.class.getDeclaredConstructors()[0].newInstance(values));
            var rules = new CookieProgress.Rules(1000, Long.MAX_VALUE, 600, 5, List.of());
            try (var cookies = new CookieClicker(fixture.plugin, db, messages, rules);
                 var block = new CookieBlock(Locations.load(folder.newFolder().toPath().resolve("activities.yml")),
                         cookies, messages, new SpotEntities(new ChunkTickets(fixture.plugin)))) {
                var counterField = CookieBlock.class.getDeclaredField("counter");
                counterField.setAccessible(true);
                counterField.set(block, fixture.counter);

                block.onBlockClick(fixture.punch(EquipmentSlot.OFF_HAND));
                assertTrue(fixture.pending.isEmpty());
                var punch = fixture.punch(EquipmentSlot.HAND);
                block.onBlockClick(punch);
                block.onBlockClick(fixture.punch(EquipmentSlot.HAND));
                assertEquals(Event.Result.DENY, punch.useItemInHand());
                assertEquals(1, fixture.pending.size());

                fixture.pending.remove().run();
                fixture.pending.remove().run();
                assertEquals(new CookieProgress.Snapshot(41, 0), cookies.progress(fixture.player).snapshot());
                assertEquals(1, fixture.statusUpdates);
            }
        } finally {
            serverField.set(null, previousServer);
            apiField.set(null, previousApi);
        }
    }

    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        int statusUpdates;
        final Block counter = fake(Block.class, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            default -> throw new AssertionError(method.getName());
        });
        final Player player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "isOnline" -> true;
            case "playSound" -> null;
            case "sendActionBar" -> { statusUpdates++; yield null; }
            default -> throw new AssertionError(method.getName());
        });
        final BukkitTask task = fake(BukkitTask.class, (proxy, method, args) -> null);
        final BukkitScheduler scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> switch (method.getName()) {
            case "runTask", "runTaskAsynchronously" -> { pending.add((Runnable) args[1]); yield task; }
            case "runTaskLater" -> task;
            default -> throw new AssertionError(method.getName());
        });
        final PluginManager manager = fake(PluginManager.class, (proxy, method, args) -> null);
        final Server server = fake(Server.class, (proxy, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "getPluginManager" -> manager;
            default -> throw new AssertionError(method.getName());
        });
        final Plugin plugin = fake(Plugin.class, (proxy, method, args) -> switch (method.getName()) {
            case "getServer" -> server;
            case "isEnabled" -> true;
            default -> throw new AssertionError(method.getName());
        });
        final ResultSet rows = fake(ResultSet.class, (proxy, method, args) -> switch (method.getName()) {
            case "next" -> true;
            case "getLong" -> (int) args[0] == 1 ? 40L : 0L;
            case "getInt" -> 0;
            case "close" -> null;
            default -> throw new AssertionError(method.getName());
        });
        final PreparedStatement statement = fake(PreparedStatement.class, (proxy, method, args) -> switch (method.getName()) {
            case "executeUpdate" -> 1;
            case "executeQuery" -> rows;
            case "setObject", "close" -> null;
            default -> throw new AssertionError(method.getName());
        });
        final Connection connection = fake(Connection.class, (proxy, method, args) -> switch (method.getName()) {
            case "prepareStatement" -> statement;
            case "close" -> null;
            default -> throw new AssertionError(method.getName());
        });
        final HikariDataSource source = new HikariDataSource() {
            @Override public Connection getConnection() { return connection; }
        };

        PlayerInteractEvent punch(EquipmentSlot hand) {
            return new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, null, counter, BlockFace.UP, hand);
        }
    }

    private static <T> T fake(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
