package me.blockcraft.zombies.menu;

import io.papermc.paper.registry.data.dialog.body.DialogBody;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.common.message.Messages;
import me.blockcraft.zombies.arena.ArenaMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.List;

public final class NoteDialog {

    private final Messages messages;
    private final ArenaMap.Note note;

    public NoteDialog(Messages messages, ArenaMap.Note note) {
        this.messages = messages;
        this.note = note;
    }

    public void show(Player player) {
        var body = messages.getLines("note." + note.id() + ".body").stream().map(DialogBody::plainMessage).toList();
        var name = Placeholder.component("note", messages.get("note." + note.id() + ".name"));
        Dialogs.show(player, Dialogs.create(messages.get("note.title", name), body, List.of(),
                Dialogs.actions(List.of(), Dialogs.button(BlockcraftApi.get().messages().get("menu.close")))));
    }
}
