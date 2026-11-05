package me.blockcraft.sternenfront.secret;

import me.blockcraft.common.message.Messages;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MusicBoxTest {

    private final Messages messages = Messages.load(Path.of("/nonexistent-music-box-test"),
            () -> getClass().getResourceAsStream("/messages.yml"));
    private final Location at = new Location(null, 12, 74, -8);
    private final Block receiver = fake(Block.class, (proxy, method, args) -> {
        if (method.getName().equals("getLocation")) return at;
        throw new AssertionError(method.getName());
    });

    @Test
    public void authoredClicksAreConsumedForUndeployedPlayersAndRidersWithoutDiscoveries() {
        var found = new ArrayList<Secret>();
        var rider = player(true);
        var box = new MusicBox(ignored -> true, messages, (player, secret) -> found.add(secret), at, Secret.RECEIVER);
        assertTrue(box.use(rider, receiver, 0));
        box = new MusicBox(ignored -> false, messages, (player, secret) -> found.add(secret), at, Secret.RECEIVER);
        assertTrue(box.use(player(false), receiver, 0));
        assertTrue(found.isEmpty());
        var elsewhere = fake(Block.class, (proxy, method, args) -> at.clone().add(1, 0, 0));
        assertFalse(box.use(rider, elsewhere, 0));
    }

    @Test
    public void playbackIsSharedAndBusyClicksDoNotRestartItOrDiscoverAgain() {
        var visits = new AtomicInteger();
        var world = fake(World.class, (proxy, method, args) -> {
            if (method.getName().equals("getPlayers")) {
                visits.incrementAndGet();
                return List.of();
            }
            if (method.getName().equals("spawnParticle")) return null;
            if (method.getName().equals("equals")) return proxy == args[0];
            throw new AssertionError(method.getName());
        });
        var speaker = new Location(world, 12, 74, -8);
        var clicked = fake(Block.class, (proxy, method, args) -> speaker);
        var found = new ArrayList<Secret>();
        var box = new MusicBox(ignored -> true, messages, (player, secret) -> found.add(secret), speaker, Secret.RECEIVER);
        assertTrue(box.use(player(false), clicked, 100));
        box.tick(100);
        int firstNotes = visits.get();
        assertTrue(firstNotes > 0);
        assertTrue(box.use(player(false), clicked, 101));
        box.tick(101);
        assertEquals(firstNotes, visits.get());
        assertEquals(List.of(Secret.RECEIVER), found);
        box.stop();
        box.tick(1000);
        assertEquals(firstNotes, visits.get());
        assertNotEquals(Secret.CANTINA.item(), Secret.RECEIVER.item());
    }

    @Test
    public void legacyCantinaUsesTheSameConsumedPlaybackPathAndKeepsItsIdentity() {
        var found = new ArrayList<Secret>();
        var box = new MusicBox(ignored -> true, messages, (player, secret) -> found.add(secret), at, Secret.CANTINA);
        assertTrue(box.use(player(false), receiver, 0));
        assertTrue(box.use(player(false), receiver, 1));
        assertEquals(List.of(Secret.CANTINA), found);
    }

    private static Player player(boolean riding) {
        return fake(Player.class, (proxy, method, args) -> {
            if (method.getName().equals("isInsideVehicle")) return riding;
            if (method.getName().equals("sendActionBar")) return null;
            throw new AssertionError(method.getName());
        });
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
