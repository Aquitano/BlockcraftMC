package me.blockcraft.api.world;

import me.blockcraft.api.scenery.AmbientCreatures;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class AmbientCreaturesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void decorationsCannotBeFedAndAreRemovedForMapSavesAndShutdown() throws Exception {
        var file = folder.newFile("creatures.yml").toPath();
        Files.writeString(file, """
                step-ticks: 2
                creatures:
                  bird:
                    type: bird
                    at: {x: 2, y: 52.5, z: 3, yaw: 135}
                    cycle-ticks: 320
                    look-ticks: 80
                    look-degrees: 35
                    hop-ticks: 16
                    hop-height: 0.18
                """);
        var fixture = new Fixture();
        try (var scenery = new AmbientCreatures(fixture.plugin, fixture.world, file)) {
            fixture.frame.run();
            var first = fixture.spawned.getFirst();
            assertEquals(false, first.settings.get("setPersistent"));
            assertEquals(false, first.settings.get("setAI"));
            assertEquals(false, first.settings.get("setCollidable"));
            assertEquals(true, first.settings.get("setSitting"));
            var feed = new PlayerInteractEntityEvent(fixture.player, first.mob);
            scenery.onInteract(feed);
            assertTrue(feed.isCancelled());

            var save = new MapSaveEvent(fixture.world);
            scenery.onSave(save);
            assertFalse(first.valid);
            fixture.frame.run();
            assertEquals(1, fixture.spawned.size());
            assertTrue(save.complete().isEmpty());
            fixture.frame.run();
            assertEquals(2, fixture.spawned.size());
            assertTrue(fixture.spawned.getLast().valid);
        }
        assertTrue(fixture.cancelled);
        assertTrue(fixture.spawned.stream().noneMatch(mob -> mob.valid));
        assertEquals(0, fixture.tickets);
    }

    private static final class Actor {
        final Map<String, Object> settings = new HashMap<>();
        boolean valid = true;
        Location at;
        Mob mob;

        Actor(Class<?> type, Location at) {
            this.at = at;
            mob = (Mob) fake(type, (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "isValid" -> valid;
                case "getLocation" -> this.at.clone();
                case "teleport" -> { this.at = ((Location) args[0]).clone(); yield true; }
                case "remove" -> { valid = false; yield null; }
                case "setPersistent", "setRemoveWhenFarAway", "setAI", "setGravity", "setInvulnerable",
                     "setSilent", "setCollidable", "setCanPickupItems", "setVariant", "setBodyYaw", "setSitting" -> {
                    settings.put(method.getName(), args[0]);
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
        }
    }

    private static final class Fixture {
        Runnable frame;
        boolean cancelled;
        int tickets;
        final List<Actor> spawned = new ArrayList<>();
        final Player player = fake(Player.class, (proxy, method, args) -> null);
        final Chunk chunk = fake(Chunk.class, (proxy, method, args) -> switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "addPluginChunkTicket" -> { tickets++; yield true; }
            case "removePluginChunkTicket" -> { tickets--; yield true; }
            default -> throw new AssertionError(method.getName());
        });
        final World world = fake(World.class, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "getPlayers" -> List.of(player);
            case "getChunkAt" -> chunk;
            case "spawn" -> spawn(args);
            default -> throw new AssertionError(method.getName());
        });
        final BukkitTask task = fake(BukkitTask.class, (proxy, method, args) -> {
            if (!method.getName().equals("cancel")) throw new AssertionError(method.getName());
            cancelled = true;
            return null;
        });
        final BukkitScheduler scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> {
            frame = (Runnable) args[1];
            return task;
        });
        final PluginManager manager = fake(PluginManager.class, (proxy, method, args) -> null);
        final Server server = fake(Server.class, (proxy, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "getPluginManager" -> manager;
            default -> throw new AssertionError(method.getName());
        });
        final Plugin plugin = fake(Plugin.class, (proxy, method, args) -> server);

        @SuppressWarnings("unchecked")
        private Mob spawn(Object[] args) {
            var actor = new Actor((Class<?>) args[1], (Location) args[0]);
            ((Consumer<Mob>) args[2]).accept(actor.mob);
            spawned.add(actor);
            return actor.mob;
        }
    }

    private static <T> T fake(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
