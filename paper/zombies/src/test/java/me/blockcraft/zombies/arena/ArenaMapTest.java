package me.blockcraft.zombies.arena;

import org.bukkit.World;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArenaMapTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void everyBreachIsApproachedFromInsideAnEnclosedSpawnRoom() throws Exception {
        var arena = arena();
        for (var window : arena.windows()) {
            var bay = window.bay().orElseThrow();
            assertTrue(window.id(), bay.contains(window.spawn()));
            assertTrue(window.id(), bay.contains(window.approach()));
            assertFalse(window.id(), window.boards().contains(window.approach()));
            assertFalse(window.id(), bay.contains(window.entry()));
        }
    }

    @Test
    public void openingEveryWingKeepsPlayersOutOfSpawnRooms() throws Exception {
        var arena = arena();
        arena.rooms().forEach(room -> arena.unlock(room.id()));
        for (var window : arena.windows()) {
            assertFalse(window.id(), arena.enterable(window.spawn()));
            assertFalse(window.id(), arena.enterable(window.approach()));
            assertTrue(window.id(), arena.enterable(window.entry()));
        }
    }

    private ArenaMap arena() throws Exception {
        var file = temporary.newFile("arena.yml").toPath();
        try (var resource = getClass().getResourceAsStream("/arena.yml")) {
            Files.copy(resource, file, StandardCopyOption.REPLACE_EXISTING);
        }
        var world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, arguments) -> method.getName().equals("equals") && proxy == arguments[0]);
        return new ArenaMap(file, world);
    }
}
