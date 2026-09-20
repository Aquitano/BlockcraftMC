package me.blockcraft.api.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.blockcraft.api.BlockcraftApi;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class ConfirmDialog {

    private final Dialog dialog;

    public ConfirmDialog(ItemStack subject, Consumer<Player> onAccept, Consumer<Player> onCancel) {
        this(subject, List.of(), onAccept, onCancel);
    }

    public ConfirmDialog(ItemStack subject, List<Component> details, Consumer<Player> onAccept, Consumer<Player> onCancel) {
        this(subject, details, Optional.of(onAccept), onCancel);
    }

    public ConfirmDialog(ItemStack subject, List<Component> details, Optional<Consumer<Player>> onAccept, Consumer<Player> onCancel) {
        var messages = BlockcraftApi.get().messages();
        var body = new ArrayList<DialogBody>();
        body.add(DialogBody.item(subject).build());
        details.forEach(line -> body.add(DialogBody.plainMessage(line)));
        dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get(onAccept.isPresent() ? "menu.confirm.title" : "menu.confirm.unavailable"))
                        .body(body)
                        .build())
                .type(onAccept.isPresent() ? DialogType.confirmation(
                        Dialogs.button(messages.get("menu.confirm.accept"), onAccept.get()),
                        Dialogs.button(messages.get("menu.confirm.cancel"), onCancel))
                        : DialogType.notice(Dialogs.button(messages.get("menu.back"), onCancel))));
    }

    public void open(Player player) {
        Dialogs.show(player, dialog);
    }
}
