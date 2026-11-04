package me.blockcraft.api.world;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Snowball;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WorldProtectionTest {

    @Test
    public void sharedWaitingWorldStaysProtectedWithoutRestrictingTheArenaOrBuilders() {
        var lobby = world();
        var arena = world();
        var building = new BuildMode();
        var protection = WorldProtection.in(lobby, building);
        var visitor = player(lobby);
        var fighter = player(arena);
        var lobbyBreak = new BlockBreakEvent(block(lobby), visitor);
        var arenaBreak = new BlockBreakEvent(block(arena), fighter);
        protection.onBreak(lobbyBreak);
        protection.onBreak(arenaBreak);
        assertTrue(lobbyBreak.isCancelled());
        assertFalse(arenaBreak.isCancelled());

        var lobbyHunger = new FoodLevelChangeEvent(visitor, 10, null);
        var arenaHunger = new FoodLevelChangeEvent(fighter, 10, null);
        protection.onHunger(lobbyHunger);
        protection.onHunger(arenaHunger);
        assertTrue(lobbyHunger.isCancelled());
        assertFalse(arenaHunger.isCancelled());

        building.toggle(visitor);
        var builderBreak = new BlockBreakEvent(block(lobby), visitor);
        protection.onBreak(builderBreak);
        assertFalse(builderBreak.isCancelled());
    }

    @Test
    public void phaseProtectionSwitchesWithTheRound() {
        var lobby = new AtomicBoolean(true);
        var world = world();
        var player = player(world);
        var protection = WorldProtection.during(lobby::get, new BuildMode());
        var waiting = new BlockBreakEvent(block(world), player);
        protection.onBreak(waiting);
        assertTrue(waiting.isCancelled());
        lobby.set(false);
        var playing = new BlockBreakEvent(block(world), player);
        protection.onBreak(playing);
        assertFalse(playing.isCancelled());
    }

    @Test
    public void noBlockAnswersAClickOrAStepUnlessTheWorldLetsIt() {
        var world = world();
        var player = player(world);
        var waiting = WorldProtection.in(world, new BuildMode());
        var exhibit = WorldProtection.during(() -> true, new BuildMode()).usable(block -> block.getType() == Material.OAK_DOOR);

        var waitingLever = click(player, block(world, Material.LEVER));
        var waitingDoor = click(player, block(world, Material.OAK_DOOR));
        waiting.onInteract(waitingLever);
        waiting.onInteract(waitingDoor);
        assertTrue(denied(waitingLever));
        assertTrue(denied(waitingDoor));

        var exhibitLever = click(player, block(world, Material.LEVER));
        var exhibitDoor = click(player, block(world, Material.OAK_DOOR));
        var exhibitPlate = new PlayerInteractEvent(player, Action.PHYSICAL, null, block(world, Material.STONE_PRESSURE_PLATE), BlockFace.UP);
        exhibit.onInteract(exhibitLever);
        exhibit.onInteract(exhibitDoor);
        exhibit.onInteract(exhibitPlate);
        assertTrue(denied(exhibitLever));
        assertFalse(denied(exhibitDoor));
        assertTrue(denied(exhibitPlate));
    }

    @Test
    public void heldItemsWorkOnlyWhereTheWorldLetsThem() {
        var world = world();
        var player = player(world);
        var waiting = WorldProtection.in(world, new BuildMode());
        var lobby = WorldProtection.in(world, new BuildMode()).withHeldItems();

        var waitingThrow = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, null, null, BlockFace.SELF);
        var lobbyThrow = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, null, null, BlockFace.SELF);
        var waitingCast = click(player, block(world, Material.GRASS_BLOCK));
        var lobbyCast = click(player, block(world, Material.GRASS_BLOCK));
        waiting.onInteract(waitingThrow);
        waiting.onInteract(waitingCast);
        lobby.onInteract(lobbyThrow);
        lobby.onInteract(lobbyCast);
        assertEquals(Event.Result.DENY, waitingThrow.useItemInHand());
        assertEquals(Event.Result.DENY, waitingCast.useItemInHand());
        assertNotEquals(Event.Result.DENY, lobbyThrow.useItemInHand());
        assertNotEquals(Event.Result.DENY, lobbyCast.useItemInHand());
        assertTrue(denied(lobbyCast));
    }

    @Test
    public void noProjectileChangesABlock() {
        var world = world();
        var protection = WorldProtection.in(world, new BuildMode());
        var snowball = (Snowball) Proxy.newProxyInstance(Snowball.class.getClassLoader(), new Class<?>[]{Snowball.class},
                (proxy, method, args) -> {
                    throw new AssertionError(method);
                });
        var data = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[]{BlockData.class},
                (proxy, method, args) -> {
                    throw new AssertionError(method);
                });

        var shatteredPot = new EntityChangeBlockEvent(snowball, block(world, Material.DECORATED_POT), data);
        protection.onEntityChangeBlock(shatteredPot);
        assertTrue(shatteredPot.isCancelled());

        var poppedCane = new BlockDestroyEvent(block(world, Material.SUGAR_CANE), data, data, 0, true);
        protection.onLostSupport(poppedCane);
        assertTrue(poppedCane.isCancelled());
    }

    @Test
    public void noEntityAnswersAClickButForBuilders() {
        var world = world();
        var player = player(world);
        var building = new BuildMode();
        var protection = WorldProtection.in(world, building);
        var sheep = (Sheep) Proxy.newProxyInstance(Sheep.class.getClassLoader(), new Class<?>[]{Sheep.class},
                (proxy, method, args) -> {
                    throw new AssertionError(method);
                });

        var shearing = new PlayerInteractEntityEvent(player, sheep);
        protection.onInteractEntity(shearing);
        assertTrue(shearing.isCancelled());

        building.toggle(player);
        var naming = new PlayerInteractEntityEvent(player, sheep);
        protection.onInteractEntity(naming);
        assertFalse(naming.isCancelled());
    }

    @Test
    public void staffMayEditAnInspectedInventoryWhileTheirOwnStaysLocked() {
        var world = world();
        var id = UUID.randomUUID();
        var viewer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getWorld" -> world;
                    case "hasPermission" -> true;
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError(method);
                });
        var target = player(world);
        var protection = WorldProtection.in(world, new BuildMode());
        for (var holder : new Player[]{target, viewer}) {
            var inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                    new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getHolder" -> holder;
                        default -> throw new AssertionError(method);
                    });
            var view = (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(),
                    new Class<?>[]{InventoryView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getPlayer" -> viewer;
                        case "getTopInventory", "getInventory" -> inventory;
                        case "convertSlot" -> args[0];
                        default -> throw new AssertionError(method);
                    });
            var click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            protection.onInventoryClick(click);
            assertEquals(holder == viewer, click.isCancelled());
        }
    }

    private static boolean denied(PlayerInteractEvent click) {
        return click.useInteractedBlock() == Event.Result.DENY;
    }

    private static PlayerInteractEvent click(Player player, Block block) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.NORTH);
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError(method);
                });
    }

    private static Player player(World world) {
        var id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getWorld" -> world;
                    default -> throw new AssertionError(method);
                });
    }

    private static Block block(World world) {
        return block(world, Material.STONE);
    }

    private static Block block(World world, Material type) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getType" -> type;
                    default -> throw new AssertionError(method);
                });
    }
}
