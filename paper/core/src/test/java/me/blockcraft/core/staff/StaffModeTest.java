package me.blockcraft.core.staff;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.api.player.RealNames;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
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
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class StaffModeTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final ArrayDeque<Runnable> main = new ArrayDeque<>();
    private final Map<UUID, Player> online = new HashMap<>();
    private final Map<UUID, World> worlds = new HashMap<>();
    private StaffMode staffMode;
    private FakePlayer observer;
    private Field serverField;
    private Object previousServer;

    @Before
    public void setUp() throws Exception {
        var scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "runTask" -> main.add((Runnable) args[1]);
                case "runTaskAsynchronously" -> { }
                default -> throw new AssertionError(method.getName());
            }
            return null;
        });
        var server = fake(Server.class, (proxy, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "getPlayer" -> online.get(args[0]);
            case "getWorld" -> worlds.get(args[0]);
            default -> throw new AssertionError(method.getName());
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, server);
        var plugin = fake(Plugin.class, (proxy, method, args) -> switch (method.getName()) {
            case "isEnabled" -> true;
            default -> throw new AssertionError(method.getName());
        });
        var messages = Messages.load(folder.newFolder().toPath(), () -> new ByteArrayInputStream(
                "staff:\n  spectating: 'Watching <player>'\n".getBytes(StandardCharsets.UTF_8)));
        var audit = new StaffAudit(plugin, null, new RealNames(), "test", new StaffAudit.Notices(Set.of(), "unused"), messages);
        staffMode = new StaffMode(plugin, messages, null, new FreezeService(plugin, null, messages), null, audit, null, null);
        observer = new FakePlayer(0);
        activate(observer.player);
    }

    @After
    public void restoreServer() throws Exception {
        if (serverField != null) {
            serverField.set(null, previousServer);
        }
    }

    @Test
    public void switchingTargetsPreservesOriginAndIgnoresThePreviousStopEvent() {
        var first = new FakePlayer(10);
        var second = new FakePlayer(20);
        staffMode.spectate(observer.player, first.player);
        staffMode.spectate(observer.player, second.player);
        assertFalse(main.isEmpty());

        tick();

        assertSame(second.player, observer.target);
        assertEquals(GameMode.SPECTATOR, observer.gameMode);
        staffMode.stopSpectating(observer.player);
        tick();
        assertRestored();
        assertEquals(0, observer.location.getX(), 0);
    }

    @Test
    public void onlyTheCurrentTargetsQuitEndsSpectating() {
        var first = new FakePlayer(10);
        var second = new FakePlayer(20);
        staffMode.spectate(observer.player, first.player);
        staffMode.spectate(observer.player, second.player);

        staffMode.onQuit(new PlayerQuitEvent(first.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertSame(second.player, observer.target);
        staffMode.onQuit(new PlayerQuitEvent(second.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        tick();

        assertRestored();
        assertEquals(0, observer.location.getX(), 0);
    }

    @Test
    public void deniedTeleportsDoNotLeaveTheObserverInCameraMode() {
        var target = new FakePlayer(10);
        observer.teleportAllowed = false;
        staffMode.spectate(observer.player, target.player);
        assertRestored();
        assertEquals(0, observer.location.getX(), 0);

        observer.teleportAllowed = true;
        staffMode.spectate(observer.player, target.player);
        observer.teleportAllowed = false;
        staffMode.stopSpectating(observer.player);
        tick();
        assertRestored();
        assertEquals(10, observer.location.getX(), 0);

        observer.teleportAllowed = true;
        staffMode.stopSpectating(observer.player);
        assertEquals(10, observer.location.getX(), 0);
    }

    @Test
    public void unloadedReturnWorldDoesNotPreventCameraCleanup() {
        var target = new FakePlayer(10);
        staffMode.spectate(observer.player, target.player);
        worlds.remove(observer.id);

        staffMode.stopSpectating(observer.player);
        tick();

        assertRestored();
        assertEquals(1, observer.teleports);
        assertEquals(10, observer.location.getX(), 0);
    }

    @Test
    public void inventoryEditorsCanChangeTargetSlotsWithoutMovingStaffTools() {
        var target = new FakePlayer(10);
        var top = fake(PlayerInventory.class, (proxy, method, args) -> switch (method.getName()) {
            case "getHolder" -> target.player;
            default -> throw new AssertionError(method.getName());
        });
        var bottom = fake(PlayerInventory.class, (proxy, method, args) -> switch (method.getName()) {
            case "getHolder" -> observer.player;
            default -> throw new AssertionError(method.getName());
        });
        var view = fake(InventoryView.class, (proxy, method, args) -> switch (method.getName()) {
            case "getPlayer" -> observer.player;
            case "getTopInventory" -> top;
            case "getInventory" -> (int) args[0] < 41 ? top : bottom;
            case "convertSlot" -> args[0];
            default -> throw new AssertionError(method.getName());
        });
        for (var action : new InventoryAction[]{InventoryAction.PICKUP_ALL, InventoryAction.MOVE_TO_OTHER_INVENTORY,
                InventoryAction.HOTBAR_SWAP, InventoryAction.COLLECT_TO_CURSOR}) {
            var event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, action);
            staffMode.onInventoryClick(event);
            new InvseeGuard().onClick(event);
            assertEquals(action != InventoryAction.PICKUP_ALL, event.isCancelled());
        }
        var ownSlot = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 41, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        staffMode.onInventoryClick(ownSlot);
        assertTrue(ownSlot.isCancelled());
    }

    private void assertRestored() {
        assertNull(observer.target);
        assertEquals(GameMode.CREATIVE, observer.gameMode);
        assertTrue(observer.allowFlight);
        assertFalse(observer.flying);
    }

    private void tick() {
        while (!main.isEmpty()) {
            main.remove().run();
        }
    }

    @SuppressWarnings("unchecked")
    private void activate(Player player) throws Exception {
        var sessionType = Class.forName(StaffMode.class.getName() + "$Session");
        var constructor = sessionType.getDeclaredConstructor(ItemStack[].class, boolean.class, boolean.class, boolean.class);
        constructor.setAccessible(true);
        var session = constructor.newInstance(new ItemStack[0], true, true, true);
        var field = StaffMode.class.getDeclaredField("sessions");
        field.setAccessible(true);
        ((Map<UUID, Object>) field.get(staffMode)).put(player.getUniqueId(), session);
    }

    private final class FakePlayer {
        private final UUID id = UUID.randomUUID();
        private final Player player;
        private Location location;
        private GameMode gameMode = GameMode.CREATIVE;
        private boolean allowFlight = true;
        private boolean flying;
        private boolean teleportAllowed = true;
        private int teleports;
        private Entity target;

        private FakePlayer(double x) {
            var world = fake(World.class, (proxy, method, args) -> switch (method.getName()) {
                case "getUID" -> id;
                case "equals" -> proxy == args[0];
                case "hashCode" -> id.hashCode();
                default -> throw new AssertionError(method.getName());
            });
            worlds.put(id, world);
            location = new Location(world, x, 100, 0);
            player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "Player" + id.toString().substring(0, 8);
                case "isOnline", "canSee", "hasPermission" -> true;
                case "isDead" -> false;
                case "getGameMode" -> gameMode;
                case "getLocation" -> location.clone();
                case "getAllowFlight" -> allowFlight;
                case "isFlying" -> flying;
                case "getSpectatorTarget" -> target;
                case "setGameMode" -> { gameMode = (GameMode) args[0]; yield null; }
                case "setAllowFlight" -> { allowFlight = (boolean) args[0]; yield null; }
                case "setFlying" -> { flying = (boolean) args[0]; yield null; }
                case "setSpectatorTarget" -> { camera((Entity) args[0]); yield null; }
                case "teleport" -> {
                    teleports++;
                    if (!teleportAllowed) yield false;
                    camera(null);
                    location = args[0] instanceof Entity entity ? entity.getLocation() : ((Location) args[0]).clone();
                    yield true;
                }
                case "sendMessage" -> null;
                case "equals" -> proxy == args[0];
                case "hashCode" -> id.hashCode();
                default -> throw new AssertionError(method.getName());
            });
            online.put(id, player);
        }

        private void camera(Entity next) {
            if (target != null && target != next) {
                staffMode.onStopSpectating(new PlayerStopSpectatingEntityEvent(player, target));
            }
            target = next;
        }
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
