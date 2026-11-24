package me.blockcraft.archive.note;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class NoteDisplays implements Listener {

    private static final int LINE_WIDTH = 180;

    private static final class Session {
        private final Map<Long, Note> notes = new HashMap<>();
        private final Map<Long, TextDisplay> displays = new HashMap<>();
        private final Set<Long> changed = new HashSet<>();
        private boolean loading = true;
    }

    private final Messages messages;
    private final Map<World, Session> sessions = new HashMap<>();

    public NoteDisplays(Messages messages) {
        this.messages = messages;
    }

    public Consumer<List<Note>> load(World world) {
        var session = new Session();
        sessions.put(world, session);
        return notes -> {
            if (sessions.get(world) != session) {
                return;
            }
            for (var note : notes) {
                if (!session.changed.contains(note.id())) {
                    session.notes.put(note.id(), note);
                }
            }
            session.loading = false;
            session.changed.clear();
            session.notes.values().forEach(note -> display(world, session, note));
        };
    }

    public void show(World world, Note note) {
        var session = sessions.computeIfAbsent(world, ignored -> new Session());
        session.notes.put(note.id(), note);
        if (session.loading) {
            session.changed.add(note.id());
        }
        remove(session, note.id());
        display(world, session, note);
    }

    public void hide(Note note) {
        for (var session : sessions.values()) {
            session.notes.remove(note.id());
            if (session.loading) {
                session.changed.add(note.id());
            }
            remove(session, note.id());
        }
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        var session = sessions.get(event.getWorld());
        if (session != null) {
            session.notes.values().stream().filter(note -> inChunk(event.getWorld(), note,
                    event.getChunk().getX(), event.getChunk().getZ()))
                    .forEach(note -> display(event.getWorld(), session, note));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(ChunkUnloadEvent event) {
        var session = sessions.get(event.getWorld());
        if (session != null) {
            session.notes.values().stream().filter(note -> inChunk(event.getWorld(), note,
                    event.getChunk().getX(), event.getChunk().getZ()))
                    .forEach(note -> remove(session, note.id()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        var session = sessions.remove(event.getWorld());
        if (session != null) {
            session.displays.values().forEach(TextDisplay::remove);
        }
    }

    private static boolean inChunk(World world, Note note, int x, int z) {
        var at = note.display(world);
        return at.getBlockX() >> 4 == x && at.getBlockZ() >> 4 == z;
    }

    private static void remove(Session session, long id) {
        var previous = session.displays.remove(id);
        if (previous != null) {
            previous.remove();
        }
    }

    private void display(World world, Session session, Note note) {
        var at = note.display(world);
        if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        var previous = session.displays.get(note.id());
        if (previous != null && previous.isValid()) {
            return;
        }
        var text = messages.get("note.display", Placeholder.unparsed("text", note.text()),
                Placeholder.unparsed("author", note.author().name()),
                Formatter.date("date", note.createdAt().atZone(ZoneId.systemDefault())));
        session.displays.put(note.id(), world.spawn(at, TextDisplay.class, entity -> {
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setLineWidth(LINE_WIDTH);
            entity.setPersistent(false);
            entity.setShadowed(true);
        }));
    }
}
