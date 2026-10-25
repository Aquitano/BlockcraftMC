package me.blockcraft.zombies.menu;

import io.papermc.paper.registry.data.dialog.body.DialogBody;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.common.message.Messages;
import org.bukkit.entity.Player;

import java.util.List;

public final class GuideDialog {

    private final Messages messages;

    public GuideDialog(Messages messages) {
        this.messages = messages;
    }

    public void show(Player player) {
        var body = messages.getLines("guide.body").stream().map(DialogBody::plainMessage).toList();
        Dialogs.show(player, Dialogs.create(messages.get("guide.title"), body, List.of(),
                Dialogs.actions(List.of(), Dialogs.button(BlockcraftApi.get().messages().get("menu.close")))));
    }
}
