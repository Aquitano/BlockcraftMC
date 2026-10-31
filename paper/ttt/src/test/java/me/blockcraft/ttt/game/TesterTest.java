package me.blockcraft.ttt.game;

import me.blockcraft.ttt.arena.Box;
import me.blockcraft.ttt.arena.TesterRoom;
import me.blockcraft.ttt.config.TttConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TesterTest {

    @Test
    public void onlyFireDeathsUnderTheActivatedTrapFloorCreditItsOwner() throws Exception {
        var scheduled = new ArrayList<Runnable>();
        var scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> {
            scheduled.add((Runnable) args[1]);
            return null;
        });
        var server = fake(Server.class, (proxy, method, args) -> scheduler);
        var world = fake(World.class, (proxy, method, args) -> method.getName().equals("equals") && proxy == args[0]);
        var data = fake(BlockData.class, (proxy, method, args) -> null);
        var block = fake(Block.class, (proxy, method, args) -> switch (method.getName()) {
            case "getWorld" -> world;
            case "getX", "getZ" -> 0;
            case "getY" -> 74;
            case "getBlockData" -> data;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "setType", "setBlockData" -> null;
            default -> throw new AssertionError(method.getName());
        });
        var room = new TesterRoom(Box.of(0, 75, 0, 0, 77, 0), null, List.of(), List.of(),
                Optional.of(new TesterRoom.Trap(null, List.of(block))));
        var tester = new Tester(null, room, new TttConfig.Tester(3, 4, 20, 3), null, List::of, player -> false);
        var owner = UUID.randomUUID();
        var pit = new Location(world, 0.5, 70, 0.5);
        assertTrue(tester.trapper(pit, "lava").isEmpty());
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        var previous = serverField.get(null);
        try {
            serverField.set(null, server);
            tester.springTrap(owner);
            scheduled.getFirst().run();
            assertEquals(Optional.of(owner), tester.trapper(pit, "lava"));
            assertEquals(Optional.of(owner), tester.trapper(pit, "on_fire"));
            assertTrue(tester.trapper(new Location(world, 2, 70, 0.5), "lava").isEmpty());
            assertTrue(tester.trapper(new Location(world, 0.5, 75, 0.5), "lava").isEmpty());
            assertTrue(tester.trapper(new Location(null, 0.5, 70, 0.5), "lava").isEmpty());
            assertTrue(tester.trapper(pit, "player_attack").isEmpty());
            tester.stop();
            assertTrue(tester.trapper(pit, "lava").isEmpty());
        } finally {
            serverField.set(null, previous);
        }
    }

    private static <T> T fake(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
