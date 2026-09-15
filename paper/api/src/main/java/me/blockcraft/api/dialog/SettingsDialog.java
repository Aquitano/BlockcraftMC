package me.blockcraft.api.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** One checkbox per on/off setting; saving applies what changed and goes back to where the dialog was opened from. */
public final class SettingsDialog {

    private static final List<PlayerSetting> TOGGLES = PlayerSetting.toggles();

    private final Dialog dialog;

    /** {@code back} is the menu this was opened from, or empty when the hotbar item or the pause menu opened it. */
    public SettingsDialog(Player viewer, Optional<Consumer<Player>> back) {
        var api = BlockcraftApi.get();
        var messages = api.messages();
        var inputs = TOGGLES.stream()
                .map(setting -> DialogInput.bool(inputKey(setting), messages.get("settings." + setting.key()))
                        .initial(api.settings().enabled(viewer, setting))
                        .build())
                .toList();
        dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("settings.title")).inputs(inputs).build())
                .type(DialogType.multiAction(List.of(Dialogs.button(messages.get("settings.save"), (player, view) -> {
                            save(player, view);
                            back.ifPresent(previous -> previous.accept(player));
                        })))
                        .exitAction(back.map(previous -> Dialogs.button(messages.get("menu.back"), previous))
                                .orElseGet(() -> Dialogs.button(messages.get("menu.close"))))
                        .build()));
    }

    public void open(Player player) {
        Dialogs.show(player, dialog);
    }

    /** Dialog inputs are named like command macro variables, which allow no hyphen. */
    private static String inputKey(PlayerSetting setting) {
        return setting.key().replace('-', '_');
    }

    private static void save(Player player, DialogResponseView view) {
        var api = BlockcraftApi.get();
        for (var setting : TOGGLES) {
            Boolean chosen = view.getBoolean(inputKey(setting));
            if (chosen == null) {
                continue;
            }
            if (chosen != api.settings().enabled(player, setting)) {
                api.settings().set(player, setting, PlayerSetting.value(chosen));
            }
        }
        Sounds.MENU_CLICK.play(player);
        player.sendMessage(api.messages().get("settings.saved"));
    }
}
