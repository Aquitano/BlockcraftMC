package me.blockcraft.lobby.menu;

import io.papermc.paper.registry.data.dialog.body.DialogBody;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.List;

/** What changed on the network lately, from {@code changelog.entries}, headed by the version it describes. */
public final class ChangelogDialog {

    private final Messages messages;
    private final String version;

    public ChangelogDialog(Messages messages, String version) {
        this.messages = messages;
        this.version = version;
    }

    public void open(Player player) {
        var body = messages.getLines("changelog.entries").stream().map(DialogBody::plainMessage).toList();
        var dialog = Dialogs.create(messages.get("changelog.title", Placeholder.unparsed("version", version)), body, List.of(),
                Dialogs.actions(List.of(), Dialogs.button(BlockcraftApi.get().messages().get("menu.close"))));
        Dialogs.show(player, dialog);
    }
}
