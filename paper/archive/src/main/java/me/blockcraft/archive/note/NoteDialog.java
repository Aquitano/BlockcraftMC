package me.blockcraft.archive.note;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BiConsumer;

/** One text field for a note left in an exhibit; an empty note comes back with the form. */
public final class NoteDialog {

    private static final String TEXT = "text";
    private static final int WIDTH = 300;

    private final Dialog dialog;

    public NoteDialog(Messages messages, String exhibitName, int maxLength, String draft, BiConsumer<Player, String> leave) {
        dialog = Dialogs.create(messages.get("note.dialog.title", Placeholder.unparsed("exhibit", exhibitName)),
                List.of(),
                List.of(DialogInput.text(TEXT, messages.get("note.dialog.label"))
                        .maxLength(maxLength)
                        .width(WIDTH)
                        .initial(draft)
                        .build()),
                Dialogs.actions(List.of(Dialogs.button(messages.get("note.dialog.leave"), (viewer, response) -> {
                    var text = Dialogs.text(response, TEXT, maxLength).orElse("");
                    if (text.isEmpty()) {
                        viewer.sendMessage(messages.get("note.empty"));
                        new NoteDialog(messages, exhibitName, maxLength, text, leave).open(viewer);
                        return;
                    }
                    leave.accept(viewer, text);
                })), Dialogs.button(BlockcraftApi.get().messages().get("menu.cancel"))));
    }

    public void open(Player player) {
        Dialogs.show(player, dialog);
    }
}
