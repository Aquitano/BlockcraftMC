package me.blockcraft.lobby.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.blockcraft.api.dialog.Dialogs;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** One text field for the name of the player to befriend; a name that is no name comes back with the form. */
final class AddFriendDialog {

    private static final String PLAYER = "player";
    private static final int MAX_NAME_LENGTH = 16;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3," + MAX_NAME_LENGTH + "}");

    private final Dialog dialog;

    AddFriendDialog(Menus menus, String draft, BiConsumer<Player, String> send, Consumer<Player> back) {
        var messages = menus.messages();
        dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("friends.note"))
                        .inputs(List.of(DialogInput.text(PLAYER, messages.get("friends.player-name"))
                                .maxLength(MAX_NAME_LENGTH)
                                .initial(draft.length() <= MAX_NAME_LENGTH ? draft : "")
                                .build()))
                        .build())
                .type(DialogType.multiAction(List.of(Dialogs.button(messages.get("friends.send"), (viewer, response) -> {
                            String name = Objects.requireNonNullElse(response.getText(PLAYER), "");
                            if (!NAME.matcher(name).matches()) {
                                viewer.sendMessage(messages.get("friends.invalid-name"));
                                new AddFriendDialog(menus, name, send, back).open(viewer);
                                return;
                            }
                            send.accept(viewer, name);
                        })))
                        .exitAction(Dialogs.button(menus.apiMessages().get("menu.back"), back))
                        .build()));
    }

    void open(Player player) {
        Dialogs.show(player, dialog);
    }
}
