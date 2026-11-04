package me.blockcraft.archive.command;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BiConsumer;

/** What a visitor reads about a checkpoint, written when staff place it; saving it with nothing typed is fine. */
final class CheckpointDialog {

    private static final String DESCRIPTION = "description";
    private static final int MAX_LENGTH = 200;
    private static final int WIDTH = 300;

    private final Dialog dialog;

    CheckpointDialog(Messages messages, String name, String draft, BiConsumer<Player, String> save) {
        dialog = Dialogs.create(messages.get("setup.checkpoint.title", Placeholder.unparsed("checkpoint", name)),
                List.of(),
                List.of(DialogInput.text(DESCRIPTION, messages.get("setup.checkpoint.label"))
                        .maxLength(MAX_LENGTH)
                        .width(WIDTH)
                        .initial(draft)
                        .build()),
                Dialogs.actions(List.of(Dialogs.button(messages.get("setup.checkpoint.save"), (viewer, response) ->
                        save.accept(viewer, Dialogs.text(response, DESCRIPTION, MAX_LENGTH).orElse("")))),
                        Dialogs.button(BlockcraftApi.get().messages().get("menu.cancel"))));
    }

    void open(Player player) {
        Dialogs.show(player, dialog);
    }
}
