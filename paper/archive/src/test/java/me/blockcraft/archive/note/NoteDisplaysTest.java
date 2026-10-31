package me.blockcraft.archive.note;

import me.blockcraft.api.world.Point;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.player.PlayerReference;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoteDisplaysTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final List<Boolean> valid = new ArrayList<>();
    private boolean loaded;
    private World world;
    private Chunk chunk;
    private NoteDisplays displays;

    @Before
    public void setUp() throws Exception {
        world = fake(World.class, (proxy, method, args) -> switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "isChunkLoaded" -> {
                assertEquals(1, args[0]);
                assertEquals(-1, args[1]);
                yield loaded;
            }
            case "spawn" -> {
                int index = valid.size();
                valid.add(true);
                var display = fake(TextDisplay.class, (entity, call, values) -> switch (call.getName()) {
                    case "isValid" -> valid.get(index);
                    case "remove" -> { valid.set(index, false); yield null; }
                    case "text", "setBillboard", "setLineWidth", "setPersistent", "setShadowed" -> null;
                    default -> throw new AssertionError(call.getName());
                });
                @SuppressWarnings("unchecked")
                var initialize = (Consumer<TextDisplay>) args[2];
                initialize.accept(display);
                yield display;
            }
            default -> throw new AssertionError(method.getName());
        });
        chunk = fake(Chunk.class, (proxy, method, args) -> switch (method.getName()) {
            case "getWorld" -> world;
            case "getX" -> 1;
            case "getZ" -> -1;
            default -> throw new AssertionError(method.getName());
        });
        var messages = Messages.load(folder.newFolder().toPath(), () -> new ByteArrayInputStream(
                "note:\n  display: '<text>'\n".getBytes(StandardCharsets.UTF_8)));
        displays = new NoteDisplays(messages);
    }

    @Test
    public void notesOnlySpawnInLoadedChunksAndReturnAfterUnloading() {
        displays.load(world).accept(List.of(note(1)));
        assertTrue(valid.isEmpty());

        loaded = true;
        displays.onLoad(new ChunkLoadEvent(chunk, false));
        displays.onLoad(new ChunkLoadEvent(chunk, false));
        assertEquals(List.of(true), valid);

        displays.onUnload(new ChunkUnloadEvent(chunk));
        loaded = false;
        assertEquals(List.of(false), valid);
        loaded = true;
        displays.onLoad(new ChunkLoadEvent(chunk, false));
        assertEquals(List.of(false, true), valid);
    }

    @Test
    public void pendingSnapshotCannotRestoreDeletedNotesOrReplaceNewOnes() {
        var complete = displays.load(world);
        loaded = true;
        displays.hide(note(1));
        displays.show(world, note(2));
        var stale = new Note(2, "test", new Point(80, 64, -2, 0, 0),
                new PlayerReference("Author", Optional.empty()), "Old text", Instant.EPOCH);
        complete.accept(List.of(note(1), stale));
        assertEquals(List.of(true), valid);

        displays.onUnload(new ChunkUnloadEvent(chunk));
        displays.onLoad(new ChunkLoadEvent(chunk, false));
        assertEquals(List.of(false, true), valid);
    }

    @Test
    public void unloadedWorldRejectsItsPendingSnapshotEvenAfterReload() {
        var stale = displays.load(world);
        loaded = true;
        displays.show(world, note(1));
        displays.onWorldUnload(new WorldUnloadEvent(world));
        assertFalse(valid.getFirst());

        var fresh = displays.load(world);
        stale.accept(List.of(note(1)));
        assertEquals(1, valid.size());
        fresh.accept(List.of(note(2)));
        assertEquals(List.of(false, true), valid);
    }

    private static Note note(long id) {
        return new Note(id, "test", new Point(16, 64, -2, 0, 0),
                new PlayerReference("Author", Optional.empty()), "Note " + id, Instant.EPOCH);
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
