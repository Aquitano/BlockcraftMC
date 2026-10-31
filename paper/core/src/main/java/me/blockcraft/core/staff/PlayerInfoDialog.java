package me.blockcraft.core.staff;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BooleanSupplier;

final class PlayerInfoDialog {

    private final Dialog dialog;

    PlayerInfoDialog(Component title, List<Component> lines) {
        dialog = Dialogs.create(title, lines.stream().map(DialogBody::plainMessage).toList(), List.of(),
                DialogType.notice(Dialogs.button(BlockcraftApi.get().messages().get("menu.close"))));
    }

    void open(Player player, BooleanSupplier eligible) {
        Dialogs.show(player, dialog, eligible);
    }
}
