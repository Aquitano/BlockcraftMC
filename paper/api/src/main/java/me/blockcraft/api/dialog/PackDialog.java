package me.blockcraft.api.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.blockcraft.api.BlockcraftApi;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Whether the viewer's client wears the resource pack, a retry while it does not, and the way back. */
public final class PackDialog {

    private PackDialog() {
    }

    public static void open(Player player, Consumer<Player> back) {
        var api = BlockcraftApi.get();
        var messages = api.messages();
        var status = api.resourcePack().status(player);
        var actions = new ArrayList<ActionButton>();
        switch (status) {
            case FAILED, DECLINED, UNAVAILABLE -> actions.add(Dialogs.button(messages.get("settings.pack-retry"), retry -> {
                api.resourcePack().retry(retry);
                retry.sendMessage(messages.get("settings.pack-requested"));
                open(retry, back);
            }));
            default -> { }
        }
        actions.add(Dialogs.button(messages.get("settings.pack-refresh"), refresh -> open(refresh, back)));
        var dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("settings.pack-title"))
                        .body(List.of(DialogBody.plainMessage(messages.get("settings.pack-status." + status.name().toLowerCase(Locale.ROOT)))))
                        .build())
                .type(DialogType.multiAction(actions)
                        .exitAction(Dialogs.button(messages.get("menu.back"), back))
                        .build()));
        Dialogs.show(player, dialog);
    }
}
