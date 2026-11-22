package me.blockcraft.zombies.arena;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void everyNoteIsItsOwnBlockInsideItsRoom() throws Exception {
        var arena = arena();
        assertFalse(arena.notes().isEmpty());
        for (var note : arena.notes()) {
            var at = note.location();
            assertTrue(note.id(), arena.rooms().stream().anyMatch(room -> room.id().equals(note.room()) && room.bounds().contains(at)));
            assertTrue(note.id(), arena.stations().stream().noneMatch(station -> station.location().toVector().toBlockVector().equals(at.toVector().toBlockVector())));
            assertTrue(note.id(), arena.doors().stream().noneMatch(door -> door.box().contains(at)));
            assertTrue(note.id(), arena.windows().stream().noneMatch(window -> window.boards().contains(at)
                    || window.bay().map(bay -> bay.contains(at)).orElse(false)));
        }
    }

    @Test
    public void aDoorBetweenTwoRoomsUnlocksBoth() throws Exception {
        var file = temporary.newFile("doors.yml").toPath();
        Files.writeString(file, """
                origin: {x: 0, y: 0, z: 0}
                rooms:
                  yard: {bounds: {x1: 0, y1: 0, z1: 0, x2: 9, y2: 3, z2: 9}}
                  workshop: {bounds: {x1: 10, y1: 0, z1: 0, x2: 19, y2: 3, z2: 9}}
                  tower: {bounds: {x1: 20, y1: 0, z1: 0, x2: 29, y2: 3, z2: 9}}
                doors:
                  workshop-tower: {rooms: [workshop, tower], price: 750, box: {x1: 19, y1: 0, z1: 4, x2: 19, y2: 2, z2: 6}}
                windows: {}
                stations: {}
                """);
        var world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "getBlockAt" -> Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class},
                            (block, call, ignored) -> call.getName().equals("getY") ? arguments[1] : null);
                    default -> null;
                });
        var arena = new ArenaMap(file, world);
        var door = arena.doors().getFirst();
        assertEquals(3, arena.open(door).size());
        assertTrue(arena.opened(door));
        assertTrue(arena.unlocked("workshop"));
        assertTrue(arena.unlocked("tower"));
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
