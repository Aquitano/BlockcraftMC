package me.blockcraft.api.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.blockcraft.api.BlockcraftApi;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** What a player new to the network sees once: the rules, the way to their settings and what the resource pack does. */
public final class WelcomeDialog {

    private WelcomeDialog() {
    }

    public static void open(Player player, Component rules) {
        var messages = BlockcraftApi.get().messages();
        Consumer<Player> back = previous -> open(previous, rules);
        var dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("welcome.title"))
                        .body(List.of(
                                DialogBody.plainMessage(messages.get("welcome.intro")),
                                DialogBody.plainMessage(rules),
                                DialogBody.plainMessage(messages.get("welcome.pack"))))
                        .build())
                .type(DialogType.multiAction(List.of(
                                Dialogs.button(messages.get("welcome.settings"),
                                        settings -> new SettingsDialog(settings, Optional.of(back)).open(settings)),
                                Dialogs.button(messages.get("welcome.pack-details"), pack -> PackDialog.open(pack, back))))
                        .exitAction(Dialogs.button(messages.get("welcome.start")))
                        .build()));
        Dialogs.show(player, dialog);
    }
}
