package me.blockcraft.api.dialog;

import me.blockcraft.api.BlockcraftApi;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.blockcraft.api.menu.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The pieces every server-side dialog is built from: the screen itself, a button whose click calls back with the
 * player (and the inputs the dialog carried), the text a player typed, and a deferred {@link #show} that may be called
 * from inside an inventory click, where Bukkit forbids touching the open inventory.
 */
public final class Dialogs {

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).build();

    private Dialogs() {
    }

    /** The title, then the body and the inputs, then the buttons {@code type} lays out. */
    public static Dialog create(Component title, List<? extends DialogBody> body, List<? extends DialogInput> inputs,
                                DialogType type) {
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title).body(body).inputs(inputs).build())
                .type(type));
    }

    /**
     * {@code buttons} in the vanilla two columns, then {@code exit}, which leads back or only closes the dialog; with no
     * buttons only {@code exit}, since a multi-action dialog must have at least one.
     */
    public static DialogType actions(List<ActionButton> buttons, ActionButton exit) {
        if (buttons.isEmpty()) {
            return DialogType.notice(exit);
        }
        return DialogType.multiAction(buttons).exitAction(exit).build();
    }

    /** What the player typed into {@code key}, trimmed; empty when it is missing, too long or holds control characters. */
    public static Optional<String> text(DialogResponseView view, String key, int maxLength) {
        String typed = view.getText(key);
        if (typed == null || typed.length() > maxLength || typed.chars().anyMatch(Character::isISOControl)) {
            return Optional.empty();
        }
        return Optional.of(typed.strip());
    }

    public static DialogAction action(Consumer<Player> onClick) {
        return action((player, view) -> onClick.accept(player));
    }

    public static DialogAction action(BiConsumer<Player, DialogResponseView> onClick) {
        return DialogAction.customClick((view, audience) -> {
            if (audience instanceof Player player) {
                onClick.accept(player, view);
            }
        }, ONCE);
    }

    public static ActionButton button(Component label, Consumer<Player> onClick) {
        return ActionButton.builder(label).action(action(onClick)).build();
    }

    public static ActionButton button(Component label, BiConsumer<Player, DialogResponseView> onClick) {
        return ActionButton.builder(label).action(action(onClick)).build();
    }

    /** A button that only closes the dialog. */
    public static ActionButton button(Component label) {
        return ActionButton.builder(label).build();
    }

    /** Closes a chest menu the player has open, since the dialog takes its screen without telling the server. */
    public static void show(Player player, Dialog dialog) {
        show(player, dialog, () -> true);
    }

    public static void show(Player player, Dialog dialog, BooleanSupplier eligible) {
        BlockcraftApi.get().playerTasks().navigate(player, eligible, opened -> {
            if (opened.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                opened.closeInventory();
            }
            opened.showDialog(dialog);
        });
    }
}
