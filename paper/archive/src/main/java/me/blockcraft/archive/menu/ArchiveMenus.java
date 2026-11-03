package me.blockcraft.archive.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.ConfirmDialog;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.archive.ArchiveConfig;
import me.blockcraft.archive.command.NoteCommand;
import me.blockcraft.archive.exhibit.Checkpoint;
import me.blockcraft.archive.exhibit.Section;
import me.blockcraft.archive.exhibit.Exhibit;
import me.blockcraft.archive.exhibit.ExhibitCollection;
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
import java.util.function.Consumer;

public final class ArchiveMenus {

    private static final int LORE_WIDTH = 40;

    private final Messages messages;
    private final ExhibitPool pool;
    private final ExhibitWorlds worlds;
    private final NoteRepository notes;
    private final EntranceHall hall;
    private final List<String> collectionOrder;
    private final List<String> suggestedVisits;

    public ArchiveMenus(Messages messages, ExhibitPool pool, ExhibitWorlds worlds, NoteRepository notes, EntranceHall hall,
                        ArchiveConfig config) {
        this.messages = messages;
        this.pool = pool;
        this.worlds = worlds;
        this.notes = notes;
        this.hall = hall;
        collectionOrder = config.collectionOrder();
        suggestedVisits = config.suggestedVisits();
    }

    Messages messages() {
        return messages;
    }

    public void openSections(Player player) {
        new CollectionMenu(this, ExhibitCollection.of(pool.exhibits(), collectionOrder)).open(player);
    }

    void openCollection(Player player, String origin) {
        ExhibitCollection.of(pool.exhibits(), collectionOrder).stream()
                .filter(collection -> collection.origin().equals(origin)).findFirst().ifPresentOrElse(collection -> {
                    if (collection.hasCategories(messages.raw("menu.section.fallback-category"))) {
                        new SectionMenu(this, collection).open(player);
                    } else {
                        new ExhibitListMenu(this, collectionName(collection.origin()), collection.exhibits(), this::openSections).open(player);
                    }
                }, () -> openSections(player));
    }

    List<Section> sections(ExhibitCollection collection) {
        return Section.of(collection.exhibits(), messages.raw("menu.section.fallback-category"));
    }

    void openSection(Player player, Section section) {
        new ExhibitListMenu(this, sectionName(section), section.exhibits(), back -> openCollection(back, section.origin())).open(player);
    }

    Component collectionName(String origin) {
        return messages.get("menu.collection.title", Placeholder.unparsed("origin", origin));
    }

    Component sectionName(Section section) {
        return messages.get("menu.section.name", Placeholder.unparsed("origin", section.origin()),
                Placeholder.unparsed("category", section.category()));
    }

    List<Exhibit> suggestedExhibits() {
        return suggestedVisits.stream().distinct().flatMap(key -> pool.exhibit(key).stream()).toList();
    }

    boolean suggested(Exhibit exhibit) {
        return suggestedVisits.contains(exhibit.key());
    }

    void openSuggestions(Player player) {
        new ExhibitListMenu(this, messages.get("menu.suggestions.title"), suggestedExhibits(), this::openSections).open(player);
    }

    void openExhibit(Player player, String key, Consumer<Player> back) {
        pool.exhibit(key).ifPresentOrElse(exhibit -> BlockcraftApi.get().playerTasks().latest(player, () -> notes.list(key),
                        list -> new ExhibitMenu(this, exhibit, list, player.hasPermission(NoteCommand.PERMISSION), back).open(player)),
                () -> openSections(player));
    }

    ItemStack entranceButton() {
        return MenuIcon.ARCHIVE_ENTRANCE.item().name(messages.get("menu.entrance"))
                .lore(messages.getLines("menu.entrance-lore")).build();
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

    void confirmDelete(Player player, Note note, ItemStack subject, Consumer<Player> reopen) {
        new ConfirmDialog(subject, text(note.text()),
                messages.get("note.delete.accept"), confirmed -> delete(confirmed, note, reopen), reopen).open(player);
    }

    private void delete(Player player, Note note, Consumer<Player> reopen) {
        BlockcraftApi.get().playerTasks().write(player, () -> notes.delete(note.id()), deleted -> {
            worlds.hide(note);
            player.sendMessage(messages.get(deleted ? "note.deleted" : "note.already-deleted"));
            reopen.accept(player);
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
