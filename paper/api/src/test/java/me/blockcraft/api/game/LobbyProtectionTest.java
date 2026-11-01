package me.blockcraft.api.game;

import me.blockcraft.api.world.BuildMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LobbyProtectionTest {

    @Test
    public void sharedWaitingWorldStaysProtectedWithoutRestrictingTheArenaOrBuilders() {
        var lobby = world();
        var arena = world();
        var building = new BuildMode();
        var protection = new LobbyProtection(lobby, building, Optional::empty);
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
    public void originalPhaseConstructorStillSwitchesProtectionWithTheRound() {
        var lobby = new AtomicBoolean(true);
        var world = world();
        var player = player(world);
        var protection = new LobbyProtection(lobby::get, new BuildMode(), Optional::empty);
        var waiting = new BlockBreakEvent(block(world), player);
        protection.onBreak(waiting);
        assertTrue(waiting.isCancelled());
        lobby.set(false);
        var playing = new BlockBreakEvent(block(world), player);
        protection.onBreak(playing);
        assertFalse(playing.isCancelled());
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
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    default -> throw new AssertionError(method);
                });
    }
}
