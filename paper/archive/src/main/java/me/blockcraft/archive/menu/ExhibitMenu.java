package me.blockcraft.archive.menu;

import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.archive.exhibit.Checkpoint;
import me.blockcraft.archive.exhibit.Exhibit;
import me.blockcraft.archive.note.Note;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * One exhibit: where a visitor arrives, its checkpoints, the notes it brings along and those staff left in it. A click
 * travels there; staff who may write notes delete one of theirs with a right-click.
 */
final class ExhibitMenu extends ListMenu<ExhibitMenu.Stop> {

    sealed interface Stop {
        record Arrival() implements Stop {
        }

        record Spot(Checkpoint checkpoint) implements Stop {
        }

        record Written(Note note, boolean deletable) implements Stop {
        }
    }

    private final ArchiveMenus menus;
    private final Exhibit exhibit;

    ExhibitMenu(ArchiveMenus menus, Exhibit exhibit, List<Note> notes, boolean mayDelete) {
        super(rowsFor(1 + exhibit.checkpoints().size() + exhibit.notes().size() + notes.size()), menus.messages().get("menu.exhibit.title",
                Placeholder.unparsed("exhibit", exhibit.name()), Placeholder.unparsed("origin", exhibit.origin())));
        this.menus = menus;
        this.exhibit = exhibit;
        backButton(player -> menus.openSection(player, exhibit));
        var stops = new ArrayList<Stop>();
        stops.add(new Stop.Arrival());
        exhibit.checkpoints().forEach(checkpoint -> stops.add(new Stop.Spot(checkpoint)));
        exhibit.notes().forEach(note -> stops.add(new Stop.Written(note, false)));
        notes.forEach(note -> stops.add(new Stop.Written(note, mayDelete)));
        show(stops);
    }

    @Override
    protected ItemStack icon(Stop stop) {
        var messages = menus.messages();
        return switch (stop) {
            case Stop.Arrival() -> {
                var lore = new ArrayList<>(menus.text(exhibit.description()));
                lore.add(messages.get("menu.travel"));
                yield MenuIcon.ARCHIVE_POINT.item().name(messages.get("menu.arrival")).lore(lore).build();
            }
            case Stop.Spot(Checkpoint checkpoint) -> {
                var lore = new ArrayList<>(menus.text(checkpoint.description()));
                lore.add(messages.get("menu.travel"));
                yield MenuIcon.ARCHIVE_POINT.item()
                        .name(messages.get("menu.checkpoint", Placeholder.unparsed("checkpoint", checkpoint.name())))
                        .lore(lore).build();
            }
            case Stop.Written(Note note, boolean deletable) -> noteIcon(note, true, deletable);
        };
    }

    @Override
    protected void onSelect(Player player, Stop stop, InventoryClickEvent event) {
        switch (stop) {
            case Stop.Arrival() -> menus.toArrival(player, exhibit);
            case Stop.Spot(Checkpoint checkpoint) -> menus.toCheckpoint(player, exhibit, checkpoint);
            case Stop.Written(Note note, boolean deletable) -> {
                if (event.isRightClick() && deletable) {
                    menus.confirmDelete(player, exhibit, note, noteIcon(note, false, false));
                } else {
                    menus.toNote(player, exhibit, note);
                }
            }
        }
    }

    private ItemStack noteIcon(Note note, boolean withHints, boolean deletable) {
        var messages = menus.messages();
        var lore = new ArrayList<Component>(menus.text(note.text()));
        lore.add(messages.get("menu.note.written", Placeholder.unparsed("author", note.author().name()),
                Formatter.date("date", note.createdAt().atZone(ZoneId.systemDefault()))));
        if (withHints) {
            lore.add(messages.get("menu.travel"));
        }
        if (withHints && deletable) {
            lore.add(messages.get("menu.note.delete"));
        }
        return MenuIcon.ARCHIVE_NOTE.item().name(messages.get("menu.note.name")).lore(lore).build();
    }
}
