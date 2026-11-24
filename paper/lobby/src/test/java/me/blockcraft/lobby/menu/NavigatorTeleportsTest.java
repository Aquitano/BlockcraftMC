package me.blockcraft.lobby.menu;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NavigatorTeleportsTest {

    @Test
    public void onlyOneWarpStartsUntilThePendingTeleportCompletes() {
        var warps = new NavigatorTeleports();
        var started = new ArrayList<CompletableFuture<Boolean>>();
        var player = player(started, new AtomicBoolean());
        var first = warps.start(player, new Location(null, 0, 64, 0)).orElseThrow();

        assertTrue(warps.start(player, new Location(null, 20, 64, 0)).isEmpty());
        assertEquals(1, started.size());
        first.complete(true);

        var next = warps.start(player, new Location(null, 20, 64, 0)).orElseThrow();
        assertEquals(2, started.size());
        assertFalse(next.isDone());
    }

    @Test
    public void failedAndRejectedTeleportsReleaseThePendingWarp() {
        var warps = new NavigatorTeleports();
        var started = new ArrayList<CompletableFuture<Boolean>>();
        var reject = new AtomicBoolean();
        var player = player(started, reject);
        var target = new Location(null, 0, 64, 0);
        warps.start(player, target).orElseThrow().completeExceptionally(new IllegalStateException("chunk unavailable"));
        warps.start(player, target).orElseThrow().complete(false);
        reject.set(true);
        try {
            warps.start(player, target);
            fail("Expected teleport submission to fail");
        } catch (IllegalStateException expected) {
            assertEquals("rejected", expected.getMessage());
        }
        reject.set(false);
        assertTrue(warps.start(player, target).isPresent());
    }

    private static Player player(ArrayList<CompletableFuture<Boolean>> started, AtomicBoolean reject) {
        var id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "teleportAsync" -> {
                        if (reject.get()) {
                            throw new IllegalStateException("rejected");
                        }
                        var future = new CompletableFuture<Boolean>();
                        started.add(future);
                        yield future;
                    }
                    default -> throw new AssertionError(method.getName());
                });
    }
}
