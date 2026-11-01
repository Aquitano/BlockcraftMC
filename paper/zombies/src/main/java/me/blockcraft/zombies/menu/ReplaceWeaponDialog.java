package me.blockcraft.zombies.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.zombies.game.Weapon;
import me.blockcraft.zombies.game.ZombiesGame;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A buyer with two guns picks which of them the new one takes the place of; cancelling buys nothing. */
final class ReplaceWeaponDialog {

    private final Dialog dialog;

    ReplaceWeaponDialog(ZombiesGame game, Player player, Weapon bought, Consumer<Weapon> onChoice, Runnable onCancel) {
        var messages = game.messages();
        var name = Placeholder.component("weapon", messages.get("weapon." + bought.key()));
        List<ActionButton> buttons = game.survivor(player).weapons().keySet().stream()
                .map(owned -> Dialogs.button(game.weapons().displayName(player, owned), chooser -> onChoice.accept(owned))).toList();
        dialog = Dialogs.create(messages.get("shop.replace-title"),
                List.of(DialogBody.item(bought.model().item().name(messages.get("weapon.name", name)).build()).build(),
                        DialogBody.plainMessage(messages.get("shop.replace-body", name))),
                List.of(), Dialogs.actions(buttons, Dialogs.button(BlockcraftApi.get().messages().get("menu.cancel"), ignored -> onCancel.run())));
    }

    void open(Player player, BooleanSupplier eligible) {
        Dialogs.show(player, dialog, eligible);
    }
}
