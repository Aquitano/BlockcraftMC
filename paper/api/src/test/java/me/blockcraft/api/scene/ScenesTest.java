package me.blockcraft.api.scene;

import me.blockcraft.api.presentation.PresentationScope;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ScenesTest {
    @Test
    public void cameraHasNoGeometryOrShadowAndIsPrivateBeforeItEntersTheWorld() {
        var properties = new HashMap<String, Object>();
        var camera = (ItemDisplay) Proxy.newProxyInstance(ItemDisplay.class.getClassLoader(), new Class<?>[]{ItemDisplay.class},
                (proxy, method, args) -> {
                    properties.put(method.getName(), args[0]);
                    return null;
                });
        var world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("spawn")) throw new AssertionError(method.getName());
                    assertEquals(ItemDisplay.class, args[1]);
                    @SuppressWarnings("unchecked")
                    var configure = (Consumer<ItemDisplay>) args[2];
                    configure.accept(camera);
                    assertTrue(properties.containsKey("setItemStack"));
                    assertNull(properties.get("setItemStack"));
                    assertEquals(false, properties.get("setVisibleByDefault"));
                    assertEquals(false, properties.get("setPersistent"));
                    assertEquals(false, properties.get("setGravity"));
                    assertEquals(0f, properties.get("setShadowRadius"));
                    assertEquals(0f, properties.get("setShadowStrength"));
                    assertEquals(1, properties.get("setTeleportDuration"));
                    return camera;
                });
        assertSame(camera, Scenes.spawnCamera(new Location(world, 1, 2, 3)));
    }

    @Test
    public void completionDetachesTheCameraAndRestoresTheCapturedPlayerState() {
        var state = new PlayerState();
        var player = state.player();
        var saved = Scenes.Saved.capture(player);
        state.mode = GameMode.SPECTATOR;
        state.location = new Location(null, 100, 20, 30);
        state.allowFlight = false;
        state.flying = false;
        state.invulnerable = true;
        state.collidable = true;
        state.fallDistance = 0;
        saved.restore(player, true);
        assertTrue(state.detached);
        assertEquals(saved, Scenes.Saved.capture(player));
    }

    @Test
    public void externalTeleportPreservesItsDestinationAndDoesNotReattachAnOldSpectatorTarget() {
        var state = new PlayerState();
        state.mode = GameMode.SPECTATOR;
        state.target = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isValid")) return true;
                    throw new AssertionError(method.getName());
                });
        var player = state.player();
        var saved = Scenes.Saved.capture(player);
        var destination = new Location(null, 100, 20, 30);
        state.location = destination;
        saved.restore(player, false);
        assertEquals(destination, state.location);
        assertTrue(state.detached);
        assertNull(state.target);
        assertFalse(state.teleported);
    }

    @Test
    public void refusedSceneStillReleasesTransferredResources() {
        var task = (BukkitTask) Proxy.newProxyInstance(BukkitTask.class.getClassLoader(), new Class<?>[]{BukkitTask.class},
                (proxy, method, args) -> null);
        var scheduler = (BukkitScheduler) Proxy.newProxyInstance(BukkitScheduler.class.getClassLoader(), new Class<?>[]{BukkitScheduler.class},
                (proxy, method, args) -> task);
        var manager = (PluginManager) Proxy.newProxyInstance(PluginManager.class.getClassLoader(), new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> null);
        var server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getScheduler" -> scheduler;
                    case "getPluginManager" -> manager;
                    default -> throw new AssertionError(method.getName());
                });
        var plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getServer")) return server;
                    throw new AssertionError(method.getName());
                });
        var resources = new PresentationScope(plugin);
        var released = new AtomicBoolean();
        resources.own(() -> released.set(true));
        var scenes = new Scenes(plugin);
        scenes.close();
        assertFalse(scenes.play(null, null, resources, ignored -> { throw new AssertionError("Scene never started"); }));
        assertTrue(resources.closed());
        assertTrue(released.get());
    }

    private static final class PlayerState {
        Location location = new Location(null, 1, 2, 3, 20, -10);
        GameMode mode = GameMode.CREATIVE;
        boolean allowFlight = true;
        boolean flying = true;
        boolean invulnerable;
        boolean collidable;
        float fallDistance = 2.5f;
        Entity target;
        boolean detached;
        boolean teleported;

        Player player() {
            return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getLocation" -> location.clone();
                        case "getGameMode" -> mode;
                        case "getAllowFlight" -> allowFlight;
                        case "isFlying" -> flying;
                        case "isMarkedInvulnerable" -> invulnerable;
                        case "isCollidable" -> collidable;
                        case "getFallDistance" -> fallDistance;
                        case "getSpectatorTarget" -> target;
                        case "setGameMode" -> { mode = (GameMode) args[0]; yield null; }
                        case "setAllowFlight" -> { allowFlight = (boolean) args[0]; yield null; }
                        case "setFlying" -> { flying = (boolean) args[0]; yield null; }
                        case "setInvulnerable" -> { invulnerable = (boolean) args[0]; yield null; }
                        case "setCollidable" -> { collidable = (boolean) args[0]; yield null; }
                        case "setFallDistance" -> { fallDistance = (float) args[0]; yield null; }
                        case "setSpectatorTarget" -> {
                            target = (Entity) args[0];
                            detached |= target == null;
                            yield null;
                        }
                        case "teleport" -> {
                            location = ((Location) args[0]).clone();
                            teleported = true;
                            yield true;
                        }
                        default -> throw new AssertionError(method.getName());
                    });
        }
    }
}
