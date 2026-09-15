package me.blockcraft.api.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import me.blockcraft.api.menu.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The pieces every server-side dialog is built from: a button whose click calls back with the player (and the
 * inputs the dialog carried), and a deferred {@link #show} that may be called from inside an inventory click,
 * where Bukkit forbids touching the open inventory.
 */
public final class Dialogs {

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).build();

    private Dialogs() {
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
        Plugin api = JavaPlugin.getProvidingPlugin(Dialogs.class);
        Bukkit.getScheduler().runTask(api, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                player.closeInventory();
            }
            player.showDialog(dialog);
        });
    }
}
