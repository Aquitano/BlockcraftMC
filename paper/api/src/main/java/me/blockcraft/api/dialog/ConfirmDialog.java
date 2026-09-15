package me.blockcraft.api.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.blockcraft.api.BlockcraftApi;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** Yes/no in front of a purchase; the item shown is what the player is about to buy, price and all. */
public final class ConfirmDialog {

    private final Dialog dialog;

    public ConfirmDialog(ItemStack subject, Consumer<Player> onAccept, Consumer<Player> onCancel) {
        var messages = BlockcraftApi.get().messages();
        dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("menu.confirm.title"))
                        .body(List.of(DialogBody.item(subject).build()))
                        .build())
                .type(DialogType.confirmation(
                        Dialogs.button(messages.get("menu.confirm.accept"), onAccept),
                        Dialogs.button(messages.get("menu.confirm.cancel"), onCancel))));
    }

    public void open(Player player) {
        Dialogs.show(player, dialog);
    }
}
