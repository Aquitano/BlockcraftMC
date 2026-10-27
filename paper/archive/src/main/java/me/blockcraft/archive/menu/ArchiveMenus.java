package me.blockcraft.archive.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.ConfirmDialog;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.archive.command.NoteCommand;
import me.blockcraft.archive.exhibit.Checkpoint;
import me.blockcraft.archive.exhibit.Section;
import me.blockcraft.archive.exhibit.Exhibit;
import me.blockcraft.archive.exhibit.ExhibitPool;
import me.blockcraft.archive.exhibit.ExhibitWorlds;
import me.blockcraft.archive.hall.EntranceHall;
import me.blockcraft.archive.note.Note;
import me.blockcraft.archive.note.NoteRepository;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ArchiveMenus {

    private static final int LORE_WIDTH = 40;

    private final Messages messages;
    private final ExhibitPool pool;
    private final ExhibitWorlds worlds;
    private final NoteRepository notes;
    private final EntranceHall hall;

    public ArchiveMenus(Messages messages, ExhibitPool pool, ExhibitWorlds worlds, NoteRepository notes, EntranceHall hall) {
        this.messages = messages;
        this.pool = pool;
        this.worlds = worlds;
        this.notes = notes;
        this.hall = hall;
    }

    Messages messages() {
        return messages;
    }

    public void openSections(Player player) {
        var sections = sections();
        if (sections.size() == 1) {
            new ExhibitListMenu(this, sections.getFirst(), true).open(player);
        } else {
            new SectionMenu(this, sections).open(player);
        }
    }

    private List<Section> sections() {
        return Section.of(pool.exhibits(), messages.raw("menu.section.fallback-category"));
    }

    void openSection(Player player, Exhibit exhibit) {
        var sections = sections();
        sections.stream().filter(section -> section.exhibits().stream().anyMatch(value -> value.key().equals(exhibit.key())))
                .findFirst().ifPresentOrElse(section -> new ExhibitListMenu(this, section, sections.size() == 1).open(player),
                        () -> openSections(player));
    }

    Component sectionName(Section section) {
        return messages.get(section.year().isPresent() ? "menu.section.name-year" : "menu.section.name",
                Placeholder.unparsed("category", section.category()),
                Placeholder.unparsed("year", Integer.toString(section.year().orElse(0))));
    }

    void openExhibit(Player player, String key) {
        pool.exhibit(key).ifPresentOrElse(exhibit -> BlockcraftApi.get().playerTasks().latest(player, () -> notes.list(key),
                        list -> new ExhibitMenu(this, exhibit, list, player.hasPermission(NoteCommand.PERMISSION)).open(player)),
                () -> openSections(player));
    }

    void toEntrance(Player player) {
        Menu.close(player, closed -> {
            closed.teleport(hall.spawn());
            Sounds.TELEPORT.play(closed);
        });
    }

    void toArrival(Player player, Exhibit exhibit) {
        Menu.close(player, closed -> worlds.travel(closed, exhibit, world -> worlds.arrival(exhibit, world),
                messages.get("travel.arrived", Placeholder.unparsed("exhibit", exhibit.name()),
                        Placeholder.unparsed("origin", exhibit.origin()))));
    }

    void toCheckpoint(Player player, Exhibit exhibit, Checkpoint checkpoint) {
        Menu.close(player, closed -> worlds.travel(closed, exhibit, world -> checkpoint.at().in(world),
                messages.get(checkpoint.description().isBlank() ? "travel.checkpoint" : "travel.checkpoint-described",
                        Placeholder.unparsed("checkpoint", checkpoint.name()),
                        Placeholder.unparsed("description", checkpoint.description()))));
    }

    void toNote(Player player, Exhibit exhibit, Note note) {
        Menu.close(player, closed -> worlds.travel(closed, exhibit, world -> note.at().in(world),
                messages.get("travel.note", Placeholder.unparsed("author", note.author().name()))));
    }

    void confirmDelete(Player player, Exhibit exhibit, Note note, ItemStack subject) {
        new ConfirmDialog(subject, text(note.text()),
                messages.get("note.delete.accept"), confirmed -> delete(confirmed, exhibit, note),
                cancelled -> openExhibit(cancelled, exhibit.key())).open(player);
    }

    private void delete(Player player, Exhibit exhibit, Note note) {
        BlockcraftApi.get().playerTasks().write(player, () -> notes.delete(note.id()), deleted -> {
            worlds.hide(note);
            player.sendMessage(messages.get(deleted ? "note.deleted" : "note.already-deleted"));
            openExhibit(player, exhibit.key());
        });
    }

    /** Text from a file or a note as lore, broken at spaces into lines a tooltip can hold. */
    List<Component> text(String text) {
        return lines(text).stream().map(line -> messages.get("menu.text", Placeholder.unparsed("line", line))).toList();
    }

    static List<String> lines(String text) {
        var lines = new ArrayList<String>();
        var line = new StringBuilder();
        for (var word : text.strip().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!line.isEmpty() && line.length() + 1 + word.length() > LORE_WIDTH) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
