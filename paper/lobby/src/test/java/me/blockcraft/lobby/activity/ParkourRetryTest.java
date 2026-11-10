package me.blockcraft.lobby.activity;

import com.zaxxer.hikari.HikariDataSource;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.ActivitiesConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class ParkourRetryTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    @SuppressWarnings("unchecked")
    public void retriesPreserveTheRunAndOnlyGuardTheirOwnTeleport() throws Exception {
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        var apiField = BlockcraftApi.class.getDeclaredField("instance");
        apiField.setAccessible(true);
        var previousServer = serverField.get(null);
        var previousApi = apiField.get(null);
        try {
            var fixture = new Fixture();
            serverField.set(null, fixture.server);
            var messages = Messages.load(folder.newFolder().toPath(), () -> new ByteArrayInputStream(new byte[0]));
            var constructor = Database.class.getDeclaredConstructor(HikariDataSource.class);
            constructor.setAccessible(true);
            var db = constructor.newInstance(fixture.source);
            var values = Arrays.stream(BlockcraftApi.class.getRecordComponents()).map(component -> switch (component.getName()) {
                case "database" -> db;
                case "settings" -> new PlayerSettings(fixture.plugin, null, messages);
                case "resourcePack" -> new ResourcePacks(messages);
                default -> null;
            }).toArray();
            apiField.set(null, BlockcraftApi.class.getDeclaredConstructors()[0].newInstance(values));
            var locations = Locations.load(folder.newFolder().toPath().resolve("locations.yml"));
            var start = new Location(fixture.world, 1, 70, 1);
            var checkpoint = new Location(fixture.world, 20, 75, 20);
            locations.set("parkour-start", start);
            locations.set("checkpoint-1", checkpoint);
            fixture.parkour = new Parkour(fixture.plugin, locations, ActivitiesConfig.fromYaml(Map.of()), messages);
            var runsField = Parkour.class.getDeclaredField("runs");
            runsField.setAccessible(true);
            var runs = (Map<UUID, ParkourRun>) runsField.get(fixture.parkour);
            var run = new ParkourRun(1_000_000_000L);
            runs.put(fixture.id, run);

            fixture.parkour.retry(fixture.player);
            assertEquals(start, fixture.destination);
            run.reach(1);
            fixture.parkour.retry(fixture.player);
            assertEquals(checkpoint, fixture.destination);
            assertSame(run, runs.get(fixture.id));
            assertEquals(72_000, run.millis(73_000_000_000L));
            assertEquals(1, run.fall());
            assertEquals(2, fixture.databaseWrites);

            fixture.teleportAllowed = false;
            fixture.parkour.retry(fixture.player);
            assertSame(run, runs.get(fixture.id));
            fixture.parkour.onTeleport(new PlayerTeleportEvent(fixture.player, start, checkpoint));
            assertFalse(fixture.parkour.running(fixture.player));
        } finally {
            serverField.set(null, previousServer);
            apiField.set(null, previousApi);
        }
    }

    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        final UUID worldId = UUID.randomUUID();
        Parkour parkour;
        Location destination;
        boolean teleportAllowed = true;
        int databaseWrites;
        final World world = fake(World.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUID" -> worldId;
            case "getName" -> "parkour";
            case "key", "getKey" -> NamespacedKey.minecraft("parkour");
            case "equals" -> proxy == args[0];
            case "hashCode" -> worldId.hashCode();
            default -> throw new AssertionError(method.getName());
        });
        final Player player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "sendMessage", "playSound" -> null;
            case "teleport" -> {
                destination = (Location) args[0];
                parkour.onTeleport(new PlayerTeleportEvent((Player) proxy, new Location(world, 0, 70, 0), destination));
                yield teleportAllowed;
            }
            default -> throw new AssertionError(method.getName());
        });
        final BukkitScheduler scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> null);
        final Server server = fake(Server.class, (proxy, method, args) -> switch (method.getName()) {
            case "getWorld" -> world;
            case "getScheduler" -> scheduler;
            default -> throw new AssertionError(method.getName());
        });
        final Plugin plugin = fake(Plugin.class, (proxy, method, args) -> server);
        final PreparedStatement statement = fake(PreparedStatement.class, (proxy, method, args) -> switch (method.getName()) {
            case "executeUpdate" -> { databaseWrites++; yield 0; }
            case "close" -> null;
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
    }

    private static <T> T fake(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
